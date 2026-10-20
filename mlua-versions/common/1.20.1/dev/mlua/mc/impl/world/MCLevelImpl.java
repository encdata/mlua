package dev.mlua.mc.impl.world;

import dev.mlua.mcapi.biome.MCBiome;
import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.block.entity.MCBlockEntity;
import dev.mlua.mcapi.core.MCAABB;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.world.MCDifficulty;
import dev.mlua.mcapi.world.MCLevel;
import dev.mlua.mcapi.world.phys.MCClipContext;
import dev.mlua.mcapi.world.phys.MCHitResult;
import dev.mlua.mc.impl.biome.MCBiomeImpl;
import dev.mlua.mc.impl.block.MCBlockStateImpl;
import dev.mlua.mc.impl.block.entity.MCBlockEntityImpl;
import dev.mlua.mc.impl.core.MCBlockPosImpl;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mc.impl.world.phys.MCClipContextImpl;
import dev.mlua.mc.impl.world.phys.MCHitResultImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

public class MCLevelImpl implements MCLevel {
    private final Level inner;

    public MCLevelImpl(Level inner) {
        this.inner = inner;
    }

    public Level inner() {
        return inner;
    }

    @Override
    public MCBlockState getBlockState(MCBlockPos pos) {
        BlockPos bp = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        return MCBlockStateImpl.fromReal(inner.getBlockState(bp));
    }

    @Override
    public boolean setBlock(MCBlockPos pos, MCBlockState state, int flags) {
        BlockPos bp = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        return inner.setBlock(bp, ((MCBlockStateImpl) state).inner(), flags);
    }

    @Override
    public boolean destroyBlock(MCBlockPos pos, boolean drop) {
        BlockPos bp = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        return inner.destroyBlock(bp, drop);
    }

    @Override
    public MCBlockEntity getBlockEntity(MCBlockPos pos) {
        BlockPos bp = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        var be = inner.getBlockEntity(bp);
        return be == null ? null : MCBlockEntityImpl.fromReal(be);
    }

    @Override
    public List<MCEntity> getEntities(@Nullable MCEntity except, MCAABB area) {
        net.minecraft.world.entity.Entity ex = except == null ? null : ((MCEntityImpl) except).inner();
        AABB aabb = new AABB(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ());
        List<MCEntity> result = new ArrayList<>();
        for (var entity : inner.getEntities(ex, aabb)) {
            result.add(new MCEntityImpl(entity));
        }
        return result;
    }

    @Override
    public <T extends MCEntity> List<T> getEntitiesOfClass(Class<T> clazz, MCAABB area, @Nullable Predicate<? super T> filter) {
        return List.of();
    }

    @Override
    public MCBiome getBiome(MCBlockPos pos) {
        BlockPos bp = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        var biome = inner.getBiome(bp);
        return MCBiomeImpl.fromReal(biome.value());
    }

    @Override
    public MCDifficulty getDifficulty() {
        return MCDifficultyImpl.fromReal(inner.getDifficulty());
    }

    @Override
    public Object getDimension() {
        return inner.dimension();
    }

    @Override
    public void sendBlockUpdated(MCBlockPos pos, MCBlockState oldState, MCBlockState newState, int flags) {
        BlockPos bp = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        inner.sendBlockUpdated(bp, ((MCBlockStateImpl) oldState).inner(), ((MCBlockStateImpl) newState).inner(), flags);
    }

    @Override
    public MCHitResult clip(MCClipContext context) {
        var ctx = ((MCClipContextImpl) context).inner();
        HitResult hit = inner.clip(ctx);
        return MCHitResultImpl.fromReal(hit);
    }

    @Override
    public long getDayTime() {
        return inner.getDayTime();
    }

    @Override
    public long getGameTime() {
        return inner.getGameTime();
    }

    @Override
    public boolean isClientSide() {
        return inner.isClientSide();
    }

    @Override
    public boolean isServerSide() {
        return !inner.isClientSide();
    }
}
