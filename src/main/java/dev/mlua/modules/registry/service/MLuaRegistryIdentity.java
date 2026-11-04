package dev.mlua.modules.registry.service;

import org.luaj.vm2.LuaError;

import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.MLuaResourceLocations;

/** Applies addon namespace and identifier rules shared by every Registry endpoint. */
final class MLuaRegistryIdentity {
    private final MLuaService.Context context;

    MLuaRegistryIdentity(MLuaService.Context context) {
        this.context = context;
    }

    String registryId(String name) {
        return normalize(name, this.context.mlua().ownerModId());
    }

    static String normalize(String name, String defaultNamespace) {
        String candidate = name.trim();
        if (!candidate.contains(":")) candidate = defaultNamespace + ":" + candidate;
        var id = MLuaResourceLocations.tryParseOrNull(candidate);
        if (id == null) throw new LuaError("[MLua] Invalid registry identifier: " + name);
        return id.toString();
    }

    String ownedRegistryId(String name, String owner, String category) {
        String id = normalize(name, this.context.mlua().ownerModId());
        return requireOwnerNamespace(id, owner, category);
    }

    static String requireOwnerNamespace(String id, String owner, String category) {
        String namespace = MLuaResourceLocations.namespace(MLuaResourceLocations.parse(id));
        if (!owner.equals(namespace)) {
            throw new LuaError("[MLua] Registry." + category
                + ":Register can only add entries in the calling addon's namespace '" + owner + "'; got '" + id + "'");
        }
        return id;
    }

    String qualifyOwnBlock(String blockName) {
        return blockName.contains(":") ? blockName : this.context.mlua().ownerModId() + ":" + blockName;
    }
}
