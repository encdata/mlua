package dev.mlua.api;

import dev.mlua.core.service.MLuaService;
import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.module.MLuaModule;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Map;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

@MLuaApiStatus(since = "1.0.0")
public interface MLuaApiBridge {
   void registerScriptRoot(String modId, Path path);

   void registerModule(MLuaModule module);

   void registerService(String name, Function<MLuaService.Context, MLuaService> factory);

   default void registerService(Side side, String className, MLuaModule.ServiceEntry service) {
      Side selectedSide = java.util.Objects.requireNonNull(side, "side");
      MLuaModule.ServiceEntry declaration = java.util.Objects.requireNonNull(service, "service");
      String selectedClassName = className == null ? declaration.className() : className;
      registerService(declaration.name(), selectedClassName, selectedSide, declaration.factory(),
         declaration.description(), declaration.functionDescriptions());
   }

   default void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory) {
      if (side == Side.BOTH) {
         registerService(name, factory);
         return;
      }
      registerService(name, name, side, factory, null, Map.of());
   }

   default void registerService(String name, Function<MLuaService.Context, MLuaService> factory,
                                String description, Map<String, String> functionDescriptions) {
      if (description == null && functionDescriptions != null && functionDescriptions.isEmpty()) {
         registerService(name, factory);
         return;
      }
      registerService(Side.BOTH, null, new MLuaModule.ServiceEntry(
         name, factory, description, functionDescriptions));
   }

   default void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory,
                                String description, Map<String, String> functionDescriptions) {
      registerService(side, null, new MLuaModule.ServiceEntry(
         name, factory, description, functionDescriptions));
   }

   default void registerService(String name, String className,
                                Function<MLuaService.Context, MLuaService> factory,
                                String description, Map<String, String> functionDescriptions) {
      registerService(Side.BOTH, className, new MLuaModule.ServiceEntry(
         name, className, factory, description, functionDescriptions));
   }

   default void registerService(String name, String className, Side side,
                                Function<MLuaService.Context, MLuaService> factory,
                                String description, Map<String, String> functionDescriptions) {
      registerService(side, className, new MLuaModule.ServiceEntry(
         name, className, factory, description, functionDescriptions));
   }

   RegistryHandle<Item> registerItem(String modId, String name, Supplier<Item> supplier);

   RegistryHandle<Block> registerBlock(String modId, String name, Supplier<Block> supplier);

   RegistryHandle<Item> registerBlockItem(String modId, String blockName);

   RegistryHandle<CreativeModeTab> registerCreativeTab(String modId, String name, Function<CreativeModeTab.Builder, CreativeModeTab.Builder> config);

   Item getItem(String modId, String name);

   Block getBlock(String modId, String name);

   BlockBehaviour.Properties blockPropertiesCopy(Block block);
}
