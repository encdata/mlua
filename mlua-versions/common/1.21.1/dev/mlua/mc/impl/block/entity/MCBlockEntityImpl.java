package dev.mlua.mc.impl.block.entity;

import dev.mlua.mc.impl.block.MCBlockStateImpl;
import dev.mlua.mc.impl.core.MCBlockPosImpl;
import dev.mlua.mc.impl.nbt.MCCompoundTagImpl;
import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.block.entity.MCBlockEntity;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.nbt.MCCompoundTag;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MCBlockEntityImpl implements MCBlockEntity {
    private final BlockEntity inner;

    public MCBlockEntityImpl(BlockEntity inner) {
        this.inner = inner;
    }

    public static MCBlockEntityImpl fromReal(BlockEntity inner) {
        return inner == null ? null : new MCBlockEntityImpl(inner);
    }

    public BlockEntity inner() {
        return inner;
    }

    @Override
    public MCBlockPos getBlockPos() {
        return MCBlockPosImpl.fromReal(inner.getBlockPos());
    }

    @Override
    public MCBlockState getBlockState() {
        return MCBlockStateImpl.fromReal(inner.getBlockState());
    }

    @Override
    public MCCompoundTag saveWithFullMetadata() {
        RegistryAccess provider = inner.getLevel() != null ? inner.getLevel().registryAccess() : RegistryAccess.EMPTY;
        return MCCompoundTagImpl.fromReal(inner.saveWithFullMetadata(provider));
    }

    @Override
    public void load(MCCompoundTag tag) {
        RegistryAccess provider = inner.getLevel() != null ? inner.getLevel().registryAccess() : RegistryAccess.EMPTY;
        inner.loadWithComponents(((MCCompoundTagImpl) tag).inner(), provider);
    }

    @Override
    public void setChanged() {
        inner.setChanged();
    }
}
