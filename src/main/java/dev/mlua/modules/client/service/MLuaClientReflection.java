package dev.mlua.modules.client.service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Cached compatibility access for Minecraft client members whose names vary by target. */
final class MLuaClientReflection {
    private static final ClassValue<ConcurrentMap<String, Optional<Method>>> METHODS = new ClassValue<>() {
        @Override protected ConcurrentMap<String, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<ConcurrentMap<String, Optional<Field>>> FIELDS = new ClassValue<>() {
        @Override protected ConcurrentMap<String, Optional<Field>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private MLuaClientReflection() { }

    static Object invoke(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            Method method = METHODS.get(target.getClass())
                .computeIfAbsent(name, key -> Optional.ofNullable(findMethod(target.getClass(), key)))
                .orElse(null);
            if (method == null) continue;
            try {
                return method.invoke(target);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Error fatal) throw fatal;
                throw accessorFailure("method", name, cause);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                throw accessorFailure("method", name, failure);
            }
        }
        return null;
    }

    static Object field(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            Field field = FIELDS.get(target.getClass())
                .computeIfAbsent(name, key -> Optional.ofNullable(findField(target.getClass(), key)))
                .orElse(null);
            if (field == null) continue;
            try {
                return field.get(target);
            } catch (IllegalAccessException | RuntimeException failure) {
                throw accessorFailure("field", name, failure);
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        try {
            Method publicMethod = type.getMethod(name);
            publicMethod.trySetAccessible();
            return publicMethod;
        } catch (NoSuchMethodException ignored) {
            // Search declared methods as a fallback for version-specific non-public accessors.
        } catch (RuntimeException inaccessible) {
            return null;
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                return method.trySetAccessible() ? method : null;
            } catch (NoSuchMethodException ignored) {
                // Continue through the superclass chain.
            } catch (RuntimeException inaccessible) {
                return null;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                return field.trySetAccessible() ? field : null;
            } catch (NoSuchFieldException ignored) {
                // Continue through the superclass chain.
            } catch (RuntimeException inaccessible) {
                return null;
            }
        }
        return null;
    }

    private static IllegalStateException accessorFailure(String kind, String name, Throwable failure) {
        return new IllegalStateException("Unable to read Minecraft client " + kind + " '" + name + "'.", failure);
    }
}
