package dev.mlua.mc.impl.world.phys;

import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.core.MCDirection;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.world.phys.MCBlockHitResult;
import dev.mlua.mc.impl.core.MCBlockPosImpl;
import dev.mlua.mc.impl.core.MCDirectionImpl;
import dev.mlua.mc.impl.core.MCVec3Impl;
import net.minecraft.world.phys.BlockHitResult;

public class MCBlockHitResultImpl extends MCHitResultImpl implements MCBlockHitResult {
    private final BlockHitResult inner;

    public MCBlockHitResultImpl(BlockHitResult inner) {
        super(inner);
        this.inner = inner;
    }

    @Override
    public MCBlockPos getBlockPos() {
        return MCBlockPosImpl.fromReal(inner.getBlockPos());
    }

    @Override
    public MCDirection getDirection() {
        return MCDirectionImpl.fromReal(inner.getDirection());
    }

    @Override
    public MCVec3 getLocation() {
        return MCVec3Impl.fromReal(inner.getLocation());
    }
}
