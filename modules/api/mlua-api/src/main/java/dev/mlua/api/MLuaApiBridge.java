package dev.mlua.api;

import dev.mlua.core.service.MLuaService;
import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.api.registry.MLuaBlockTypeProvider;
import dev.mlua.api.registry.MLuaItemTypeProvider;
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

   /** Registers an addon module with an identity-bound handle for runtime cleanup. */
   default MLuaApi.Registration registerModuleScoped(MLuaModule module) {
      throw new UnsupportedOperationException("This MLua runtime does not support scoped module registrations");
   }

   @Deprecated(forRemoval = false)
   default void registerService(String name, Function<MLuaService.Context, MLuaService> factory) {
      throw new UnsupportedOperationException("Register services with a description and function metadata");
   }

   void registerService(Side side, String className, MLuaModule.ServiceEntry service);

   @Deprecated(forRemoval = false)
   default void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory) {
      throw new UnsupportedOperationException("Register services with a description and function metadata");
   }

   default void registerService(String name, Function<MLuaService.Context, MLuaService> factory,
                                String description, Map<String, String> functionDescriptions) {
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

   /** Registers an addon-owned implementation kind for Lua item definitions. */
   default MLuaApi.Registration registerItemType(String owner, String name, String description,
                                                  MLuaItemTypeProvider provider) {
      throw new UnsupportedOperationException("This MLua runtime does not expose addon item type registration");
   }

   /** Registers an addon-owned implementation kind for Lua block definitions. */
   default MLuaApi.Registration registerBlockType(String owner, String name, String description,
                                                   MLuaBlockTypeProvider provider) {
      throw new UnsupportedOperationException("This MLua runtime does not expose addon block type registration");
   }

   /** Registers a namespaced Lua event backed by an addon-owned Java signal. */
   default MLuaApi.EventSignal registerEventSignal(String owner, String name, String description) {
      throw new UnsupportedOperationException("This MLua runtime does not expose addon event signal registration");
   }
}
