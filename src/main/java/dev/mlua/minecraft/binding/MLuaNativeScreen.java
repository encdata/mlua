package dev.mlua.minecraft.binding;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.modules.coregui.service.MLuaGuiService;
import dev.mlua.modules.coregui.service.MLuaNativeScreenManager;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.util.MLuaWeakIdentityMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import org.luaj.vm2.Varargs;

/** Live Lua view of the currently open native Minecraft screen. */
public final class MLuaNativeScreen {
    private static final String BACKING = "__mlua_native_screen";
    private static final Map<MLuaRegistration, MLuaWeakIdentityMap<Object, WeakReference<LuaTable>>> INSTANCES = new WeakHashMap<>();

    private MLuaNativeScreen() { }

    public static void registerClass(MLuaRegistration registration) {
        registration.registerClass("NativeScreen", registration.requireInstanceBaseClassName(),
            "A live handle to a native Minecraft screen. Read or modify native controls on the client thread; queued screen changes remain safe from Lua workers.");
        registration.registerClassProperty("NativeScreen", "ScreenClass", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return LuaValue.valueOf(backing(self.checktable()).screenClass());
            }
        }, LuaValue.NIL, "string", "client:Client", "Returns the fully qualified Minecraft screen class.");
        registration.registerClassProperty("NativeScreen", "IsOpen", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return LuaValue.valueOf(backing(self.checktable()).isOpen());
            }
        }, LuaValue.NIL, "boolean", "client:Client", "Returns whether this screen is currently open in Minecraft. This live client state may only be read on the client thread.");
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
            "Returns whether this screen exposes Minecraft's standard iterable child-widget API for NativeControl inspection. This reads the live screen and must run on the client thread.");
        registration.registerClassFunction("NativeScreen", "GetControls", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return backing(args.arg1().checktable()).controls();
            }
        }, "Returns live NativeControl instances while this screen is open, and during the synchronous Gui.ScreenClosed callback for final inspection. Returns an empty array after that callback. Must run on the client thread.");
        registration.registerClassFunction("NativeScreen", "GetControl", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                int index = args.arg(2).checkint();
                if (index < 1) throw new LuaError("[MLua] NativeScreen:GetControl index must be one-based.");
                return backing(args.arg1().checktable()).controls().get(index);
            }
        }, "Returns the NativeControl at a one-based widget index, or nil when that index is absent. Must run on the client thread.");
        registration.registerClassFunction("NativeScreen", "FindControls", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String requested = args.arg(2).checkjstring();
                if (requested.isBlank()) throw new LuaError("[MLua] NativeScreen:FindControls expects a widget class name.");
                LuaTable controls = backing(args.arg1().checktable()).controls();
                LuaTable matches = new LuaTable();
                int next = 1;
                for (int index = 1; index <= controls.length(); index++) {
                    LuaValue control = controls.get(index);
                    if (!control.istable()) continue;
                    if (MLuaNativeControl.matchesWidgetClass(
                            MLuaNativeControl.backing(control.checktable()).widget().getClass(), requested)) {
                        matches.set(next++, control);
                    }
                }
                return matches;
            }
        }, "Returns live controls whose native widget class, superclass, or implemented interface matches a simple or fully qualified Java name. Must run on the client thread.");
        registration.registerClassFunction("NativeScreen", "Close", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Backing screen = backing(args.arg1().checktable());
                return LuaValue.valueOf(MLuaNativeScreenManager.close(screen.screen()));
            }
        }, "Closes this screen when it is the currently open Minecraft screen.");
    }

    public static LuaTable instance(MLuaRuntime runtime, Object screen) {
        if (runtime == null || runtime.session() == null) {
            throw new IllegalStateException("Active MLua runtime session is unavailable for NativeScreen");
        }
        if (screen == null) throw new NullPointerException("screen");
        MLuaRegistration registration = runtime;
        synchronized (INSTANCES) {
            MLuaWeakIdentityMap<Object, WeakReference<LuaTable>> instances = INSTANCES.computeIfAbsent(registration,
                ignored -> new MLuaWeakIdentityMap<>());
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

    private static final class Backing {
        private final WeakReference<Object> screen;
        private final WeakReference<MLuaTaskSchedulerView> scheduler;
        private final String screenClass;

        private Backing(Object screen, MLuaTaskSchedulerView scheduler) {
            this.screen = new WeakReference<>(screen);
            this.scheduler = new WeakReference<>(scheduler);
            this.screenClass = screen.getClass().getName();
        }

        Object screen() { return this.screen.get(); }
        String screenClass() { return this.screenClass; }
        boolean isOpen() {
            MLuaNativeScreenManager.requireClientThread("NativeScreen.IsOpen");
            Object active = this.screen();
            return active != null && !MLuaNativeScreenManager.isDisposed(active)
                && MLuaNativeScreenManager.currentScreen() == active;
        }
        boolean isPending() {
            Object active = this.screen();
            return active != null && MLuaNativeScreenManager.isPending(active);
        }
        boolean canInspectControls() {
            MLuaNativeScreenManager.requireClientThread("NativeScreen.CanInspectControls");
            Object active = this.screen();
            if (active == null) return false;
            try {
                return active.getClass().getMethod("children").invoke(active) instanceof Iterable<?>;
            } catch (ReflectiveOperationException failure) {
                return false;
            }
        }
        LuaTable controls() {
            MLuaNativeScreenManager.requireClientThread("NativeScreen:GetControls");
            Object active = this.screen();
            MLuaTaskSchedulerView activeScheduler = this.scheduler.get();
            if (active == null || activeScheduler == null
                || (!isOpen() && !MLuaNativeScreenManager.isClosing(active))) return new LuaTable();
            return MLuaGuiService.getNativeControls(activeScheduler, active);
        }
    }
}
