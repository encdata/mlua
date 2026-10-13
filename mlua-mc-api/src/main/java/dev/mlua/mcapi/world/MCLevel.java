package dev.mlua.mcapi.world;

import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.mcapi.biome.MCBiome;
import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.block.entity.MCBlockEntity;
import dev.mlua.mcapi.core.MCAABB;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.world.phys.MCClipContext;
import dev.mlua.mcapi.world.phys.MCHitResult;

import java.util.List;
import java.util.function.Predicate;

public interface MCLevel {
    MCBlockState getBlockState(MCBlockPos pos);

    boolean setBlock(MCBlockPos pos, MCBlockState state, int flags);

    boolean destroyBlock(MCBlockPos pos, boolean drop);

    MCBlockEntity getBlockEntity(MCBlockPos pos);

    List<MCEntity> getEntities(MCEntity except, MCAABB area);

    <T extends MCEntity> List<T> getEntitiesOfClass(Class<T> clazz, MCAABB area, Predicate<? super T> filter);

    MCBiome getBiome(MCBlockPos pos);

    MCDifficulty getDifficulty();

    Object dimension();

    void sendBlockUpdated(MCBlockPos pos, MCBlockState oldState, MCBlockState newState, int flags);

    MCHitResult clip(MCClipContext context);

    long getDayTime();

    long getGameTime();

    boolean isClientSide();

    boolean isServerSide();

    static MCLevel create() {
        return MCApiRuntime.getCurrentLevel();
    }
}
