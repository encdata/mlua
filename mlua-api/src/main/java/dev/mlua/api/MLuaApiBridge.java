package dev.mlua.api;

import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaDescribedService;
import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.module.MLuaModule;
import java.nio.file.Path;
import java.util.List;
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

   default void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory) {
      if (side == Side.BOTH) {
         registerService(name, factory);
         return;
      }
      registerService(name, name, side, factory, null, Map.of());
   }

   default void registerService(String name, Function<MLuaService.Context, MLuaService> factory,
                                String description, Map<String, String> functionDescriptions) {
      if (description == null && functionDescriptions.isEmpty()) {
         registerService(name, factory);
         return;
      }
      registerService(name, context -> new MLuaDescribedService(
         factory.apply(context), description, functionDescriptions));
   }

   default void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory,
                                String description, Map<String, String> functionDescriptions) {
      if (side == Side.BOTH) {
         registerService(name, factory, description, functionDescriptions);
         return;
      }
      registerService(name, name, side, factory, description, functionDescriptions);
   }

   /** Registers a described service and selects the Lua class name used for its service class. */
   default void registerService(String name, String className, Side side,
                                Function<MLuaService.Context, MLuaService> factory,
                                String description, Map<String, String> functionDescriptions) {
      Side selectedSide = java.util.Objects.requireNonNull(side, "side");
      Map<String, String> methodDescriptions = Map.copyOf(functionDescriptions);
      registerModule(new MLuaModule() {
         @Override public String id() {
            String key = name + "|" + className + "|" + selectedSide.name();
            return "mlua_api_service_" + java.util.UUID.nameUUIDFromBytes(
               key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "");
         }

         @Override public Side side() { return selectedSide; }

         @Override public List<MLuaModule.ServiceEntry> services() {
            return List.of(new MLuaModule.ServiceEntry(name, className, factory, description, methodDescriptions));
         }
      });
   }

   RegistryHandle<Item> registerItem(String modId, String name, Supplier<Item> supplier);

   RegistryHandle<Block> registerBlock(String modId, String name, Supplier<Block> supplier);

   RegistryHandle<Item> registerBlockItem(String modId, String blockName);

   RegistryHandle<CreativeModeTab> registerCreativeTab(String modId, String name, Function<CreativeModeTab.Builder, CreativeModeTab.Builder> config);

   Item getItem(String modId, String name);

   Block getBlock(String modId, String name);

   BlockBehaviour.Properties blockPropertiesCopy(Block block);
}
