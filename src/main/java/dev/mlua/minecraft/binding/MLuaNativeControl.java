package dev.mlua.minecraft.binding;

import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.util.MLuaWeakIdentityMap;
import dev.mlua.modules.coregui.service.MLuaGuiService;
import dev.mlua.modules.coregui.service.MLuaNativeScreenManager;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Live Lua view of a native Minecraft screen widget. */
public final class MLuaNativeControl {
    private static final String BACKING = "__mlua_native_control";
    private static final Map<MLuaRegistration, MLuaWeakIdentityMap<Object, WeakReference<LuaTable>>> INSTANCES = new WeakHashMap<>();
    private static final MLuaWeakIdentityMap<Object, List<TrackedBacking>> BACKINGS_BY_SCREEN =
        new MLuaWeakIdentityMap<>();
    private static final MLuaWeakIdentityMap<Object, Map<String, List<TrackedBacking>>> BACKINGS_BY_OWNER =
        new MLuaWeakIdentityMap<>();

    private MLuaNativeControl() { }

    public static void registerClass(MLuaRegistration registration) {
        registration.registerClass("NativeControl", registration.requireInstanceBaseClassName(),
            "A live handle to a native Minecraft screen widget. Read and modify it only on Minecraft's client thread; properties and methods update the original widget.");
        property(registration, "Position", "UDim2", "Gets or sets native widget position as a UDim2 value.",
            self -> position(backing(self), false), (self, value) -> setDimension(self, value, false));
        property(registration, "Size", "UDim2", "Gets or sets native widget size as a UDim2 value.",
            self -> position(backing(self), true), (self, value) -> setDimension(self, value, true));
        property(registration, "Visible", "boolean", "Gets or sets whether the native widget is visible.",
            self -> {
                Backing backing = backing(self);
                dev.mlua.minecraft.ui.element.GuiBase replacement = backing.replacement.istable()
                    ? dev.mlua.minecraft.ui.element.MLuaTableRegistry.getElement(backing.replacement.checktable()) : null;
                return LuaValue.valueOf(replacement instanceof dev.mlua.minecraft.ui.element.GuiObject object
                    ? object.getVisible() : readBoolean(backing.widget(), "visible", true));
            },
            (self, value) -> {
                if (!value.isboolean()) throw new LuaError("[MLua] NativeControl.Visible expects a boolean");
                MLuaGuiService.setNativeControlProperty(backing(self), "Visible", value);
            });
        property(registration, "Text", "string", "Gets or sets the native widget's displayed text when it has text.",
            self -> readText(backing(self).widget()),
            (self, value) -> {
                if (value.type() != LuaValue.TSTRING) throw new LuaError("[MLua] NativeControl.Text expects a Lua string");
                Backing backing = backing(self);
                if (!hasMethod(backing.widget(), "getMessage") || !hasMethod(backing.widget(), "setMessage")) {
                    throw new LuaError("[MLua] NativeControl.Text is only writable for widgets that expose text");
                }
                MLuaGuiService.setNativeControlProperty(backing, "Text", value);
            });
        property(registration, "Replacement", "GuiObject", "Gets or sets a GuiObject rendered in place of this widget. Button activation is forwarded when the native widget supports it.",
            self -> backing(self).replacement,
            (self, value) -> {
                if (!value.isnil() && !value.istable()) throw new LuaError("[MLua] NativeControl.Replacement expects a live GuiObject instance or nil");
                Backing backing = backing(self);
                MLuaGuiService.setNativeControlProperty(backing, "Replacement", value);
            });
        property(registration, "Before", "GuiObject", "Gets or sets a GuiObject placed immediately to the left of this native widget.",
            self -> backing(self).before,
            (self, value) -> setAdjacent(self, value, "Before"));
        property(registration, "After", "GuiObject", "Gets or sets a GuiObject placed immediately to the right of this native widget.",
            self -> backing(self).after,
            (self, value) -> setAdjacent(self, value, "After"));
        property(registration, "Index", "number", "Returns this control's one-based position among native widgets on its screen.",
            self -> LuaValue.valueOf(backing(self).index), null);
        property(registration, "WidgetClass", "string", "Returns the fully qualified Java class of the native widget.",
            self -> LuaValue.valueOf(backing(self).widget().getClass().getName()), null);
        MLuaNativeControlReflection.register(registration);
    }

