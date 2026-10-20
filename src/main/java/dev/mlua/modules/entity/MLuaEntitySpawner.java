package dev.mlua.modules.entity;

//--version 26.1
import net.minecraft.world.entity.EntitySpawnReason;
//--endversion

import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.server.MLuaServerThread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Shared server-side entity type resolution and spawn lifecycle for entity and world APIs. */
public final class MLuaEntitySpawner {
    private MLuaEntitySpawner() { }

    public static EntityType<?> resolveType(String owner, String requestedId) {
        return resolveType(null, owner, requestedId);
    }

    /** Resolves against the live world's registry first, then vanilla and add-on registrations. */
    public static EntityType<?> resolveType(ServerLevel level, String owner, String requestedId) {
        if (requestedId == null || requestedId.isBlank()) return null;
        String id = requestedId.contains(":") ? requestedId : (owner == null || owner.isBlank() ? "minecraft" : owner) + ":" + requestedId;
        ResourceLocation location = MLuaResourceLocations.tryParseOrNull(id);
        if (location == null) return null;
        if (level != null) {
            EntityType<?> worldType = level.registryAccess()
                .registryOrThrow(Registries.ENTITY_TYPE).getOptional(location).orElse(null);
            if (worldType != null) return worldType;
        }
        EntityType<?> builtIn = BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElse(null);
        if (builtIn != null) return builtIn;
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
            MLuaServerThread.call(level.getServer(),
                () -> level.getChunk(position.getX() >> 4, position.getZ() >> 4));
        }
        if (!level.hasChunkAt(position)) return null;
        //--version 26.1
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
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
