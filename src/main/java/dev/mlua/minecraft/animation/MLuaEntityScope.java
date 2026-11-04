package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.Side;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Stable identity for state attached to an entity in one logical world. */
public record MLuaEntityScope(Side side, String dimension, UUID uuid, int entityId) {
    public static MLuaEntityScope of(Entity entity) {
        return new MLuaEntityScope(
                entity.level().isClientSide() ? Side.CLIENT : Side.SERVER,
                dev.mlua.minecraft.MLuaDimensionId.from(entity.level()),
                entity.getUUID(),
                entity.getId());
    }

    public static MLuaEntityScope legacy(int entityId) {
        return new MLuaEntityScope(Side.BOTH, "", new UUID(0L, 0L), entityId);
    }
}
