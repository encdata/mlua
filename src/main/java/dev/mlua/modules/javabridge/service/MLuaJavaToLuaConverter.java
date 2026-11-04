package dev.mlua.modules.javabridge.service;

import dev.mlua.api.MLuaLuaCoercions;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Converts Java arrays and collections into bounded Lua table snapshots. */
final class MLuaJavaToLuaConverter {
    private final MLuaJavaService service;

    MLuaJavaToLuaConverter(MLuaJavaService service) {
        this.service = service;
    }

    LuaValue convert(Object value, dev.mlua.runtime.client.MLuaRenderFrameScope.View frameScope) {
        return toLuaTable(value, new IdentityHashMap<>(), 0, new long[]{0}, frameScope);
    }

    LuaValue toLuaTable(Object value, IdentityHashMap<Object, Boolean> active, int depth, long[] entries,
                                dev.mlua.runtime.client.MLuaRenderFrameScope.View frameScope) {
        if (value == null) {
            return LuaValue.NIL;
        }
        MLuaJavaService.requireConversionDepth(depth, "Java-to-Lua");
        if (active.put(value, Boolean.TRUE) != null) {
            throw new LuaError("[MLua] Java:ToTable cannot convert a cyclic collection");
        }
        try {
            return convertCompositeToLuaTable(value, active, depth, entries, frameScope);
        } finally {
            active.remove(value);
        }
    }

    private LuaValue convertCompositeToLuaTable(Object value, IdentityHashMap<Object, Boolean> active,
            int depth, long[] entries, dev.mlua.runtime.client.MLuaRenderFrameScope.View frameScope) {
        if (value.getClass().isArray()) {
            LuaTable array = new LuaTable();
            int length = Array.getLength(value);
            MLuaJavaService.requireConversionEntries(length, "Java-to-Lua");
            for (int i = 0; i < length; i++) {
                addConversionEntry(entries, "Java-to-Lua");
                array.set(i + 1, toLuaTableValue(Array.get(value, i), active, depth + 1, entries, frameScope));
            }
            return array;
        }
        // A custom type may implement both Map and Iterable. Preserve its
        // key/value contract instead of exposing its iteration as an array.
        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            MLuaJavaService.requireConversionEntries(map.size(), "Java-to-Lua");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                addConversionEntry(entries, "Java-to-Lua");
                LuaValue key = toLuaMapKey(entry.getKey());
                if (!table.rawget(key).isnil()) {
                    throw new LuaError("[MLua] Java:ToTable found map keys that collapse to the same Lua key: "
                        + entry.getKey());
                }
                table.set(key, toLuaTableValue(entry.getValue(), active, depth + 1, entries, frameScope));
            }
            return table;
        }
        if (value instanceof List<?> list) {
            LuaTable array = new LuaTable();
            MLuaJavaService.requireConversionEntries(list.size(), "Java-to-Lua");
            for (int i = 0; i < list.size(); i++) {
                addConversionEntry(entries, "Java-to-Lua");
                array.set(i + 1, toLuaTableValue(list.get(i), active, depth + 1, entries, frameScope));
            }
            return array;
        }
        if (value instanceof Collection<?> collection) {
            LuaTable array = new LuaTable();
            int index = 1;
            for (Object item : collection) {
                addConversionEntry(entries, "Java-to-Lua");
                array.set(index++, toLuaTableValue(item, active, depth + 1, entries, frameScope));
            }
            return array;
        }
        if (value instanceof Iterable<?> iterable) {
            // Iterable does not promise a size, so copy it through the bounded
            // iterator path before building the Lua array. This also prevents
            // unbounded/lazy iterables from hanging a script conversion.
            List<Object> values = MLuaJavaService.copyBoundedIterable(iterable, "Java-to-Lua");
            LuaTable array = new LuaTable();
            int index = 1;
            for (Object item : values) {
                addConversionEntry(entries, "Java-to-Lua");
                array.set(index++, toLuaTableValue(item, active, depth + 1, entries, frameScope));
            }
            return array;
        }
        throw new LuaError("[MLua] Java:ToTable only supports arrays, Iterable values, and maps. Got "
            + value.getClass().getName());
    }

    private static void addConversionEntry(long[] entries, String direction) {
        entries[0]++;
        MLuaJavaService.requireConversionEntries(entries[0], direction);
    }

    private LuaValue toLuaTableValue(Object value, IdentityHashMap<Object, Boolean> active,
            int depth, long[] entries, dev.mlua.runtime.client.MLuaRenderFrameScope.View frameScope) {
        if (value == null) {
            return MLuaJavaService.JAVA_NULL;
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent()
                ? toLuaTableValue(optional.get(), active, depth, entries, frameScope)
                : MLuaJavaService.JAVA_NULL;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character) {
            if (value instanceof Character character) return LuaValue.valueOf(character.toString());
            return service.wrapJavaValue(value);
        }
        if (value.getClass().isArray() || value instanceof Iterable<?> || value instanceof Map<?, ?>) {
            return toLuaTable(value, active, depth, entries, frameScope);
        }
        return service.wrapJavaValue(value, Object.class, frameScope);
    }

    private LuaValue toLuaMapKey(Object key) {
        try {
            return MLuaLuaCoercions.toLuaMapKey(key);
        } catch (IllegalArgumentException invalidKey) {
            throw new LuaError("[MLua] Java:ToTable " + invalidKey.getMessage());
        }
    }

}
