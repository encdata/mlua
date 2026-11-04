package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.coregui.service.MLuaGuiService;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/** Reflection-backed access to public native widget methods and bean properties. */
final class MLuaNativeControlReflection {
    private MLuaNativeControlReflection() { }

    static void register(MLuaRegistration registration) {
        registration.registerClassFunction("NativeControl", "IsWidgetClass", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue className = args.arg(2);
                if (className.type() != LuaValue.TSTRING || className.tojstring().isBlank()) {
                    throw new LuaError("[MLua] NativeControl:IsWidgetClass expects a Java class or interface name.");
                }
                String requested = className.tojstring();
                return LuaValue.valueOf(matchesWidgetClass(
                    MLuaNativeControl.backing(args.arg1().checktable()).widget().getClass(), requested));
            }
        }, "Returns whether this widget's Java class, a superclass, or an implemented interface matches the supplied simple or fully qualified name.");
        MLuaNativeControl.property(registration, "Screen", "string", "Returns the fully qualified screen class that owns this control.",
            self -> LuaValue.valueOf(MLuaNativeControl.backing(self).screenName), null);
        registration.registerClassFunction("NativeControl", "GetProperty", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                MLuaNativeControl.Backing backing = MLuaNativeControl.backing(args.arg1().checktable());
                String name = propertyName(args.arg(2));
                Object widget = backing.widget();
                Method getter = findPropertyMethod(widget, "get" + name, 0);
                if (getter == null) getter = findPropertyMethod(widget, "is" + name, 0);
                if (getter == null) throw new LuaError("[MLua] Native widget has no readable property '" + name + "'");
                try {
                    return MLuaNativeControlValueAdapter.toLuaValue(getter.invoke(widget));
                } catch (ReflectiveOperationException failure) {
                    throw propertyFailure("read", name, failure);
                }
            }
        }, "Reads an additional public bean property from this widget, such as GetProperty(\"Message\"); compatible properties are also available as direct fields. Integer and decimal values that cannot be represented exactly as a Lua number raise an error.");
        registration.registerClassFunction("NativeControl", "SetProperty", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                MLuaNativeControl.Backing backing = MLuaNativeControl.backing(args.arg1().checktable());
                String name = propertyName(args.arg(2));
                MLuaGuiService.setNativeControlProperty(backing, name, args.arg(3));
                return LuaValue.NONE;
            }
        }, "Writes an additional public widget property when its setter accepts a Lua string, finite number, boolean, enum name, or text. Lua strings and numbers are not implicitly converted between types. Integer setters require an in-range whole number; float setters reject overflow. Numeric overloads use the same deterministic preference as Invoke, and equally ranked setters report ambiguity. Compatible properties are also writable as direct fields.");
        registration.registerClassFunction("NativeControl", "GetMethods", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                MLuaNativeControl.Backing backing = MLuaNativeControl.backing(args.arg1().checktable());
                java.util.TreeSet<String> signatures = new java.util.TreeSet<>();
            for (Method method : widgetMethods(backing.widget())) {
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
                MLuaNativeControl.Backing backing = MLuaNativeControl.backing(args.arg1().checktable());
                java.util.Map<String, WidgetProperty> properties = new java.util.TreeMap<>();
            List<Method> methods = widgetMethods(backing.widget());
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
                MLuaNativeControl.Backing backing = MLuaNativeControl.backing(args.arg1().checktable());
                String name = methodName(args.arg(2));
                LuaValue[] values = new LuaValue[Math.max(0, args.narg() - 2)];
                for (int index = 0; index < values.length; index++) values[index] = args.arg(index + 3);
                MLuaNativeControlOverloadResolver.Resolution resolution =
                    MLuaNativeControlOverloadResolver.resolve(methodsNamed(backing.widget(), name), values, true,
                        MLuaNativeControlReflection::isExposedWidgetMethod);
                if (!resolution.found() && !resolution.ambiguous()) {
                    throw new LuaError("[MLua] Native widget has no public method '" + name
                        + "' with the supplied argument count and supported Lua value types");
                }
                if (resolution.ambiguous()) {
                    throw new LuaError("[MLua] Native widget method '" + name
                        + "' is ambiguous for these Lua arguments: "
                        + String.join("; ", resolution.ambiguousSignatures()));
                }
                try {
                    Object result = resolution.method().invoke(backing.widget(), resolution.arguments());
                    return resolution.method().getReturnType() == Void.TYPE ? LuaValue.NONE
                        : MLuaNativeControlValueAdapter.toLuaValue(result);
                } catch (ReflectiveOperationException failure) {
                    throw propertyFailure("invoke", name, failure);
                }
            }
        }, "Invokes a public instance method when each argument is nil, a string, finite number, boolean, enum name, or text value. Nil maps to Java null for reference parameters and is rejected for primitives. Lua strings and numbers are not implicitly converted between types. Integer arguments must be whole and within the target Java type's range; float arguments reject overflow. Numeric overloads prefer the narrowest valid integer type for whole numbers and double for fractional numbers. Equally ranked overloads raise an ambiguity error. Return values must be a string, number, boolean, enum, or text component; integer and decimal results that cannot be represented exactly as a Lua number raise an error.");
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

    static Method findPropertyMethod(Object widget, String methodName, int parameterCount) {
        return MLuaNativeControlMethodIndex.propertyMethod(widget, methodName, parameterCount);
    }

    private static List<Method> widgetMethods(Object widget) {
        return MLuaNativeControlMethodIndex.methods(widget);
    }

    private static List<Method> methodsNamed(Object widget, String name) {
        return MLuaNativeControlMethodIndex.methodsNamed(widget, name);
    }

    /** Writes a public Java bean property on a native widget using MLua's supported Lua conversions. */
    static void setWidgetProperty(Object widget, String rawName, LuaValue value) {
        String name = propertyName(LuaValue.valueOf(rawName));
        String setterName = "set" + name;
        MLuaNativeControlOverloadResolver.Resolution resolution =
            MLuaNativeControlOverloadResolver.resolve(methodsNamed(widget, setterName), new LuaValue[] {value}, false,
                setter -> !Modifier.isStatic(setter.getModifiers())
                    && setter.getDeclaringClass() != Object.class && !setter.isBridge() && !setter.isSynthetic());
        if (resolution.ambiguous()) {
            throw new LuaError("[MLua] Native widget property '" + name
                + "' has ambiguous setters for this Lua value: "
                + String.join("; ", resolution.ambiguousSignatures()));
        }
        if (resolution.found()) {
            try {
                resolution.method().invoke(widget, resolution.arguments());
                return;
            } catch (ReflectiveOperationException failure) {
                throw propertyFailure("write", name, failure);
            }
        }
        throw new LuaError("[MLua] Native widget has no writable property '" + name
            + "' with a supported Lua value type");
    }

    /** Reads a bean property so a persistent native layout can restore its original value. */
    static Object readWidgetProperty(Object widget, String rawName) {
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
    static void restoreWidgetProperty(Object widget, String rawName, Object value) {
        String name = propertyName(LuaValue.valueOf(rawName));
        List<Method> methods = methodsNamed(widget, "set" + name);
        java.util.List<Method> compatible = new java.util.ArrayList<>();
        for (Method setter : methods) {
            if (setter.getParameterCount() != 1
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

    static boolean matchesWidgetClass(Class<?> actual, String requested) {
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

    static LuaError propertyFailure(String action, String name, ReflectiveOperationException failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation && invocation.getCause() != null
            ? invocation.getCause() : failure;
        return new LuaError("[MLua] Could not " + action + " native widget property '" + name + "': " + cause.getMessage());
    }

}
