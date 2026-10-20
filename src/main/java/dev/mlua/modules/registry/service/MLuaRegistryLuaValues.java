package dev.mlua.modules.registry.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.engine.MLuaLuaBindings;

/** Shared validation and Lua table conversion for Registry service components. */
final class MLuaRegistryLuaValues {
    private static final Set<String> BLOCK_ENTITY_CALLBACKS =
        Set.of("onLoad", "onUnload", "onOpen", "onClose", "onContainerChanged");

    private MLuaRegistryLuaValues() { }

    static Map<String, LuaValue> collectCallbacks(LuaTable properties, String... eventNames) {
        if (properties == null || eventNames.length == 0) return Map.of();
        Map<String, LuaValue> callbacks = new HashMap<>(eventNames.length);
        for (String eventName : eventNames) {
            LuaValue callback = properties.get(eventName);
            if (callback.isnil()) continue;
            if (!callback.isfunction()) {
                throw new LuaError("[MLua] Registry callback '" + eventName + "' must be a function");
            }
            callbacks.put(eventName, callback);
        }
        return Map.copyOf(callbacks);
    }

    static Map<String, Object> registryPropertiesMap(LuaTable properties, String... callbackNames) {
        if (properties == null) return Map.of();
        Set<String> callbacks = callbackNames.length == 0 ? Set.of() : Set.of(callbackNames);
        LuaTable values = new LuaTable();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = properties.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            String propertyName = key.type() == LuaValue.TSTRING ? key.tojstring() : null;
            if (propertyName != null && callbacks.contains(propertyName)) continue;

            LuaValue value = next.arg(2);
            if (("blockEntity".equals(propertyName) || "BlockEntity".equals(propertyName)) && value.istable()) {
                value = withoutBlockEntityCallbacks(value.checktable());
            }
            values.set(key, value);
        }
        return MLuaLuaBindings.mapFromLua(values);
    }

    private static LuaTable withoutBlockEntityCallbacks(LuaTable source) {
        LuaTable filtered = new LuaTable();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = source.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (key.type() == LuaValue.TSTRING && BLOCK_ENTITY_CALLBACKS.contains(key.tojstring())) continue;
            filtered.set(key, next.arg(2));
        }
        return filtered;
    }

    static String optionalNamespace(LuaValue value) {
        if (value == null || value.isnil()) return null;
        String namespace = value.checkjstring().trim();
        if (namespace.isEmpty() || !isNamespace(namespace)) {
            throw new LuaError("[MLua] Registry namespace must contain lowercase letters, digits, '_', '.', or '-'");
        }
        return namespace;
    }

    private static boolean isNamespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '_' || character == '.' || character == '-')) return false;
        }
        return true;
    }

    static LuaTable stringArray(List<String> values) {
        LuaTable result = new LuaTable();
        for (int index = 0; index < values.size(); index++) {
            result.set(index + 1, LuaValue.valueOf(values.get(index)));
        }
        return result;
    }

    static LuaTable enumNames(Enum<?>[] values) {
        return stringArray(Arrays.stream(values)
            .map(value -> value.name().toLowerCase(Locale.ROOT)).toList());
    }
}
