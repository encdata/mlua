package dev.mlua.runtime.registry;

import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class MLuaNamedBlock extends MLuaRenderableBlock {
   private final String displayName;

   public MLuaNamedBlock(BlockBehaviour.Properties properties, String registryName, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      super(properties, blockEntityType);
      this.displayName = MLuaRegistryNameHelper.displayName(registryName);
   }

   public MutableComponent getName() {
      return Component.literal(this.displayName);
   }
}
