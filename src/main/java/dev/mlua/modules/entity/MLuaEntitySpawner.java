package dev.mlua.modules.entity;

import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Shared server-side entity type resolution and spawn lifecycle for entity and world APIs. */
public final class MLuaEntitySpawner {
    private MLuaEntitySpawner() { }

    public static EntityType<?> resolveType(String owner, String requestedId) {
        if (requestedId == null || requestedId.isBlank()) return null;
        String id = requestedId.contains(":") ? requestedId : (owner == null || owner.isBlank() ? "minecraft" : owner) + ":" + requestedId;
        ResourceLocation location = MLuaResourceLocations.tryParseOrNull(id);
        if (location == null) return null;
        Object builtIn = MCApiRuntime.lookupRegistry("entity_type", location.toString());
        if (builtIn instanceof EntityType<?> entityType) return entityType;
        RegistryHandle<?> handle = MLuaLoaderRegistry.get(RegistryKind.ENTITY,
            MLuaResourceLocations.namespace(location), MLuaResourceLocations.path(location));
        Object registered = handle == null ? null : handle.get();
        return registered instanceof EntityType<?> entityType ? entityType : null;
    }

    public static Entity spawn(ServerLevel level, EntityType<?> type, double x, double y, double z,
                               float yaw, float pitch) {
        if (level == null || type == null) return null;
        BlockPos position = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(position)) {
            level.getChunk(position.getX() >> 4, position.getZ() >> 4);
        }
        if (!level.hasChunkAt(position)) return null;
        //--version 26.1
        Entity entity = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        //--endversion
        //--version 1.20.1,1.21.1
        Entity entity = type.create(level);
        //--endversion
        if (entity == null) return null;
        entity.setPos(x, y, z);
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        return level.addFreshEntity(entity) ? entity : null;
    }
}
