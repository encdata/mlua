package dev.mlua.modules.registry.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Shared validation and Lua table conversion for Registry service components. */
final class MLuaRegistryLuaValues {
    private MLuaRegistryLuaValues() { }

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
