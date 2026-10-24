package dev.mlua.modules.registry.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Validates ownership, inheritance, and callback contracts for registered content. */
final class MLuaRegistryContentValidation {
    private MLuaRegistryContentValidation() { }

    static void validateCallback(String name, LuaValue callback) {
        if (!callback.isnil() && !callback.isfunction()) {
            throw new LuaError("[MLua] Registry callback '" + name + "' must be a function");
        }
    }

    static String requireContentClass(dev.mlua.module.MLuaRegistration registration, LuaTable definition,
                                      String parentClass) {
        String className = MLuaRegistryPropertyReaders.stringProperty(definition, "className", "ClassName");
        if (className == null) return parentClass;
        if (className.isBlank() || registration.classTable(className) == null
                || !registration.isSubclassOf(className, parentClass)) {
            throw new LuaError("[MLua] Content class '" + className + "' must be registered and inherit from " + parentClass);
        }
        return className;
    }

    static void requireLocalRegistryName(String owner, String name, String operation) {
        try {
            dev.mlua.runtime.registry.MLuaResourceNameValidator.validate(owner);
            dev.mlua.runtime.registry.MLuaResourceNameValidator.validate(name);
            var id = dev.mlua.minecraft.MLuaResourceLocations.tryParseOrNull(owner + ":" + name);
            if (id == null || !owner.equals(dev.mlua.minecraft.MLuaResourceLocations.namespace(id))
                    || !name.equals(dev.mlua.minecraft.MLuaResourceLocations.path(id))) {
                throw new IllegalArgumentException("Invalid lowercase local registry path: " + name);
            }
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] " + operation
                    + " name must be a valid lowercase registry path without a namespace: " + invalid.getMessage());
        }
    }
}
