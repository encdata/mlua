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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Comparator;

/** Reflection-backed access to public native widget methods and bean properties. */
final class MLuaNativeControlReflection {
    private static final ClassValue<WidgetMethodIndex> WIDGET_METHODS = new ClassValue<>() {
        @Override protected WidgetMethodIndex computeValue(Class<?> type) {
            return new WidgetMethodIndex(type);
        }
    };

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
                    return toLuaValue(getter.invoke(widget));
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
            Method[] methods = widgetMethods(backing.widget());
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
                Method selected = null;
                Object[] selectedArguments = null;
                int bestScore = Integer.MAX_VALUE;
                java.util.List<Method> tiedMethods = new java.util.ArrayList<>();
                for (Method method : methodsNamed(backing.widget(), name)) {
                    if (!isExposedWidgetMethod(method) || method.getParameterCount() != values.length) continue;
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
        return WIDGET_METHODS.get(widget.getClass()).propertyMethod(methodName, parameterCount);
    }

    private static Method[] widgetMethods(Object widget) {
        return WIDGET_METHODS.get(widget.getClass()).methods;
    }

    private static List<Method> methodsNamed(Object widget, String name) {
        return WIDGET_METHODS.get(widget.getClass()).methodsNamed(name);
    }

    /** Writes a public Java bean property on a native widget using MLua's supported Lua conversions. */
    static void setWidgetProperty(Object widget, String rawName, LuaValue value) {
        String name = propertyName(LuaValue.valueOf(rawName));
        String setterName = "set" + name;
        List<Method> methods = methodsNamed(widget, setterName);
        Method selected = null;
        Object selectedValue = null;
        int bestScore = Integer.MAX_VALUE;
        java.util.List<Method> tiedMethods = new java.util.ArrayList<>();
        for (Method setter : methods) {
            if (setter.getParameterCount() != 1
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

    private static final class WidgetMethodIndex {
        private final Method[] methods;
        private final Map<String, List<Method>> methodsByName;
        private final Map<MethodKey, Method> properties;

        private WidgetMethodIndex(Class<?> type) {
            this.methods = type.getMethods();
            Arrays.sort(this.methods, Comparator.comparing(Method::toGenericString));
            Map<MethodKey, Method> indexed = new HashMap<>();
            Map<String, List<Method>> named = new HashMap<>();
            for (Method method : this.methods) {
                named.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(method);
                if (Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass() == Object.class
                        || method.isBridge() || method.isSynthetic()) continue;
                MethodKey key = new MethodKey(method.getName(), method.getParameterCount());
                Method previous = indexed.get(key);
                if (previous == null || betterPropertyMethod(type, method, previous)) indexed.put(key, method);
            }
            named.replaceAll((name, matches) -> List.copyOf(matches));
            this.methodsByName = Map.copyOf(named);
            this.properties = Map.copyOf(indexed);
        }

        private List<Method> methodsNamed(String name) {
            return this.methodsByName.getOrDefault(name, List.of());
        }

        private Method propertyMethod(String name, int parameterCount) {
            return this.properties.get(new MethodKey(name, parameterCount));
        }

        private static boolean betterPropertyMethod(Class<?> actual, Method candidate, Method previous) {
            int candidateDistance = inheritanceDistance(actual, candidate.getDeclaringClass());
            int previousDistance = inheritanceDistance(actual, previous.getDeclaringClass());
            return candidateDistance < previousDistance || (candidateDistance == previousDistance
                && candidate.toGenericString().compareTo(previous.toGenericString()) < 0);
        }

        private static int inheritanceDistance(Class<?> actual, Class<?> declaring) {
            int distance = 0;
            for (Class<?> current = actual; current != null; current = current.getSuperclass()) {
                if (current == declaring) return distance;
                distance++;
            }
            return 64;
        }
    }

    private record MethodKey(String name, int parameterCount) { }

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

    static LuaValue toLuaValue(Object value) {
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

    static LuaError propertyFailure(String action, String name, ReflectiveOperationException failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation && invocation.getCause() != null
            ? invocation.getCause() : failure;
        return new LuaError("[MLua] Could not " + action + " native widget property '" + name + "': " + cause.getMessage());
    }

}
