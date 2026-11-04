package dev.mlua.runtime.registry;

import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class MLuaRenderableBlock extends Block implements EntityBlock {
   private final Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType;

   public MLuaRenderableBlock(BlockBehaviour.Properties properties, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      super(properties);
      this.blockEntityType = blockEntityType;
   }

   @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      BlockEntityType<MLuaVisualBlockEntity> type = this.blockEntityType.get();
      return type == null ? null : new MLuaVisualBlockEntity(type, pos, state);
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   public MutableComponent getName() {
      return Component.literal("MLua Block");
   }
}
