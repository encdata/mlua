package dev.mlua.modules.registry.service;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.minecraft.binding.MLuaLuaArguments;

/** Reads and validates typed properties from Lua registry definition tables. */
final class MLuaRegistryPropertyReaders {
    private MLuaRegistryPropertyReaders() { }

    static JsonObject optionalJsonObject(LuaTable table, String... keys) {
        if (table == null) return null;

        for (String key : keys) {
            LuaValue value = table.get(key);
            if (!value.isnil()) return MLuaLuaJson.objectArg(value, key);
        }
        return null;
    }

    static List<String> stringValues(LuaValue value, String label) {
        List<String> values = new ArrayList<>();
        if (value.type() == LuaValue.TSTRING) {
            values.add(value.checkjstring());
        } else if (value.istable()) {
            LuaTable entries = value.checktable();
            int length = MLuaLuaJson.jsonArrayLength(entries);
            if (length < 0) throw new LuaError("[MLua] " + label + " table must use dense array indexes");
            for (int index = 1; index <= length; index++) {
                LuaValue entry = entries.get(index);
                if (entry.type() != LuaValue.TSTRING) {
                    throw new LuaError("[MLua] " + label + " entries must be strings");
                }
                values.add(entry.tojstring());
            }
        } else {
            throw new LuaError("[MLua] " + label + " must be an id or an array of ids");
        }
        if (values.isEmpty() || values.stream().anyMatch(String::isBlank)) {
            throw new LuaError("[MLua] " + label + " must contain at least one non-empty id");
        }
        return List.copyOf(values);
    }

    static String stringProperty(LuaTable table, String... keys) {
        if (table == null) return null;
        for (String key : keys) {
            LuaValue value = table.get(key);
            if (value.type() == LuaValue.TSTRING) return value.tojstring();
        }
        return null;
    }

    static boolean booleanProperty(LuaTable table, String... keys) {
        if (table == null) return false;
        LuaValue selected = LuaValue.NIL;
        String selectedKey = null;
        for (String key : keys) {
            LuaValue value = table.get(key);
            if (value.isnil()) continue;
            if (selectedKey != null) {
                throw new LuaError("[MLua] Registry properties '" + selectedKey + "' and '" + key
                        + "' are aliases; provide only one.");
            }
            if (!value.isboolean()) throw new LuaError("[MLua] Registry property '" + key + "' must be a boolean.");
            selected = value;
            selectedKey = key;
        }
        return !selected.isnil() && selected.toboolean();
    }

    static boolean hasProperty(LuaTable table, String... keys) {
        if (table == null) return false;
        for (String key : keys) if (!table.get(key).isnil()) return true;
        return false;
    }

    static LuaValue callbackProperty(LuaTable table, String... keys) {
        if (table == null) return LuaValue.NIL;
        for (String key : keys) {
            LuaValue value = table.get(key);
            if (!value.isnil()) return value;
        }
        return LuaValue.NIL;
    }

    static int wholeIntProperty(LuaTable table, int fallback, String... keys) {
        if (table == null) return fallback;
        for (String key : keys) {
            LuaValue value = table.get(key);
            if (!value.isnil()) return MLuaLuaArguments.wholeInt(value, "Registry.Effects:Register", key);
        }
        return fallback;
    }

    static int colorProperty(LuaTable table, int fallback, String... keys) {
        if (table == null) return fallback;
        for (String key : keys) {
            LuaValue value = table.get(key);
            if (value.isnil()) continue;
            if (value.isnumber()) return (int) value.tolong();
            if (value.type() != LuaValue.TSTRING) continue;
            String text = value.tojstring().trim();
            if (text.startsWith("#")) text = text.substring(1);
            else if (text.startsWith("0x") || text.startsWith("0X")) text = text.substring(2);
            try {
                return (int) Long.parseLong(text, 16);
            } catch (NumberFormatException invalid) {
                throw new LuaError("[MLua] Invalid color value: " + value.tojstring());
            }
        }
        return fallback;
    }
}
