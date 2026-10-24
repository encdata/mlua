package dev.mlua.modules.registry.service;

import org.luaj.vm2.LuaTable;
import dev.mlua.runtime.registry.MLuaAssetResolver;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;

/** Resolves texture and model fields from Lua content definitions. */
final class MLuaRegistryVisualDefinitions {
    private MLuaRegistryVisualDefinitions() { }

    static MLuaTextureDescriptor textureDescriptor(LuaTable definition, String... keys) {
        if (definition != null) {
            for (String key : keys) {
                var value = definition.get(key);
                if (!value.isnil()) return MLuaTextureDescriptor.fromLua(value);
            }
        }
        return MLuaTextureDescriptor.empty();
    }

    static String resolvedModel(String owner, String defaultFolder, LuaTable definition, String... keys) {
        String model = MLuaRegistryPropertyReaders.stringProperty(definition, keys);
        return model != null && !model.isBlank()
                ? MLuaAssetResolver.resolveModelString(owner, model, defaultFolder) : null;
    }
}
