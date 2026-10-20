package dev.mlua.mcapi.block.entity;

import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.nbt.MCCompoundTag;

public interface MCBlockEntity {
    MCBlockPos getBlockPos();
    MCBlockState getBlockState();
    MCCompoundTag saveWithFullMetadata();
    void load(MCCompoundTag tag);
    void setChanged();
}
