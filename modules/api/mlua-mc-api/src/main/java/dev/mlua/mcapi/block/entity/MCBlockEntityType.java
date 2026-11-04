package dev.mlua.mcapi.block.entity;

import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mcapi.world.MCLevel;

public interface MCBlockEntityType {
    MCResourceLocation getRegistryName();
    <T extends MCBlockEntity> T create(MCBlockPos pos, MCBlockState state);
}
