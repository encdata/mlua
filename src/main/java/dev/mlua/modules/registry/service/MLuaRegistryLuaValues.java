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
import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.engine.MLuaLuaBindings;

/** Shared validation and Lua table conversion for Registry service components. */
final class MLuaRegistryLuaValues {
    private static final String[] ITEM_CALLBACKS = {
        "onUse", "onUseOnBlock", "onInventoryTick", "onCrafted", "onConsume", "onAttackEntity"
    };
    private static final String[] BLOCK_CALLBACKS = {
        "onRightClick", "onPlace", "onBreak", "onStepOn", "onEntityInside",
        "onNeighborChanged", "onRemove", "onRandomTick", "onTick", "onFallOn", "onProjectileHit"
    };
    private static final String[] BLOCK_ENTITY_CALLBACK_NAMES = {
        "onLoad", "onUnload", "onOpen", "onClose", "onContainerChanged"
    };
    private static final Set<String> BLOCK_ENTITY_CALLBACKS =
        Set.of(BLOCK_ENTITY_CALLBACK_NAMES);

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

    static Map<String, Object> itemRegistryProperties(LuaTable properties) {
        return registryPropertiesMap(properties, ITEM_CALLBACKS);
    }

    static Map<String, LuaValue> itemCallbacks(LuaTable properties) {
        return collectCallbacks(properties, ITEM_CALLBACKS);
    }

    static Map<String, Object> blockRegistryProperties(LuaTable properties) {
        return registryPropertiesMap(properties, BLOCK_CALLBACKS);
    }

    static Map<String, LuaValue> blockCallbacks(LuaTable properties) {
        return collectCallbacks(properties, BLOCK_CALLBACKS);
    }

    static Map<String, LuaValue> blockEntityCallbacks(LuaTable properties) {
        return collectCallbacks(properties, BLOCK_ENTITY_CALLBACK_NAMES);
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

    static LuaTable typeCatalog(Map<String, String> types) {
        LuaTable result = new LuaTable();
        types.forEach((name, description) -> result.set(name, description));
        return result;
    }

    static String requiredString(Varargs args, int index, String method, String parameter) {
        LuaValue value = args.arg(index);
        if (value.type() != LuaValue.TSTRING || value.tojstring().isBlank()) {
            throw new LuaError("[MLua] " + method + " expects a non-empty string for " + parameter + ".");
        }
        return value.tojstring();
    }

    static LuaError validationError(IllegalArgumentException invalid) {
        return MLuaLuaErrors.javaFailure("Registry definition", invalid);
    }
}
