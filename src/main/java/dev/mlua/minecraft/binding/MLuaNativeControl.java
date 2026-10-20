package dev.mlua.minecraft.binding;

import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.util.MLuaWeakIdentityMap;
import dev.mlua.modules.coregui.service.MLuaGuiService;
import dev.mlua.modules.coregui.service.MLuaNativeScreenManager;
import org.luaj.vm2.LuaError;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
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
        registration.registerClassFunction("NativeControl", "IsWidgetClass", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue className = args.arg(2);
                if (className.type() != LuaValue.TSTRING || className.tojstring().isBlank()) {
                    throw new LuaError("[MLua] NativeControl:IsWidgetClass expects a Java class or interface name.");
                }
                String requested = className.tojstring();
                return LuaValue.valueOf(matchesWidgetClass(backing(args.arg1().checktable()).widget().getClass(), requested));
            }
        }, "Returns whether this widget's Java class, a superclass, or an implemented interface matches the supplied simple or fully qualified name.");
        property(registration, "Screen", "string", "Returns the fully qualified screen class that owns this control.",
            self -> LuaValue.valueOf(backing(self).screenName), null);
        registration.registerClassFunction("NativeControl", "GetProperty", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Backing backing = backing(args.arg1().checktable());
                String name = propertyName(args.arg(2));
                Object widget = backing.widget();
                Method getter = findPropertyMethod(widget, "get" + name, 0);
                if (getter == null) getter = findPropertyMethod(widget, "is" + name, 0);
                if (getter == null) throw new LuaError("[MLua] Native widget has no readable property '" + name + "'");
                try {
                    return toLuaValue(getter.invoke(widget));
                } catch (ReflectiveOperationException failure) {
                    throw propertyFailure("read", name, failure);
                }
            }
        }, "Reads an additional public bean property from this widget, such as GetProperty(\"Message\"); compatible properties are also available as direct fields. Integer and decimal values that cannot be represented exactly as a Lua number raise an error.");
        registration.registerClassFunction("NativeControl", "SetProperty", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Backing backing = backing(args.arg1().checktable());
                String name = propertyName(args.arg(2));
                MLuaGuiService.setNativeControlProperty(backing, name, args.arg(3));
                return LuaValue.NONE;
            }
        }, "Writes an additional public widget property when its setter accepts a Lua string, finite number, boolean, enum name, or text. Lua strings and numbers are not implicitly converted between types. Integer setters require an in-range whole number; float setters reject overflow. Numeric overloads use the same deterministic preference as Invoke, and equally ranked setters report ambiguity. Compatible properties are also writable as direct fields.");
        registration.registerClassFunction("NativeControl", "GetMethods", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Backing backing = backing(args.arg1().checktable());
                java.util.TreeSet<String> signatures = new java.util.TreeSet<>();
                for (Method method : backing.widget().getClass().getMethods()) {
                    if (isExposedWidgetMethod(method)) signatures.add(method.toGenericString());
                }
                LuaTable result = new LuaTable();
                int index = 1;
                for (String signature : signatures) result.set(index++, LuaValue.valueOf(signature));
                return result;
            }
        }, "Lists public instance methods available on this native widget. Use Invoke for methods whose arguments MLua can convert.");
        registration.registerClassFunction("NativeControl", "GetProperties", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Backing backing = backing(args.arg1().checktable());
                java.util.Map<String, WidgetProperty> properties = new java.util.TreeMap<>();
                Method[] methods = backing.widget().getClass().getMethods();
                java.util.Arrays.sort(methods, java.util.Comparator.comparing(Method::toGenericString));
                for (Method method : methods) {
                    if (!isExposedWidgetMethod(method)) continue;
                    String methodName = method.getName();
                    String propertyName = null;
                    boolean readable = false;
                    boolean writable = false;
                    Class<?> propertyType = null;
                    if (method.getParameterCount() == 0 && method.getReturnType() != Void.TYPE
                        && methodName.startsWith("get") && methodName.length() > 3) {
                        propertyName = methodName.substring(3);
                        readable = supportsWidgetProperty(method.getReturnType());
                        if (!readable) continue;
                        propertyType = method.getReturnType();
                    } else if (method.getParameterCount() == 0 && methodName.startsWith("is")
                        && methodName.length() > 2
                        && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                        propertyName = methodName.substring(2);
                        readable = true;
                        propertyType = method.getReturnType();
                    } else if (method.getParameterCount() == 1 && methodName.startsWith("set")
                        && methodName.length() > 3 && supportsWidgetProperty(method.getParameterTypes()[0])) {
                        propertyName = methodName.substring(3);
                        writable = true;
                        propertyType = method.getParameterTypes()[0];
                    }
                    if (propertyName == null || propertyName.equals("Class") || propertyName.isBlank()) continue;
                    WidgetProperty previous = properties.get(propertyName);
                    if (previous != null) {
                        readable |= previous.readable();
                        writable |= previous.writable();
                        if (previous.type() != null) propertyType = previous.type();
                    }
                    properties.put(propertyName, new WidgetProperty(readable, writable, propertyType));
                }
                LuaTable result = new LuaTable();
                properties.forEach((name, property) -> {
                    LuaTable descriptor = new LuaTable();
                    descriptor.set("Readable", LuaValue.valueOf(property.readable()));
                    descriptor.set("Writable", LuaValue.valueOf(property.writable()));
                    if (property.type() != null) descriptor.set("Type", LuaValue.valueOf(widgetTypeName(property.type())));
                    result.set(name, descriptor);
                });
                return result;
            }
        }, "Lists native bean properties available for direct reads and writes, including each property's supported type. Numeric getter results must be representable as Lua numbers.");
        registration.registerClassFunction("NativeControl", "Invoke", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Backing backing = backing(args.arg1().checktable());
                String name = methodName(args.arg(2));
                LuaValue[] values = new LuaValue[Math.max(0, args.narg() - 2)];
                for (int index = 0; index < values.length; index++) values[index] = args.arg(index + 3);
                Method[] methods = backing.widget().getClass().getMethods();
                java.util.Arrays.sort(methods, java.util.Comparator.comparing(Method::toGenericString));
                Method selected = null;
                Object[] selectedArguments = null;
                int bestScore = Integer.MAX_VALUE;
                java.util.List<Method> tiedMethods = new java.util.ArrayList<>();
                for (Method method : methods) {
                    if (!isExposedWidgetMethod(method) || !method.getName().equals(name)
                        || method.getParameterCount() != values.length) continue;
                    Object[] converted = new Object[values.length];
                    boolean compatible = true;
                    int score = 0;
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    for (int index = 0; index < values.length; index++) {
                        try {
                            Conversion conversion = convertArgument(values[index], parameterTypes[index], true);
                            converted[index] = conversion.value();
                            score += conversion.score();
                        } catch (IllegalArgumentException unsupportedArgument) {
                            compatible = false;
                            break;
                        }
                    }
                    if (!compatible) continue;
                    if (score < bestScore) {
                        selected = method;
                        selectedArguments = converted;
                        bestScore = score;
                        tiedMethods.clear();
                        tiedMethods.add(method);
                    } else if (score == bestScore) {
                        tiedMethods.add(method);
                    }
                }
                if (selected == null) {
                    throw new LuaError("[MLua] Native widget has no public method '" + name
                        + "' with the supplied argument count and supported Lua value types");
                }
                if (tiedMethods.size() > 1) {
                    selected = mostSpecificMethod(tiedMethods);
                    if (selected == null) {
                        java.util.List<String> signatures = tiedMethods.stream().map(Method::toGenericString).sorted().toList();
                        throw new LuaError("[MLua] Native widget method '" + name
                            + "' is ambiguous for these Lua arguments: " + String.join("; ", signatures));
                    }
                    selectedArguments = new Object[values.length];
                    Class<?>[] parameterTypes = selected.getParameterTypes();
                    for (int index = 0; index < values.length; index++) {
                        selectedArguments[index] = convertArgument(values[index], parameterTypes[index], true).value();
                    }
                }
                try {
                    Object result = selected.invoke(backing.widget(), selectedArguments);
                    return selected.getReturnType() == Void.TYPE ? LuaValue.NONE : toLuaValue(result);
                } catch (ReflectiveOperationException failure) {
                    throw propertyFailure("invoke", name, failure);
                }
            }
        }, "Invokes a public instance method when each argument is nil, a string, finite number, boolean, enum name, or text value. Nil maps to Java null for reference parameters and is rejected for primitives. Lua strings and numbers are not implicitly converted between types. Integer arguments must be whole and within the target Java type's range; float arguments reject overflow. Numeric overloads prefer the narrowest valid integer type for whole numbers and double for fractional numbers. Equally ranked overloads raise an ambiguity error. Return values must be a string, number, boolean, enum, or text component; integer and decimal results that cannot be represented exactly as a Lua number raise an error.");
    }

    private static void property(MLuaRegistration registration, String name, String valueType, String description,
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
                Method getter = findPropertyMethod(widget, "get" + suffix, 0);
                if (getter == null) getter = findPropertyMethod(widget, "is" + suffix, 0);
                if (getter == null) return LuaValue.NIL;
                try {
                    return toLuaValue(getter.invoke(widget));
                } catch (ReflectiveOperationException failure) {
                    throw propertyFailure("read", suffix, failure);
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
                        if (findPropertyMethod(widget, "set" + suffix, 1) != null) {
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

    private static String propertyName(LuaValue value) {
        if (value.type() != LuaValue.TSTRING || value.tojstring().isBlank()) {
            throw new LuaError("[MLua] NativeControl property name must be a non-empty string");
        }
        String name = value.tojstring();
        if (!Character.isLetter(name.charAt(0)) || name.equals("Class")) {
            throw new LuaError("[MLua] Invalid native widget property name '" + name + "'");
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String methodName(LuaValue value) {
        if (value.type() != LuaValue.TSTRING || value.tojstring().isBlank()) {
            throw new LuaError("[MLua] Native widget method name must be a non-empty string");
        }
        String name = value.tojstring();
        if (!Character.isLetter(name.charAt(0))) {
            throw new LuaError("[MLua] Invalid native widget method name '" + name + "'");
        }
        return name;
    }

    private static Method findPropertyMethod(Object widget, String methodName, int parameterCount) {
        return java.util.Arrays.stream(widget.getClass().getMethods())
            .filter(method -> method.getName().equals(methodName) && method.getParameterCount() == parameterCount
                && !Modifier.isStatic(method.getModifiers()) && method.getDeclaringClass() != Object.class
                && !method.isBridge() && !method.isSynthetic())
            .min(java.util.Comparator
                .comparingInt((Method method) -> inheritanceDistance(widget.getClass(), method.getDeclaringClass()))
                .thenComparing(Method::toGenericString))
            .orElse(null);
    }

    private static int inheritanceDistance(Class<?> actual, Class<?> declaring) {
        int distance = 0;
        for (Class<?> current = actual; current != null; current = current.getSuperclass()) {
            if (current == declaring) return distance;
            distance++;
        }
        return 64;
    }

    /** Writes a public Java bean property on a native widget using MLua's supported Lua conversions. */
    public static void setWidgetProperty(Object widget, String rawName, LuaValue value) {
        String name = propertyName(LuaValue.valueOf(rawName));
        String setterName = "set" + name;
        Method[] methods = widget.getClass().getMethods();
        java.util.Arrays.sort(methods, java.util.Comparator.comparing(Method::toGenericString));
        Method selected = null;
        Object selectedValue = null;
        int bestScore = Integer.MAX_VALUE;
        java.util.List<Method> tiedMethods = new java.util.ArrayList<>();
        for (Method setter : methods) {
            if (!setter.getName().equals(setterName) || setter.getParameterCount() != 1
                || Modifier.isStatic(setter.getModifiers()) || setter.getDeclaringClass() == Object.class
                || setter.isBridge() || setter.isSynthetic()) continue;
            Conversion conversion;
            try {
                conversion = convertArgument(value, setter.getParameterTypes()[0], false);
            } catch (IllegalArgumentException unsupportedType) {
                continue;
            }
            if (conversion.score() < bestScore) {
                selected = setter;
                selectedValue = conversion.value();
                bestScore = conversion.score();
                tiedMethods.clear();
                tiedMethods.add(setter);
            } else if (conversion.score() == bestScore) {
                tiedMethods.add(setter);
            }
        }
        if (selected != null && tiedMethods.size() > 1) {
            selected = mostSpecificMethod(tiedMethods);
            if (selected == null) {
                java.util.List<String> signatures = tiedMethods.stream().map(Method::toGenericString).sorted().toList();
                throw new LuaError("[MLua] Native widget property '" + name
                    + "' has ambiguous setters for this Lua value: " + String.join("; ", signatures));
            }
            selectedValue = convertArgument(value, selected.getParameterTypes()[0], false).value();
        }
        if (selected != null) {
            try {
                selected.invoke(widget, selectedValue);
                return;
            } catch (ReflectiveOperationException failure) {
                throw propertyFailure("write", name, failure);
            }
        }
        throw new LuaError("[MLua] Native widget has no writable property '" + name
            + "' with a supported Lua value type");
    }

    /** Reads a bean property so a persistent native layout can restore its original value. */
    public static Object readWidgetProperty(Object widget, String rawName) {
        String name = propertyName(LuaValue.valueOf(rawName));
        Method getter = findPropertyMethod(widget, "get" + name, 0);
        if (getter == null) getter = findPropertyMethod(widget, "is" + name, 0);
        if (getter == null) {
            throw new LuaError("[MLua] Native layout property '" + name
                + "' needs a public getter so the original value can be restored");
        }
        try {
            return getter.invoke(widget);
        } catch (ReflectiveOperationException failure) {
            throw propertyFailure("read", name, failure);
        }
    }

    /** Restores a previously captured native bean property without converting its Java value. */
    public static void restoreWidgetProperty(Object widget, String rawName, Object value) {
        String name = propertyName(LuaValue.valueOf(rawName));
        Method[] methods = widget.getClass().getMethods();
        java.util.Arrays.sort(methods, java.util.Comparator.comparing(Method::toGenericString));
        java.util.List<Method> compatible = new java.util.ArrayList<>();
        for (Method setter : methods) {
            if (!setter.getName().equals("set" + name) || setter.getParameterCount() != 1
                || Modifier.isStatic(setter.getModifiers()) || setter.getDeclaringClass() == Object.class
                || setter.isBridge() || setter.isSynthetic()) continue;
            Class<?> parameter = setter.getParameterTypes()[0];
            if (value == null ? parameter.isPrimitive() : !boxed(parameter).isInstance(value)) continue;
            compatible.add(setter);
        }
        Method selected = compatible.size() == 1 ? compatible.get(0) : mostSpecificMethod(compatible);
        if (selected != null) {
            try {
                selected.invoke(widget, value);
                return;
            } catch (ReflectiveOperationException failure) {
                throw propertyFailure("restore", name, failure);
            }
        }
        if (compatible.size() > 1) {
            java.util.List<String> signatures = compatible.stream().map(Method::toGenericString).sorted().toList();
            throw new LuaError("[MLua] Native layout cannot choose a unique setter to restore property '"
                + name + "': " + String.join("; ", signatures));
        }
        throw new LuaError("[MLua] Native layout could not restore property '" + name + "'");
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    private static boolean isExposedWidgetMethod(Method method) {
        int modifiers = method.getModifiers();
        return Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)
            && method.getDeclaringClass() != Object.class && !method.isBridge() && !method.isSynthetic()
            && !method.getName().equals("getClass");
    }

    static Method mostSpecificMethod(java.util.List<Method> candidates) {
        for (Method candidate : candidates) {
            boolean moreSpecificThanAll = true;
            for (Method other : candidates) {
                if (candidate != other && !moreSpecific(candidate, other)) {
                    moreSpecificThanAll = false;
                    break;
                }
            }
            if (moreSpecificThanAll) return candidate;
        }
        return null;
    }

    private static boolean moreSpecific(Method candidate, Method other) {
        Class<?>[] candidateTypes = candidate.getParameterTypes();
        Class<?>[] otherTypes = other.getParameterTypes();
        if (candidateTypes.length != otherTypes.length) return false;
        for (int index = 0; index < candidateTypes.length; index++) {
            Class<?> candidateType = boxed(candidateTypes[index]);
            Class<?> otherType = boxed(otherTypes[index]);
            if (candidateType == otherType) continue;
            if (!otherType.isAssignableFrom(candidateType)) return false;
        }
        return true;
    }

    private static boolean supportsWidgetProperty(Class<?> type) {
        return type == String.class || type == boolean.class || type == Boolean.class
            || type == int.class || type == Integer.class || type == long.class || type == Long.class
            || type == float.class || type == Float.class || type == double.class || type == Double.class
            || type.isEnum() || Component.class.isAssignableFrom(type);
    }

    private static String widgetTypeName(Class<?> type) {
        if (type == String.class || Component.class.isAssignableFrom(type)) return "string";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type.isEnum()) return "enum";
        return "number";
    }

    private record WidgetProperty(boolean readable, boolean writable, Class<?> type) { }

    public static boolean matchesWidgetClass(Class<?> actual, String requested) {
        if (actual == null || requested == null || requested.isBlank()) return false;
        if (matchesTypeName(actual, requested, new java.util.HashSet<>())) return true;
        try {
            Class<?> requestedType = Class.forName(requested, false, actual.getClassLoader());
            return requestedType.isAssignableFrom(actual);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean matchesTypeName(Class<?> actual, String requested, java.util.Set<Class<?>> visited) {
        if (actual == null || !visited.add(actual)) return false;
        if (actual.getName().equals(requested) || actual.getSimpleName().equals(requested)) return true;
        for (Class<?> implemented : actual.getInterfaces()) {
            if (matchesTypeName(implemented, requested, visited)) return true;
        }
        return matchesTypeName(actual.getSuperclass(), requested, visited);
    }

    static Object fromLuaValue(LuaValue value, Class<?> type) {
        if (value.isnil()) {
            if (type.isPrimitive()) throw new IllegalArgumentException();
            return null;
        }
        if (type == String.class) {
            if (value.type() != LuaValue.TSTRING) throw new IllegalArgumentException();
            return value.tojstring();
        }
        if (type == boolean.class || type == Boolean.class) {
            if (!value.isboolean()) throw new IllegalArgumentException();
            return value.toboolean();
        }
        if (type == int.class || type == Integer.class) {
            if (value.type() != LuaValue.TNUMBER) throw new IllegalArgumentException();
            double number = value.todouble();
            if (!Double.isFinite(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE
                || number != Math.rint(number)) throw new IllegalArgumentException();
            return (int) number;
        }
        if (type == long.class || type == Long.class) {
            if (value.type() != LuaValue.TNUMBER) throw new IllegalArgumentException();
            double number = value.todouble();
            // The upper bound is exclusive because Long.MAX_VALUE rounds to 2^63 as a double.
            if (!Double.isFinite(number) || number < -0x1.0p63 || number >= 0x1.0p63
                || number != Math.rint(number)) throw new IllegalArgumentException();
            return (long) number;
        }
        if (type == float.class || type == Float.class) {
            if (value.type() != LuaValue.TNUMBER) throw new IllegalArgumentException();
            double number = value.todouble();
            float converted = (float) number;
            if (!Double.isFinite(number) || !Float.isFinite(converted) || (number != 0.0D && converted == 0.0F)) {
                throw new IllegalArgumentException();
            }
            return converted;
        }
        if (type == double.class || type == Double.class) {
            if (value.type() != LuaValue.TNUMBER) throw new IllegalArgumentException();
            double number = value.todouble();
            if (!Double.isFinite(number)) throw new IllegalArgumentException();
            return number;
        }
        if (Component.class.isAssignableFrom(type)) {
            if (value.type() != LuaValue.TSTRING) throw new IllegalArgumentException();
            Component component = Component.literal(value.tojstring());
            if (!type.isInstance(component)) throw new IllegalArgumentException();
            return component;
        }
        if (type.isEnum()) {
            if (value.type() != LuaValue.TSTRING) throw new IllegalArgumentException();
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(value.tojstring())) return constant;
            }
        }
        throw new IllegalArgumentException();
    }

    static Conversion convertArgument(LuaValue value, Class<?> type, boolean allowNil) {
        if (value.isnil()) {
            if (!allowNil || type.isPrimitive()) throw new IllegalArgumentException();
            return new Conversion(null, 4);
        }
        Object converted = fromLuaValue(value, type);
        if (type == String.class || type == boolean.class) {
            return new Conversion(converted, 0);
        }
        if (type == Boolean.class) return new Conversion(converted, 1);
        if (Component.class.isAssignableFrom(type)) {
            return new Conversion(converted, 1);
        }
        if (type.isEnum()) return new Conversion(converted, 2);
        if (value.type() == LuaValue.TNUMBER) {
            double number = value.todouble();
            boolean integral = number == Math.rint(number);
            int score;
            if (type == int.class) score = 0;
            else if (type == Integer.class) score = 1;
            else if (type == long.class) score = 2;
            else if (type == Long.class) score = 3;
            else if (type == double.class) score = integral ? 4 : 0;
            else if (type == Double.class) score = integral ? 5 : 1;
            else if (type == float.class) score = integral ? 6 : 2;
            else if (type == Float.class) score = integral ? 7 : 3;
            else throw new IllegalArgumentException();
            return new Conversion(converted, score);
        }
        throw new IllegalArgumentException();
    }

    record Conversion(Object value, int score) { }

    private static LuaValue toLuaValue(Object value) {
        if (value == null) return LuaValue.NIL;
        if (value instanceof String string) return LuaValue.valueOf(string);
        if (value instanceof Boolean bool) return LuaValue.valueOf(bool);
        if (value instanceof Number number) return LuaValue.valueOf(exactLuaNumber(number));
        if (value instanceof Enum<?> enumeration) return LuaValue.valueOf(enumeration.name());
        if (value instanceof Component component) return LuaValue.valueOf(component.getString());
        throw new LuaError("[MLua] Native widget value type is not exposed to Lua: " + value.getClass().getSimpleName());
    }

    private static double exactLuaNumber(Number number) {
        double converted = number.doubleValue();
        if (number instanceof Long || number instanceof java.util.concurrent.atomic.AtomicLong) {
            long integer = number.longValue();
            // Double's upper endpoint is exclusive: 2^63 converts back to Long.MAX_VALUE
            // by saturation, so the round-trip check alone cannot detect that overflow.
            if (converted < -0x1.0p63 || converted >= 0x1.0p63
                    || (long) converted != integer) {
                throw new LuaError("[MLua] Native widget integer result cannot be represented exactly as a Lua number: "
                    + integer);
            }
            return converted;
        }
        if (number instanceof Float || number instanceof Double) return converted;
        if (!Double.isFinite(converted)) {
            throw new LuaError("[MLua] Native widget numeric result is outside Lua's number range: " + number);
        }
        try {
            java.math.BigDecimal original = new java.math.BigDecimal(number.toString());
            java.math.BigDecimal roundTrip;
            try {
                // Integer values can be exactly represented above 2^53 when they
                // align with the binary precision (for example, powers of two).
                original.toBigIntegerExact();
                roundTrip = new java.math.BigDecimal(converted);
            } catch (ArithmeticException fractionalValue) {
                roundTrip = java.math.BigDecimal.valueOf(converted);
            }
            if (original.compareTo(roundTrip) != 0) {
                throw new LuaError("[MLua] Native widget numeric result cannot be represented exactly as a Lua number: "
                    + number);
            }
        } catch (NumberFormatException invalidNumber) {
            throw new LuaError("[MLua] Native widget returned an unsupported numeric value: " + number);
        }
        return converted;
    }

    private static LuaError propertyFailure(String action, String name, ReflectiveOperationException failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation && invocation.getCause() != null
            ? invocation.getCause() : failure;
        return new LuaError("[MLua] Could not " + action + " native widget property '" + name + "': " + cause.getMessage());
    }

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
