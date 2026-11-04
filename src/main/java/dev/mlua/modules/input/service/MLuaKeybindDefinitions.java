package dev.mlua.modules.input.service;

import dev.mlua.minecraft.client.MLuaKeybindController;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns Lua definition parsing and validation for addon keybind registrations. */
final class MLuaKeybindDefinitions {
    record Definition(String name, String label, String category, LuaValue defaultKey) { }

    private MLuaKeybindDefinitions() { }

    static Definition parse(String name, LuaTable properties, String operation) {
        if (properties == null) properties = new LuaTable();
        String label = optionalString(properties, "label", "Label", name, operation);
        String category = optionalString(properties, "category", "Category", "key.categories.mlua", operation);
        LuaValue defaultKey = aliasedValue(properties, "defaultKey", "DefaultKey", operation);
        LuaValue keyAlias = aliasedValue(properties, "key", "Key", operation);
        if (!defaultKey.isnil() && !keyAlias.isnil()) {
            throw new LuaError("[MLua] " + operation + " options 'defaultKey' and 'key' are aliases; provide only one.");
        }
        LuaValue selectedKey = defaultKey.isnil() ? keyAlias : defaultKey;
        if (!selectedKey.isnil() && !selectedKey.isstring() && !selectedKey.isnumber() && !selectedKey.istable()) {
            throw new LuaError("[MLua] " + operation + " defaultKey must be a key name, key code, key table, or nil.");
        }
        MLuaKeybindController.validateKey(selectedKey);
        return new Definition(name, label, category, selectedKey);
    }

    static MLuaKeybindController.KeybindDefinition toControllerDefinition(Definition definition) {
        return new MLuaKeybindController.KeybindDefinition(definition.name(), definition.label(),
            definition.category(), definition.defaultKey());
    }

    static String requireName(String value, String operation) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || !name.equals(value)) {
            throw new LuaError("[MLua] " + operation + " requires a non-blank name without surrounding whitespace.");
        }
        return name;
    }

    private static String optionalString(LuaTable properties, String lowerName, String upperName,
                                         String defaultValue, String operation) {
        LuaValue value = aliasedValue(properties, lowerName, upperName, operation);
        if (value.isnil()) return defaultValue;
        if (!value.isstring()) {
            throw new LuaError("[MLua] " + operation + " option '" + lowerName + "' must be a string.");
        }
        String text = value.checkjstring().trim();
        if (text.isEmpty()) throw new LuaError("[MLua] " + operation + " option '" + lowerName + "' cannot be blank.");
        return text;
    }

    private static LuaValue aliasedValue(LuaTable properties, String lowerName, String upperName, String operation) {
        LuaValue lower = properties.rawget(lowerName);
        LuaValue upper = properties.rawget(upperName);
        if (!lower.isnil() && !upper.isnil()) {
            throw new LuaError("[MLua] " + operation + " options '" + lowerName + "' and '" + upperName
                + "' are aliases; provide only one.");
        }
        return lower.isnil() ? upper : lower;
    }
}
