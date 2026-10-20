package dev.mlua.modules.javabridge.service;

/** Shared Java type classification used by value conversion and overload scoring. */
final class MLuaJavaConversionTypes {
    private MLuaJavaConversionTypes() { }

    static boolean isNumericType(Class<?> type) {
        return type == Byte.class
            || type == Short.class
            || type == Integer.class
            || type == Long.class
            || type == Float.class
            || type == Double.class;
    }
}
