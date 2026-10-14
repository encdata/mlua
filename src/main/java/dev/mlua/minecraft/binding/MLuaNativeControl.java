package dev.mlua.minecraft.binding;

import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.coregui.Service.MLuaGuiService;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Live Lua view of a native Minecraft screen widget. */
public final class MLuaNativeControl {
    private static final String BACKING = "__mlua_native_control";
    private static final Map<MLuaRegistration, Map<Object, WeakReference<LuaTable>>> INSTANCES = new WeakHashMap<>();

    private MLuaNativeControl() { }

    public static void registerClass(MLuaRegistration registration) {
        registration.registerClass("NativeControl", registration.requireInstanceBaseClassName(),
            "A live handle to a native Minecraft screen widget. Properties update the original widget.");
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
                    ? object.getVisible() : readBoolean(backing.widget, "visible", true));
            },
            (self, value) -> {
                if (!value.isboolean()) throw new LuaError("[MLua] NativeControl.Visible expects a boolean");
                MLuaGuiService.setNativeControlProperty(backing(self), "Visible", value);
            });
        property(registration, "Text", "string", "Gets or sets the native widget's displayed text when it has text.",
            self -> readText(backing(self).widget),
            (self, value) -> {
                if (!value.isstring()) throw new LuaError("[MLua] NativeControl.Text expects a string");
                Backing backing = backing(self);
                if (!hasMethod(backing.widget, "getMessage") || !hasMethod(backing.widget, "setMessage")) {
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
                backing.replacement = value;
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
            self -> LuaValue.valueOf(backing(self).widget.getClass().getName()), null);
        registration.registerClassFunction("NativeControl", "IsWidgetClass", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                String requested = args.arg(2).checkjstring();
                if (requested.isBlank()) throw new LuaError("[MLua] NativeControl:IsWidgetClass expects a Java class or interface name.");
                return LuaValue.valueOf(matchesWidgetClass(backing(args.arg1().checktable()).widget.getClass(), requested));
            }
        }, "Returns whether this widget's Java class, a superclass, or an implemented interface matches the supplied simple or fully qualified name.");
        property(registration, "Screen", "string", "Returns the fully qualified screen class that owns this control.",
            self -> LuaValue.valueOf(backing(self).screenName), null);
        registration.registerClassFunction("NativeControl", "GetProperty", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                Backing backing = backing(args.arg1().checktable());
                String name = propertyName(args.arg(2));
                Method getter = findPropertyMethod(backing.widget, "get" + name, 0);
                if (getter == null) getter = findPropertyMethod(backing.widget, "is" + name, 0);
                if (getter == null) throw new LuaError("[MLua] Native widget has no readable property '" + name + "'");
                try {
                    return toLuaValue(getter.invoke(backing.widget));
                } catch (ReflectiveOperationException failure) {
                    throw propertyFailure("read", name, failure);
                }
            }
        }, "Reads an additional public bean property from this widget, such as GetProperty(\"Message\"); compatible properties are also available as direct fields.");
        registration.registerClassFunction("NativeControl", "SetProperty", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                Backing backing = backing(args.arg1().checktable());
                String name = propertyName(args.arg(2));
                MLuaGuiService.setNativeControlProperty(backing, name, args.arg(3));
                return LuaValue.NONE;
            }
        }, "Writes an additional public widget property when its setter accepts a Lua string, number, boolean, enum name, or text; compatible properties are also writable as direct fields.");
        registration.registerClassFunction("NativeControl", "GetMethods", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                Backing backing = backing(args.arg1().checktable());
                java.util.TreeSet<String> signatures = new java.util.TreeSet<>();
                for (Method method : backing.widget.getClass().getMethods()) {
                    if (isExposedWidgetMethod(method)) signatures.add(method.toGenericString());
                }
                LuaTable result = new LuaTable();
                int index = 1;
                for (String signature : signatures) result.set(index++, LuaValue.valueOf(signature));
                return result;
            }
        }, "Lists public instance methods available on this native widget. Use Invoke for methods whose arguments MLua can convert.");
        registration.registerClassFunction("NativeControl", "GetProperties", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                Backing backing = backing(args.arg1().checktable());
                java.util.Map<String, WidgetProperty> properties = new java.util.TreeMap<>();
                for (Method method : backing.widget.getClass().getMethods()) {
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
        }, "Lists native bean properties available for direct reads and writes, including each property's supported type.");
        registration.registerClassFunction("NativeControl", "Invoke", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                Backing backing = backing(args.arg1().checktable());
                String name = methodName(args.arg(2));
                LuaValue[] values = new LuaValue[Math.max(0, args.narg() - 2)];
                for (int index = 0; index < values.length; index++) values[index] = args.arg(index + 3);
                Method[] methods = backing.widget.getClass().getMethods();
                java.util.Arrays.sort(methods, java.util.Comparator.comparing(Method::toGenericString));
                for (Method method : methods) {
                    if (!isExposedWidgetMethod(method) || !method.getName().equals(name)
                        || method.getParameterCount() != values.length) continue;
                    Object[] converted = new Object[values.length];
                    boolean compatible = true;
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    for (int index = 0; index < values.length; index++) {
                        try {
                            converted[index] = fromLuaValue(values[index], parameterTypes[index]);
                        } catch (IllegalArgumentException unsupportedArgument) {
                            compatible = false;
                            break;
                        }
                    }
                    if (!compatible) continue;
                    try {
                        Object result = method.invoke(backing.widget, converted);
                        return method.getReturnType() == Void.TYPE ? LuaValue.NONE : toLuaValue(result);
                    } catch (ReflectiveOperationException failure) {
                        throw propertyFailure("invoke", name, failure);
                    }
                }
                throw new LuaError("[MLua] Native widget has no public method '" + name
                    + "' with the supplied argument count and supported Lua value types");
            }
        }, "Invokes a public instance method when each argument is a string, number, boolean, enum name, or text value. Return values must be a string, number, boolean, enum, or text component; unsupported Java values raise an error.");
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
            Map<Object, WeakReference<LuaTable>> instances = INSTANCES.computeIfAbsent(registration, ignored -> new WeakHashMap<>());
            WeakReference<LuaTable> existing = instances.get(backing.widget);
            LuaTable cached = existing == null ? null : existing.get();
            if (cached != null) {
                cached.rawset(BACKING, LuaValue.userdataOf(backing));
                return cached;
            }
            LuaTable created = createInstance(registration, backing);
            instances.put(backing.widget, new WeakReference<>(created));
            return created;
        }
    }

    private static LuaTable createInstance(MLuaRegistration registration, Backing backing) {
        LuaTable instance = new LuaTable();
        instance.rawset(BACKING, LuaValue.userdataOf(backing));
        LuaTable fallback = new LuaTable();
        fallback.set("__index", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                LuaValue key = args.arg(2);
                if (!key.isstring() || key.tojstring().isBlank()) return LuaValue.NIL;
                String property = key.tojstring();
                if (!Character.isLetter(property.charAt(0)) || property.equals("Class")) return LuaValue.NIL;
                String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
                Backing current = backing(args.arg1().checktable());
                Method getter = findPropertyMethod(current.widget, "get" + suffix, 0);
                if (getter == null) getter = findPropertyMethod(current.widget, "is" + suffix, 0);
                if (getter == null) return LuaValue.NIL;
                try {
                    return toLuaValue(getter.invoke(current.widget));
                } catch (ReflectiveOperationException failure) {
                    throw propertyFailure("read", suffix, failure);
                }
            }
        });
        fallback.set("__newindex", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                LuaValue key = args.arg(2);
                if (key.isstring() && !key.tojstring().isBlank()) {
                    String property = key.tojstring();
                    if (Character.isLetter(property.charAt(0)) && !property.equals("Class")) {
                        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
                        Backing current = backing(args.arg1().checktable());
                        if (findPropertyMethod(current.widget, "set" + suffix, 1) != null) {
                            setWidgetProperty(current.widget, suffix, args.arg(3));
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
        if ("Before".equals(property)) backing.setBefore(value);
        else backing.setAfter(value);
    }

    private static boolean validDimension(LuaValue value) {
        return value.istable() && "UDim".equals(value.get("__type").optjstring(null)) && value.get("Scale").isnumber() && value.get("Offset").isnumber();
    }

    private static String propertyName(LuaValue value) {
        if (!value.isstring() || value.tojstring().isBlank()) {
            throw new LuaError("[MLua] NativeControl property name must be a non-empty string");
        }
        String name = value.tojstring();
        if (!Character.isLetter(name.charAt(0)) || name.equals("Class")) {
            throw new LuaError("[MLua] Invalid native widget property name '" + name + "'");
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String methodName(LuaValue value) {
        if (!value.isstring() || value.tojstring().isBlank()) {
            throw new LuaError("[MLua] Native widget method name must be a non-empty string");
        }
        String name = value.tojstring();
        if (!Character.isLetter(name.charAt(0))) {
            throw new LuaError("[MLua] Invalid native widget method name '" + name + "'");
        }
        return name;
    }

    private static Method findPropertyMethod(Object widget, String methodName, int parameterCount) {
        for (Method method : widget.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount
                && !Modifier.isStatic(method.getModifiers()) && method.getDeclaringClass() != Object.class
                && !method.isBridge() && !method.isSynthetic()) return method;
        }
        return null;
    }

    /** Writes a public Java bean property on a native widget using MLua's supported Lua conversions. */
    public static void setWidgetProperty(Object widget, String rawName, LuaValue value) {
        String name = propertyName(LuaValue.valueOf(rawName));
        String setterName = "set" + name;
        Method[] methods = widget.getClass().getMethods();
        java.util.Arrays.sort(methods, java.util.Comparator.comparing(method -> method.getParameterCount() == 1
            ? method.getParameterTypes()[0].getName() : "~"));
        for (Method setter : methods) {
            if (!setter.getName().equals(setterName) || setter.getParameterCount() != 1
                || Modifier.isStatic(setter.getModifiers()) || setter.getDeclaringClass() == Object.class
                || setter.isBridge() || setter.isSynthetic()) continue;
            Object converted;
            try {
                converted = fromLuaValue(value, setter.getParameterTypes()[0]);
            } catch (IllegalArgumentException unsupportedType) {
                continue;
            }
            try {
                setter.invoke(widget, converted);
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
        for (Method setter : methods) {
            if (!setter.getName().equals("set" + name) || setter.getParameterCount() != 1
                || Modifier.isStatic(setter.getModifiers()) || setter.getDeclaringClass() == Object.class
                || setter.isBridge() || setter.isSynthetic()) continue;
            Class<?> parameter = setter.getParameterTypes()[0];
            if (value == null ? parameter.isPrimitive() : !boxed(parameter).isInstance(value)) continue;
            try {
                setter.invoke(widget, value);
                return;
            } catch (ReflectiveOperationException failure) {
                throw propertyFailure("restore", name, failure);
            }
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

    private static boolean supportsWidgetProperty(Class<?> type) {
        return type == String.class || type == boolean.class || type == Boolean.class
            || type == int.class || type == Integer.class || type == long.class || type == Long.class
            || type == float.class || type == Float.class || type == double.class || type == Double.class
            || type.isEnum() || net.minecraft.network.chat.Component.class.isAssignableFrom(type);
    }

    private static String widgetTypeName(Class<?> type) {
        if (type == String.class || net.minecraft.network.chat.Component.class.isAssignableFrom(type)) return "string";
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

    private static Object fromLuaValue(LuaValue value, Class<?> type) {
        if (type == String.class) {
            if (!value.isstring()) throw new IllegalArgumentException();
            return value.tojstring();
        }
        if (type == boolean.class || type == Boolean.class) {
            if (!value.isboolean()) throw new IllegalArgumentException();
            return value.toboolean();
        }
        if (type == int.class || type == Integer.class) {
            if (!value.isnumber() || value.todouble() != value.toint()) throw new IllegalArgumentException();
            return value.toint();
        }
        if (type == long.class || type == Long.class) {
            if (!value.isnumber() || value.todouble() != value.tolong()) throw new IllegalArgumentException();
            return value.tolong();
        }
        if (type == float.class || type == Float.class) {
            if (!value.isnumber()) throw new IllegalArgumentException();
            return (float) value.todouble();
        }
        if (type == double.class || type == Double.class) {
            if (!value.isnumber()) throw new IllegalArgumentException();
            return value.todouble();
        }
        if (net.minecraft.network.chat.Component.class.isAssignableFrom(type)) {
            if (!value.isstring()) throw new IllegalArgumentException();
            return net.minecraft.network.chat.Component.literal(value.tojstring());
        }
        if (type.isEnum()) {
            if (!value.isstring()) throw new IllegalArgumentException();
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(value.tojstring())) return constant;
            }
        }
        throw new IllegalArgumentException();
    }

    private static LuaValue toLuaValue(Object value) {
        if (value == null) return LuaValue.NIL;
        if (value instanceof String string) return LuaValue.valueOf(string);
        if (value instanceof Boolean bool) return LuaValue.valueOf(bool);
        if (value instanceof Number number) return LuaValue.valueOf(number.doubleValue());
        if (value instanceof Enum<?> enumeration) return LuaValue.valueOf(enumeration.name());
        if (value instanceof net.minecraft.network.chat.Component component) return LuaValue.valueOf(component.getString());
        throw new LuaError("[MLua] Native widget value type is not exposed to Lua: " + value.getClass().getSimpleName());
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
        Object widget = backing.widget;
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

    public static final class Backing {
        public final Object widget;
        public final Object screen;
        public final String screenName;
        public final int index;
        private LuaValue replacement = LuaValue.NIL;
        private LuaValue before = LuaValue.NIL;
        private LuaValue after = LuaValue.NIL;

        public Backing(Object widget, Object screen, String screenName, int index) {
            this.widget = widget;
            this.screen = screen;
            this.screenName = screenName;
            this.index = index;
        }

        public LuaValue replacement() { return replacement; }
        public void setReplacement(LuaValue replacement) { this.replacement = replacement; }
        public void setBefore(LuaValue before) { this.before = before; }
        public void setAfter(LuaValue after) { this.after = after; }

        public void requireCurrent() {
            Object current = MLuaGuiService.currentScreen();
            if (current != this.screen) throw new LuaError("[MLua] NativeControl is stale because its Minecraft screen is no longer open");
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
