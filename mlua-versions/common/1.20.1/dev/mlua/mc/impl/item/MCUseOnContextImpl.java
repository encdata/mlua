package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.core.MCDirection;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.item.MCUseOnContext;
import dev.mlua.mcapi.world.MCInteractionHand;
import dev.mlua.mcapi.world.MCLevel;
import dev.mlua.mcapi.world.phys.MCBlockHitResult;
import dev.mlua.mc.impl.core.MCBlockPosImpl;
import dev.mlua.mc.impl.core.MCDirectionImpl;
import dev.mlua.mc.impl.core.MCVec3Impl;
import dev.mlua.mc.impl.world.MCInteractionHandImpl;
import dev.mlua.mc.impl.world.MCLevelImpl;
import dev.mlua.mc.impl.world.phys.MCBlockHitResultImpl;
import net.minecraft.world.item.context.UseOnContext;

public class MCUseOnContextImpl implements MCUseOnContext {
    private final UseOnContext inner;

    public MCUseOnContextImpl(UseOnContext inner) {
        this.inner = inner;
    }

    @Override
    public MCLevel getLevel() {
        return new MCLevelImpl(inner.getLevel());
    }

    @Override
    public MCPlayer getPlayer() {
        var player = inner.getPlayer();
        return player == null ? null : dev.mlua.mc.impl.entity.MCPlayerImpl.fromReal(player);
    }

    @Override
    public MCInteractionHand getHand() {
        return MCInteractionHandImpl.fromReal(inner.getHand());
    }

    @Override
    public MCBlockHitResult getHitResult() {
        return new MCBlockHitResultImpl(new net.minecraft.world.phys.BlockHitResult(
                inner.getClickLocation(),
                inner.getClickedFace(),
                inner.getClickedPos(),
                false));
    }

    @Override
    public MCBlockPos getClickedPos() {
        return MCBlockPosImpl.fromReal(inner.getClickedPos());
    }

    @Override
    public MCDirection getClickedFace() {
        return MCDirectionImpl.fromReal(inner.getClickedFace());
    }

    @Override
    public MCVec3 getClickLocation() {
        return MCVec3Impl.fromReal(inner.getClickLocation());
    }

    @Override
    public MCItemStack getItemInHand() {
        return MCItemStackImpl.fromReal(inner.getItemInHand());
    }
}
