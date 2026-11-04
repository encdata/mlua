package dev.mlua.runtime.registry;

import dev.mlua.api.MLuaLuaCoercions;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Bounded defensive snapshots passed from Lua registry definitions to Java content providers.
 * Maps, collections, arrays, and their structural keys are copied; opaque Java values retain identity.
 */
final class MLuaDefinitionSnapshot {
    private static final int MAX_DEPTH = 64;

    private MLuaDefinitionSnapshot() { }

    static Map<String, Object> freeze(Map<String, Object> definition) {
        if (definition == null) throw new IllegalArgumentException("Content definition cannot be null");
        long[] entries = {0L};
        Object snapshot = copy(definition, new IdentityHashMap<>(), 0, entries);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) snapshot;
        return result;
    }

    /**
     * Validates an isolated view, then creates a fresh copy for the provider's construction step.
     * This prevents mutation of cloned arrays during validation from affecting construction.
     */
    static Map<String, Object> validateAndFreeze(Map<String, Object> definition,
                                                 Consumer<Map<String, Object>> validator) {
        if (validator == null) throw new NullPointerException("validator");
        validator.accept(freeze(definition));
        return freeze(definition);
    }

    private static Object copy(Object value, IdentityHashMap<Object, Boolean> active, int depth, long[] entries) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof Class<?>) return value;
        if (depth >= MAX_DEPTH) {
            throw new IllegalArgumentException("Content definition exceeds " + MAX_DEPTH + " nested container levels");
        }
        if (active.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Content definition cannot contain cyclic maps, collections, or arrays");
        }
        try {
            if (value instanceof Map<?, ?> map) {
                LinkedHashMap<Object, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    consume(entries);
                    Object key = copy(entry.getKey(), active, depth + 1, entries);
                    Object copiedValue = copy(entry.getValue(), active, depth + 1, entries);
                    if (result.containsKey(key)) {
                        throw new IllegalArgumentException("Content definition map keys collide after snapshot copying");
                    }
                    result.put(key, copiedValue);
                }
                return Collections.unmodifiableMap(result);
            }
            if (value instanceof List<?> list) {
                ArrayList<Object> result = new ArrayList<>(list.size());
                for (Object item : list) {
                    consume(entries);
                    result.add(copy(item, active, depth + 1, entries));
                }
                return Collections.unmodifiableList(result);
            }
            if (value instanceof Set<?> set) {
                LinkedHashSet<Object> result = new LinkedHashSet<>();
                for (Object item : set) {
                    consume(entries);
                    result.add(copy(item, active, depth + 1, entries));
                }
                return Collections.unmodifiableSet(result);
            }
            if (value instanceof Collection<?> collection) {
                ArrayList<Object> result = new ArrayList<>(collection.size());
                for (Object item : collection) {
                    consume(entries);
                    result.add(copy(item, active, depth + 1, entries));
                }
                return Collections.unmodifiableList(result);
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                Object result = Array.newInstance(value.getClass().getComponentType(), length);
                for (int index = 0; index < length; index++) {
                    consume(entries);
                    Array.set(result, index, copy(Array.get(value, index), active, depth + 1, entries));
                }
                return result;
            }
            return value;
        } finally {
            active.remove(value);
        }
    }

    private static void consume(long[] entries) {
        if (++entries[0] > MLuaLuaCoercions.MAX_CONVERSION_ENTRIES) {
            throw new IllegalArgumentException("Content definition exceeds the table entry limit of "
                + MLuaLuaCoercions.MAX_CONVERSION_ENTRIES);
        }
    }
}
