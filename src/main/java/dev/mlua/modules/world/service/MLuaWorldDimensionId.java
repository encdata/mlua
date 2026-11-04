package dev.mlua.modules.world.service;

import dev.mlua.minecraft.MLuaResourceLocations;

final class MLuaWorldDimensionId {
    private MLuaWorldDimensionId() { }

    static String canonicalize(String id) {
        var location = MLuaResourceLocations.tryParseOrNull(id);
        return location == null ? null : location.toString();
    }

    static boolean matches(String dimensionId, String requestedId) {
        return requestedId != null && requestedId.equals(dimensionId);
    }
}
