package dev.mlua.mc.impl.chunk;

import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.chunk.MCChunk;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.world.MCLevel;
import dev.mlua.mc.impl.block.MCBlockStateImpl;
import dev.mlua.mc.impl.world.MCLevelImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

public class MCChunkImpl implements MCChunk {
    private final LevelChunk inner;
    public MCChunkImpl(LevelChunk inner) { this.inner = inner; }
    public static MCChunkImpl fromReal(LevelChunk inner) { return inner == null ? null : new MCChunkImpl(inner); }
    public LevelChunk inner() { return inner; }
    @Override public MCBlockState getBlockState(int x, int y, int z) { return MCBlockStateImpl.fromReal(inner.getBlockState(new BlockPos(x, y, z))); }
    @Override public MCBlockState getBlockState(MCBlockPos pos) { return MCBlockStateImpl.fromReal(inner.getBlockState(new BlockPos(pos.getX(), pos.getY(), pos.getZ()))); }
    @Override public int getX() { return inner.getPos().x(); }
    @Override public int getZ() { return inner.getPos().z(); }
    @Override public MCLevel getLevel() { Level level = inner.getLevel(); return level == null ? null : new MCLevelImpl(level); }
    @Override public Object getBiome(int x, int y, int z) { return inner.getNoiseBiome(x >> 2, y >> 2, z >> 2); }
    @Override public void setBlockState(MCBlockPos pos, MCBlockState state, boolean applyPhysics) { inner.setBlockState(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), ((MCBlockStateImpl) state).inner(), applyPhysics ? 3 : 0); }
}
