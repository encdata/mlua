package dev.mlua.api;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaModuleLoader;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class RuntimeMLuaApiBridge implements MLuaApiBridge {
   @Override
   public void registerScriptRoot(String modId, Path path) {
      MLuaRuntime.get().registerScriptRoot(modId, path);
   }

   @Override
   public void registerModule(MLuaModule module) {
      MLuaModuleLoader.get().register(module);
   }

   @Override
   public void registerService(Side side, String className, MLuaModule.ServiceEntry service) {
      MLuaRuntime.get().registerService(service.name(), className, side,
         service.factory(), service.description(), service.functionDescriptions());
   }

   @Override
   public RegistryHandle<Item> registerItem(String modId, String name, Supplier<Item> supplier) {
      return LoaderApi.registry().register(RegistryKind.ITEM, modId, name, supplier);
   }

   @Override
   public RegistryHandle<Block> registerBlock(String modId, String name, Supplier<Block> supplier) {
      return LoaderApi.registry().register(RegistryKind.BLOCK, modId, name, supplier);
   }

   @Override
   public RegistryHandle<Item> registerBlockItem(String modId, String blockName) {
      return LoaderApi.registry().register(RegistryKind.BLOCK_ITEM, modId, blockName, null);
   }

   @Override
   public RegistryHandle<CreativeModeTab> registerCreativeTab(String modId, String name, Function<CreativeModeTab.Builder, CreativeModeTab.Builder> config) {
      Objects.requireNonNull(config, "config");
      return LoaderApi.registry().register(RegistryKind.CREATIVE_TAB, modId, name, () -> {
         CreativeModeTab.Builder builder = this.createCreativeTabBuilder()
            .title(Component.translatable("itemGroup." + modId + "." + name));
         return Objects.requireNonNull(config.apply(builder), "creative tab config returned null").build();
      });
   }

   @Override
   public Item getItem(String modId, String name) {
      RegistryHandle<Item> handle = LoaderApi.registry().get(RegistryKind.ITEM, modId, name);
      return handle == null ? null : handle.get();
   }

   @Override
   public Block getBlock(String modId, String name) {
      RegistryHandle<Block> handle = LoaderApi.registry().get(RegistryKind.BLOCK, modId, name);
      return handle == null ? null : handle.get();
   }

   @Override
   public BlockBehaviour.Properties blockPropertiesCopy(Block block) {
      return BlockBehaviour.Properties.ofFullCopy(block);
   }

   private CreativeModeTab.Builder createCreativeTabBuilder() {
      try {
         return (CreativeModeTab.Builder) CreativeModeTab.class.getMethod("builder").invoke(null);
      } catch (ReflectiveOperationException ignored) {
      }

      try {
         Class<?> rowClass = Class.forName("net.minecraft.world.item.CreativeModeTab$Row");
         Object topRow = rowClass.getField("TOP").get(null);
         return (CreativeModeTab.Builder) CreativeModeTab.class.getMethod("builder", rowClass, int.class).invoke(null, topRow, 0);
      } catch (ReflectiveOperationException exception) {
         throw new IllegalStateException("Unable to create CreativeModeTab builder", exception);
      }
   }
}
