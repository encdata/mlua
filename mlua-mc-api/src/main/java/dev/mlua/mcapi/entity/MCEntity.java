package dev.mlua.mcapi.entity;

import dev.mlua.mcapi.core.MCAABB;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.world.MCLevel;

import java.util.UUID;

public interface MCEntity {
    double getX();
    double getY();
    double getZ();
    MCVec3 getPosition();
    @Deprecated(forRemoval = false)
    default MCVec3 position() { return getPosition(); }
    MCVec3 getDeltaMovement();
    void setDeltaMovement(MCVec3 v);
    MCAABB getBoundingBox();
    int getId();
    UUID getUUID();
    MCBlockPos getBlockPosition();
    @Deprecated(forRemoval = false)
    default MCBlockPos blockPosition() { return getBlockPosition(); }
    MCLevel getLevel();
    @Deprecated(forRemoval = false)
    default MCLevel level() { return getLevel(); }
    void setYRot(float yRot);
    void setXRot(float xRot);
    float getYRot();
    float getXRot();
    boolean isAlive();
}
