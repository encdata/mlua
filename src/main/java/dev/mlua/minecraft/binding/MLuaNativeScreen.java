package dev.mlua.minecraft.binding;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.modules.coregui.Service.MLuaGuiService;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Live Lua view of the currently open native Minecraft screen. */
public final class MLuaNativeScreen {
    private static final String BACKING = "__mlua_native_screen";
    private static final Map<MLuaRegistration, Map<Object, WeakReference<LuaTable>>> INSTANCES = new WeakHashMap<>();

    private MLuaNativeScreen() { }

    public static void registerClass(MLuaRegistration registration) {
        registration.registerClass("NativeScreen", registration.requireInstanceBaseClassName(),
            "A live handle to a native Minecraft screen and its native widget controls.");
        registration.registerClassProperty("NativeScreen", "ScreenClass", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return LuaValue.valueOf(backing(self.checktable()).screenClass());
            }
        }, LuaValue.NIL, "string", "client:Client", "Returns the fully qualified Minecraft screen class.");
        registration.registerClassProperty("NativeScreen", "IsOpen", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return LuaValue.valueOf(backing(self.checktable()).isOpen());
            }
        }, LuaValue.NIL, "boolean", "client:Client", "Returns whether this screen is currently open in Minecraft.");
        registration.registerClassProperty("NativeScreen", "IsPending", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return LuaValue.valueOf(backing(self.checktable()).isPending());
            }
        }, LuaValue.NIL, "boolean", "client:Client", "Returns whether Minecraft has a queued request to open this screen on the client thread.");
        registration.registerClassProperty("NativeScreen", "CanInspectControls", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return LuaValue.valueOf(backing(self.checktable()).canInspectControls());
            }
        }, LuaValue.NIL, "boolean", "client:Client",
            "Returns whether this screen exposes Minecraft's standard iterable child-widget API for NativeControl inspection.");
        registration.registerClassFunction("NativeScreen", "GetControls", new VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                return backing(args.arg1().checktable()).controls();
            }
        }, "Returns the current live NativeControl instances for this screen's widgets, or an empty array after the screen closes.");
        registration.registerClassFunction("NativeScreen", "GetControl", new VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                int index = args.arg(2).checkint();
                if (index < 1) throw new LuaError("[MLua] NativeScreen:GetControl index must be one-based.");
                return backing(args.arg1().checktable()).controls().get(index);
            }
        }, "Returns the NativeControl at a one-based widget index, or nil when that index is absent.");
        registration.registerClassFunction("NativeScreen", "FindControls", new VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                String requested = args.arg(2).checkjstring();
                if (requested.isBlank()) throw new LuaError("[MLua] NativeScreen:FindControls expects a widget class name.");
                LuaTable controls = backing(args.arg1().checktable()).controls();
                LuaTable matches = new LuaTable();
                int next = 1;
                for (int index = 1; index <= controls.length(); index++) {
                    LuaValue control = controls.get(index);
                    if (!control.istable()) continue;
                    if (MLuaNativeControl.matchesWidgetClass(
                            MLuaNativeControl.backing(control.checktable()).widget.getClass(), requested)) {
                        matches.set(next++, control);
                    }
                }
                return matches;
            }
        }, "Returns live controls whose native widget class, superclass, or implemented interface matches a simple or fully qualified Java name.");
        registration.registerClassFunction("NativeScreen", "Close", new VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                Backing screen = backing(args.arg1().checktable());
                return LuaValue.valueOf(MLuaGuiService.closeNativeScreen(screen.screen));
            }
        }, "Closes this screen when it is the currently open Minecraft screen.");
    }

    public static LuaTable instance(MLuaRuntime runtime, Object screen) {
        if (runtime == null) throw new IllegalStateException("MLua runtime is unavailable for NativeScreen");
        if (screen == null) throw new NullPointerException("screen");
        MLuaRegistration registration = runtime;
        synchronized (INSTANCES) {
            Map<Object, WeakReference<LuaTable>> instances = INSTANCES.computeIfAbsent(registration,
                ignored -> new WeakHashMap<>());
            WeakReference<LuaTable> reference = instances.get(screen);
            LuaTable cached = reference == null ? null : reference.get();
            if (cached != null) return cached;
            LuaTable created = registration.createClassInstance("NativeScreen");
            created.rawset(BACKING, LuaValue.userdataOf(new Backing(screen, runtime.session().clientScheduler())));
            instances.put(screen, new WeakReference<>(created));
            return created;
        }
    }

    private static Backing backing(LuaTable instance) {
        LuaValue value = instance.rawget(BACKING);
        Object backing = value.isuserdata() ? value.touserdata() : null;
        if (!(backing instanceof Backing nativeScreen)) {
            throw new LuaError("[MLua] NativeScreen is no longer attached to a Minecraft screen.");
        }
        return nativeScreen;
    }

    private record Backing(Object screen, MLuaTaskSchedulerView scheduler) {
        String screenClass() { return this.screen.getClass().getName(); }
        boolean isOpen() { return MLuaGuiService.currentScreen() == this.screen; }
        boolean isPending() { return MLuaGuiService.isNativeScreenPending(this.screen); }
        boolean canInspectControls() {
            try {
                return this.screen.getClass().getMethod("children").invoke(this.screen) instanceof Iterable<?>;
            } catch (ReflectiveOperationException failure) {
                return false;
            }
        }
        LuaTable controls() {
            if (!isOpen()) return new LuaTable();
            return MLuaGuiService.getNativeControls(this.scheduler, this.screen);
        }
    }
}
