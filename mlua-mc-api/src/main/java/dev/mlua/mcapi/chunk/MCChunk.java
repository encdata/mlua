package dev.mlua.mcapi.chunk;

import dev.mlua.mcapi.biome.MCBiome;
import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.world.MCLevel;

public interface MCChunk {

    MCBlockState getBlockState(int x, int y, int z);

    MCBlockState getBlockState(MCBlockPos pos);

    int getX();

    int getZ();

    MCLevel getLevel();

    Object getBiome(int x, int y, int z);

    void setBlockState(MCBlockPos pos, MCBlockState state, boolean applyPhysics);

}
