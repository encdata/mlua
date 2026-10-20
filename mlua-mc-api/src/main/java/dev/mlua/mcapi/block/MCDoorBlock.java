package dev.mlua.mcapi.block;

import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.world.MCLevel;

public interface MCDoorBlock extends MCBlock {
    boolean isOpen(MCBlockState state);
    void setOpen(MCLevel level, MCBlockState state, MCBlockPos pos, boolean open);
}
