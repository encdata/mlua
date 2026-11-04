package dev.mlua.runtime.neoforge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.BiFunction;

/** Adapts NeoForge's 26.1 constructor-based block entity type factory. */
final class MLuaNeoForgeBlockEntityTypeFactory {
    private MLuaNeoForgeBlockEntityTypeFactory() { }

    static <T extends BlockEntity> BlockEntityType<T> create(
            BiFunction<BlockPos, BlockState, T> factory, Block... validBlocks) {
        return new BlockEntityType<>(factory::apply, validBlocks);
    }
}
