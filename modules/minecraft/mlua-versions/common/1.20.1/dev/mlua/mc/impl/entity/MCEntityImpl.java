package dev.mlua.mc.impl.entity;

import dev.mlua.mcapi.core.MCAABB;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.world.MCLevel;
import dev.mlua.mc.impl.core.MCAABBImpl;
import dev.mlua.mc.impl.core.MCBlockPosImpl;
import dev.mlua.mc.impl.core.MCVec3Impl;
import dev.mlua.mc.impl.world.MCLevelImpl;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

public class MCEntityImpl implements MCEntity {
    protected final Entity inner;

    public MCEntityImpl(Entity inner) {
        this.inner = inner;
    }

    public Entity inner() {
        return inner;
    }

    @Override
    public double getX() {
        return inner.getX();
    }

    @Override
    public double getY() {
        return inner.getY();
    }

    @Override
    public double getZ() {
        return inner.getZ();
    }

    @Override
    public MCVec3 getPosition() {
        var p = inner.position();
        return MCVec3Impl.fromReal(p);
    }

    @Override
    public MCVec3 getDeltaMovement() {
        return MCVec3Impl.fromReal(inner.getDeltaMovement());
    }

    @Override
    public void setDeltaMovement(MCVec3 v) {
        inner.setDeltaMovement(new net.minecraft.world.phys.Vec3(v.x(), v.y(), v.z()));
    }

    @Override
    public MCAABB getBoundingBox() {
        var bb = inner.getBoundingBox();
        return MCAABBImpl.fromReal(bb);
    }

    @Override
    public int getId() {
        return inner.getId();
    }

    @Override
    public UUID getUUID() {
        return inner.getUUID();
    }

    @Override
    public MCBlockPos getBlockPosition() {
        return MCBlockPosImpl.fromReal(inner.blockPosition());
    }

    @Override
    public MCLevel getLevel() {
        return new MCLevelImpl(inner.level());
    }

    @Override
    public void setYRot(float yRot) {
        inner.setYRot(yRot);
    }

    @Override
    public void setXRot(float xRot) {
        inner.setXRot(xRot);
    }

    @Override
    public float getYRot() {
        return inner.getYRot();
    }

    @Override
    public float getXRot() {
        return inner.getXRot();
    }

    @Override
    public boolean isAlive() {
        return inner.isAlive();
    }
}
