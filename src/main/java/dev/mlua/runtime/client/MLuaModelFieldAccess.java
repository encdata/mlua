package dev.mlua.runtime.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Cached reflective access to model and renderer fields used by addon adapters. */
final class MLuaModelFieldAccess {
    private static final ConcurrentMap<String, List<String>> PATH_SEGMENTS = new ConcurrentHashMap<>();
    private static final ClassValue<ConcurrentMap<String, Optional<Field>>> FIELDS = new ClassValue<>() {
        @Override protected ConcurrentMap<String, Optional<Field>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<Field[]> RENDERER_FIELDS = new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> rendererClass) {
            ArrayList<Field> fields = new ArrayList<>();
            for (Class<?> type = rendererClass; type != null; type = type.getSuperclass()) {
                Field[] declared = type.getDeclaredFields();
                Arrays.sort(declared, Comparator.comparing(Field::getName));
                for (Field field : declared) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || field.getType().isPrimitive()) continue;
                    try {
                        if (field.trySetAccessible()) fields.add(field);
                    } catch (RuntimeException inaccessible) {
                        // Other renderer fields may still expose the adapted model.
                    }
                }
            }
            return fields.toArray(Field[]::new);
        }
    };

    private MLuaModelFieldAccess() { }

    static Field[] rendererFields(Class<?> rendererClass) {
        return RENDERER_FIELDS.get(rendererClass);
    }

    static Object resolveFieldPath(Object root, String path) {
        Object current = root;
        for (String segment : pathSegments(path)) {
            current = readField(current, segment);
            if (current == null) return null;
        }
        return current;
    }

    static Object readField(Object target, String name) {
        if (target == null) return null;
        Optional<Field> field = FIELDS.get(target.getClass()).computeIfAbsent(name,
            fieldName -> findField(target.getClass(), fieldName));
        if (field.isEmpty()) return null;
        try {
            return field.get().get(target);
        } catch (IllegalAccessException | RuntimeException inaccessible) {
            return null;
        }
    }

    static List<String> pathSegments(String path) {
        return PATH_SEGMENTS.computeIfAbsent(path, value -> List.of(value.split("\\.", -1)));
    }

    static void clearPathSegments() {
        PATH_SEGMENTS.clear();
    }

    private static Optional<Field> findField(Class<?> modelClass, String name) {
        for (Class<?> type = modelClass; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
            } catch (RuntimeException inaccessible) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
