package dev.mlua.modules.javabridge.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Shared formatting and primitive boxing rules for Java reflection APIs. */
final class MLuaJavaReflectionSupport {
    private MLuaJavaReflectionSupport() { }

    static String describeArgs(Object[] rawArgs) {
        List<String> parts = new ArrayList<>(rawArgs.length);
        for (Object rawArg : rawArgs) {
            if (rawArg instanceof MLuaJavaTableArgument) parts.add("LuaTable");
            else parts.add(rawArg == null ? "nil" : rawArg.getClass().getName());
        }
        return parts.toString();
    }

    static String describeMethods(List<Method> methods) {
        List<String> parts = new ArrayList<>(methods.size());
        for (Method method : methods) parts.add(methodSignature(method));
        return parts.toString();
    }

    static String describeConstructors(Class<?> clazz) {
        List<String> parts = new ArrayList<>();
        for (Constructor<?> constructor : clazz.getConstructors()) {
            StringBuilder builder = new StringBuilder(clazz.getName()).append("(");
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            for (int index = 0; index < parameterTypes.length; index++) {
                if (index > 0) builder.append(", ");
                builder.append(parameterTypes[index].getName());
            }
            builder.append(")");
            parts.add(builder.toString());
        }
        return parts.toString();
    }

    static String rootMessage(Exception exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation
            && invocation.getCause() != null ? invocation.getCause() : exception;
        return cause.getClass().getName() + ": " + cause.getMessage();
    }

    static String methodSignature(Method method) {
        StringBuilder builder = new StringBuilder(method.getName()).append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int index = 0; index < parameterTypes.length; index++) {
            if (index > 0) builder.append(", ");
            builder.append(parameterTypes[index].getName());
        }
        return builder.append(") -> ").append(method.getReturnType().getName()).toString();
    }

    static Class<?> boxType(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof java.lang.reflect.ParameterizedType parameterized
                && parameterized.getRawType() instanceof Class<?> clazz) return clazz;
        if (type instanceof java.lang.reflect.GenericArrayType genericArray) {
            return java.lang.reflect.Array.newInstance(rawClass(genericArray.getGenericComponentType()), 0).getClass();
        }
        return Object.class;
    }

}