    static void property(MLuaRegistration registration, String name, String valueType, String description,
            java.util.function.Function<LuaTable, LuaValue> getter,
            java.util.function.BiConsumer<LuaTable, LuaValue> setter) {
        LuaTable classTable = registration.classTable("NativeControl");
        LuaValue properties = classTable.rawget("__mlua_properties");
        if (properties.istable() && properties.checktable().rawget(name).istable()) return;
        registration.registerClassProperty("NativeControl", name, new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return getter.apply(self.checktable()); }
        }, setter == null ? LuaValue.NIL : new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                setter.accept(self.checktable(), value);
                return LuaValue.NIL;
            }
        }, valueType, "client:Client", description);
    }

    public static LuaTable instance(MLuaRegistration registration, Backing backing) {
        synchronized (INSTANCES) {
            MLuaWeakIdentityMap<Object, WeakReference<LuaTable>> instances = INSTANCES.computeIfAbsent(registration,
                ignored -> new MLuaWeakIdentityMap<>());
            WeakReference<LuaTable> existing = instances.get(backing.widget());
            LuaTable cached = existing == null ? null : existing.get();
            if (cached != null) {
                cached.rawset(BACKING, LuaValue.userdataOf(backing));
                return cached;
            }
            LuaTable created = createInstance(registration, backing);
            instances.put(backing.widget(), new WeakReference<>(created));
            return created;
        }
    }

    private static LuaTable createInstance(MLuaRegistration registration, Backing backing) {
        LuaTable instance = new LuaTable();
        instance.rawset(BACKING, LuaValue.userdataOf(backing));
        LuaTable fallback = new LuaTable();
        fallback.set("__index", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                if (key.type() != LuaValue.TSTRING || key.tojstring().isBlank()) return LuaValue.NIL;
                String property = key.tojstring();
                if (!Character.isLetter(property.charAt(0)) || property.equals("Class")) return LuaValue.NIL;
                String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
                Backing current = backing(args.arg1().checktable());
                Object widget = current.widget();
                Method getter = MLuaNativeControlReflection.findPropertyMethod(widget, "get" + suffix, 0);
                if (getter == null) getter = MLuaNativeControlReflection.findPropertyMethod(widget, "is" + suffix, 0);
                if (getter == null) return LuaValue.NIL;
                try {
                    return MLuaNativeControlReflection.toLuaValue(getter.invoke(widget));
                } catch (ReflectiveOperationException failure) {
                    throw MLuaNativeControlReflection.propertyFailure("read", suffix, failure);
                }
            }
        });
        fallback.set("__newindex", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                if (key.type() == LuaValue.TSTRING && !key.tojstring().isBlank()) {
                    String property = key.tojstring();
                    if (Character.isLetter(property.charAt(0)) && !property.equals("Class")) {
                        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
                        Backing current = backing(args.arg1().checktable());
                        Object widget = current.widget();
                        if (MLuaNativeControlReflection.findPropertyMethod(widget, "set" + suffix, 1) != null) {
                            setWidgetProperty(widget, suffix, args.arg(3));
                            return LuaValue.NONE;
                        }
                    }
                }
                args.arg1().checktable().rawset(key, args.arg(3));
                return LuaValue.NONE;
            }
        });
        instance.setmetatable(fallback);
        return registration.attachClassInstance(instance, "NativeControl", null);
    }

    public static Backing backing(LuaTable instance) {
        LuaValue value = instance.rawget(BACKING);
        Object backing = value.isuserdata() ? value.touserdata() : null;
        if (!(backing instanceof Backing nativeControl)) throw new LuaError("[MLua] NativeControl is no longer attached to a native widget");
        nativeControl.requireCurrent();
        return nativeControl;
    }

    /** Releases native widget and screen references while leaving stale Lua handles detectable. */
    public static void invalidateScreen(Object screen) {
        if (screen == null) return;
        List<TrackedBacking> backings;
        synchronized (BACKINGS_BY_SCREEN) {
            backings = BACKINGS_BY_SCREEN.remove(screen);
        }
        if (backings == null) return;
        for (TrackedBacking tracked : backings) {
            Backing backing = tracked.reference().get();
            if (backing != null) {
                backing.invalidate();
            }
            untrackOwner(tracked);
        }
    }

    /** Releases controls owned by one addon when that addon's Lua runtime is unloaded. */
    public static void invalidateOwner(Object runtimeOwner, String ownerModId) {
        if (runtimeOwner == null || ownerModId == null || ownerModId.isBlank()) return;
        List<TrackedBacking> backings;
        synchronized (BACKINGS_BY_OWNER) {
            Map<String, List<TrackedBacking>> owners = BACKINGS_BY_OWNER.get(runtimeOwner);
            if (owners == null) return;
            backings = owners.remove(ownerModId);
            if (owners.isEmpty()) BACKINGS_BY_OWNER.remove(runtimeOwner);
        }
        if (backings == null) return;
        for (TrackedBacking tracked : backings) {
            Backing backing = tracked.reference().get();
            untrackScreen(tracked);
            if (backing != null) {
                backing.invalidate();
            }
        }
    }

    /** Releases all native widget handles belonging to one client runtime session. */
    public static void invalidateRuntime(Object runtimeOwner) {
        if (runtimeOwner == null) return;
        Map<String, List<TrackedBacking>> owners;
        synchronized (BACKINGS_BY_OWNER) {
            owners = BACKINGS_BY_OWNER.remove(runtimeOwner);
        }
        if (owners == null) return;
        for (List<TrackedBacking> backings : owners.values()) {
            for (TrackedBacking tracked : backings) {
                Backing backing = tracked.reference().get();
                untrackScreen(tracked);
                if (backing != null) backing.invalidate();
            }
        }
    }

    /** Releases every native widget handle when the shared client runtime is torn down. */
    public static void invalidateAll() {
        List<Backing> backings = new ArrayList<>();
        synchronized (BACKINGS_BY_SCREEN) {
            BACKINGS_BY_SCREEN.values().forEach(references -> references.forEach(tracked -> {
                Backing backing = tracked.reference().get();
                if (backing != null) backings.add(backing);
            }));
            BACKINGS_BY_SCREEN.clear();
        }
        synchronized (BACKINGS_BY_OWNER) { BACKINGS_BY_OWNER.clear(); }
        backings.forEach(Backing::invalidate);
    }

    private static void track(Backing backing) {
        TrackedBacking tracked = new TrackedBacking(new WeakReference<>(backing),
            new WeakReference<>(backing.runtimeOwner()), new WeakReference<>(backing.screen()), backing.ownerModId);
        synchronized (BACKINGS_BY_SCREEN) {
            Object screen = backing.screen();
            List<TrackedBacking> references = BACKINGS_BY_SCREEN.get(screen);
            if (references == null) {
                references = new ArrayList<>();
                BACKINGS_BY_SCREEN.put(screen, references);
            }
            references.add(tracked);
        }
        Object runtimeOwner = backing.runtimeOwner();
        if (runtimeOwner != null && !backing.ownerModId.isBlank()) {
            synchronized (BACKINGS_BY_OWNER) {
                Map<String, List<TrackedBacking>> owners = BACKINGS_BY_OWNER.get(runtimeOwner);
                if (owners == null) {
                    owners = new HashMap<>();
                    BACKINGS_BY_OWNER.put(runtimeOwner, owners);
                }
                owners.computeIfAbsent(backing.ownerModId, ignored -> new ArrayList<>()).add(tracked);
            }
        }
    }

    private static void untrackOwner(TrackedBacking tracked) {
        Object runtimeOwner = tracked.runtimeOwner().get();
        if (runtimeOwner == null || tracked.ownerModId().isBlank()) return;
        synchronized (BACKINGS_BY_OWNER) {
            Map<String, List<TrackedBacking>> owners = BACKINGS_BY_OWNER.get(runtimeOwner);
            if (owners == null) return;
            List<TrackedBacking> references = owners.get(tracked.ownerModId());
            if (references == null) return;
            references.removeIf(reference -> reference == tracked || reference.reference().get() == null);
            if (references.isEmpty()) owners.remove(tracked.ownerModId());
            if (owners.isEmpty()) BACKINGS_BY_OWNER.remove(runtimeOwner);
        }
    }

    private static void untrackScreen(TrackedBacking tracked) {
        Object screen = tracked.screen().get();
        if (screen == null) return;
        untrackScreen(screen, null, tracked);
    }

    private static void untrackScreen(Object screen, Backing backing, TrackedBacking tracked) {
        synchronized (BACKINGS_BY_SCREEN) {
            List<TrackedBacking> references = BACKINGS_BY_SCREEN.get(screen);
            if (references == null) return;
            references.removeIf(reference -> reference.reference().get() == null
                || (backing != null && reference.reference().get() == backing)
                || (tracked != null && reference == tracked));
            if (references.isEmpty()) BACKINGS_BY_SCREEN.remove(screen);
        }
    }

    private static void setDimension(LuaTable self, LuaValue value, boolean size) {
        if (!MLuaLuauTypes.isUDim2(value)) throw new LuaError("[MLua] NativeControl Position and Size require UDim2 values created by UDim2.new, UDim2.fromScale, or UDim2.fromOffset");
        Backing backing = backing(self);
        LuaValue x = value.get("X");
        LuaValue y = value.get("Y");
        if (!validDimension(x) || !validDimension(y)) {
            throw new LuaError("[MLua] NativeControl Position and Size require X and Y UDim values with numeric Scale and Offset");
        }
        LuaTable properties = new LuaTable();
        properties.set(size ? "Width" : "X", x);
        properties.set(size ? "Height" : "Y", y);
        MLuaGuiService.setNativeControlProperties(backing, properties);
    }

    private static void setAdjacent(LuaTable self, LuaValue value, String property) {
        if (!value.isnil()) {
            if (!value.istable() || !(dev.mlua.minecraft.ui.element.MLuaTableRegistry.getElement(value.checktable())
                    instanceof dev.mlua.minecraft.ui.element.GuiObject)) {
                throw new LuaError("[MLua] NativeControl." + property + " expects a live GuiObject instance or nil");
            }
        }
        Backing backing = backing(self);
        MLuaGuiService.setNativeControlProperty(backing, property, value);
    }

    private static boolean validDimension(LuaValue value) {
        return MLuaLuauTypes.isUDim(value);
    }

    public static void setWidgetProperty(Object widget, String rawName, LuaValue value) {
        MLuaNativeControlReflection.setWidgetProperty(widget, rawName, value);
    }

    public static Object readWidgetProperty(Object widget, String rawName) {
        return MLuaNativeControlReflection.readWidgetProperty(widget, rawName);
    }

    public static void restoreWidgetProperty(Object widget, String rawName, Object value) {
        MLuaNativeControlReflection.restoreWidgetProperty(widget, rawName, value);
    }

    public static boolean matchesWidgetClass(Class<?> actual, String requested) {
        return MLuaNativeControlReflection.matchesWidgetClass(actual, requested);
    }

    static Object fromLuaValue(LuaValue value, Class<?> type) {
        return MLuaNativeControlReflection.fromLuaValue(value, type);
    }

    static Conversion convertArgument(LuaValue value, Class<?> type, boolean allowNil) {
        MLuaNativeControlReflection.Conversion converted =
            MLuaNativeControlReflection.convertArgument(value, type, allowNil);
        return new Conversion(converted.value(), converted.score());
    }

    static Method mostSpecificMethod(List<Method> candidates) {
        return MLuaNativeControlReflection.mostSpecificMethod(candidates);
    }

    record Conversion(Object value, int score) { }

    private static boolean hasMethod(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (name.equals(method.getName())) return true;
            }
        }
        return false;
    }

    private static LuaValue position(Backing backing, boolean size) {
        dev.mlua.minecraft.ui.element.GuiBase element = backing.replacement.istable()
            ? dev.mlua.minecraft.ui.element.MLuaTableRegistry.getElement(backing.replacement.checktable()) : null;
        if (element instanceof dev.mlua.minecraft.ui.element.GuiObject object) {
            return toLuaUDim2(size ? object.getSize() : object.getPosition());
        }
        Object widget = backing.widget();
        int x = readInt(widget, size ? "getWidth" : "getX", size ? "width" : "x", 0);
        int y = readInt(widget, size ? "getHeight" : "getY", size ? "height" : "y", 0);
        return MLuaLuauTypes.udim2(0.0, x, 0.0, y);
    }

    private static LuaTable toLuaUDim2(dev.mlua.minecraft.ui.UDim2 value) {
        return MLuaLuauTypes.udim2(value.x().scale(), value.x().offset(), value.y().scale(), value.y().offset());
    }

    private static LuaValue readText(Object widget) {
        try {
            Object message = widget.getClass().getMethod("getMessage").invoke(widget);
            return LuaValue.valueOf(message.getClass().getMethod("getString").invoke(message).toString());
        } catch (ReflectiveOperationException ignored) {
            return LuaValue.NIL;
        }
    }

    private static boolean readBoolean(Object widget, String fieldName, boolean fallback) {
        for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getBoolean(widget);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int readInt(Object target, String methodName, String fieldName, int fallback) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            if (value instanceof Number number) return number.intValue();
        } catch (ReflectiveOperationException ignored) { }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(target);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private record TrackedBacking(WeakReference<Backing> reference, WeakReference<Object> runtimeOwner,
                                  WeakReference<Object> screen, String ownerModId) { }

    public static final class Backing {
        private volatile Object widget;
        private volatile Object screen;
        private final WeakReference<Object> runtimeOwner;
        public final String screenName;
        public final int index;
        private final String ownerModId;
        private volatile LuaValue replacement = LuaValue.NIL;
        private volatile LuaValue before = LuaValue.NIL;
        private volatile LuaValue after = LuaValue.NIL;

        public Backing(Object widget, Object screen, String screenName, int index) {
            this(widget, screen, screenName, index, "", null);
        }

        public Backing(Object widget, Object screen, String screenName, int index, String ownerModId) {
            this(widget, screen, screenName, index, ownerModId, null);
        }

        public Backing(Object widget, Object screen, String screenName, int index, String ownerModId,
                       Object runtimeOwner) {
            this.widget = java.util.Objects.requireNonNull(widget, "widget");
            this.screen = java.util.Objects.requireNonNull(screen, "screen");
            this.runtimeOwner = new WeakReference<>(runtimeOwner);
            this.screenName = java.util.Objects.requireNonNull(screenName, "screenName");
            this.index = index;
            this.ownerModId = ownerModId == null || ownerModId.isBlank() ? "runtime" : ownerModId;
            track(this);
        }

        public LuaValue replacement() { return replacement; }
        public void setReplacement(LuaValue replacement) { this.replacement = replacement; }
        public void setBefore(LuaValue before) { this.before = before; }
        public void setAfter(LuaValue after) { this.after = after; }
        Object runtimeOwner() { return this.runtimeOwner.get(); }
        public String ownerModId() { return this.ownerModId; }

        public synchronized Object widget() {
            if (this.widget == null) throw stale();
            return this.widget;
        }

        public synchronized Object screen() {
            if (this.screen == null) throw stale();
            return this.screen;
        }

        private synchronized Object screenIfActive() { return this.screen; }

        private static LuaError stale() {
            return new LuaError("[MLua] NativeControl is stale because its Minecraft screen has been closed or unloaded");
        }

        private synchronized void invalidate() {
            this.replacement = LuaValue.NIL;
            this.before = LuaValue.NIL;
            this.after = LuaValue.NIL;
            this.widget = null;
            this.screen = null;
        }

        public synchronized void requireCurrent() {
            MLuaNativeScreenManager.requireClientThread("NativeControl access");
            if (this.widget == null || this.screen == null) {
                throw stale();
            }
            if (MLuaNativeScreenManager.isDisposed(this.screen)) {
                throw new LuaError("[MLua] NativeControl is stale because its Minecraft screen has been closed or unloaded");
            }
            Object current = MLuaNativeScreenManager.currentScreen();
            if (current != this.screen && !MLuaNativeScreenManager.isClosing(this.screen)) {
                throw new LuaError("[MLua] NativeControl is stale because its Minecraft screen is no longer open");
            }
            try {
                Object children = this.screen.getClass().getMethod("children").invoke(this.screen);
                if (children instanceof Iterable<?> widgets) {
                    for (Object widget : widgets) if (widget == this.widget) return;
                    throw new LuaError("[MLua] NativeControl is stale because its widget is no longer attached to the screen");
                }
            } catch (NoSuchMethodException ignored) {
                // Some third-party screens do not expose their child widgets.
            } catch (ReflectiveOperationException failure) {
                throw new LuaError("[MLua] Could not verify whether NativeControl is still attached to its screen");
            }
        }
    }
}
