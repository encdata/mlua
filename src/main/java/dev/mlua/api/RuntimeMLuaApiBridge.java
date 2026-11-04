package dev.mlua.api;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaModuleLoader;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaBlockFactory;
import dev.mlua.runtime.registry.MLuaItemFactory;
import dev.mlua.modules.events.service.MLuaEventsService;
import dev.mlua.api.registry.MLuaBlockTypeProvider;
import dev.mlua.api.registry.MLuaItemTypeProvider;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.luaj.vm2.Varargs;

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
   public MLuaApi.Registration registerModuleScoped(MLuaModule module) {
      AutoCloseable registration = MLuaModuleLoader.get().registerScoped(module);
      return () -> {
         try {
            registration.close();
         } catch (RuntimeException failure) {
            throw failure;
         } catch (Exception failure) {
            throw new IllegalStateException("Failed to close MLua module registration '" + module.id() + "'", failure);
         }
      };
   }

   @Override
   public void registerService(Side side, String className, MLuaModule.ServiceEntry service) {
      MLuaRuntime.get().registerService(service.name(), className, side,
         service.factory(), service.description(), service.functionDescriptions());
   }

   @Override
   public RegistryHandle<Item> registerItem(String modId, String name, Supplier<Item> supplier) {
      return MLuaLoaderRegistry.register(RegistryKind.ITEM, modId, name, supplier);
   }

   @Override
   public RegistryHandle<Block> registerBlock(String modId, String name, Supplier<Block> supplier) {
      return MLuaLoaderRegistry.register(RegistryKind.BLOCK, modId, name, supplier);
   }

   @Override
   public RegistryHandle<Item> registerBlockItem(String modId, String blockName) {
      return MLuaLoaderRegistry.register(RegistryKind.BLOCK_ITEM, modId, blockName, null);
   }

   @Override
   public RegistryHandle<CreativeModeTab> registerCreativeTab(String modId, String name, Function<CreativeModeTab.Builder, CreativeModeTab.Builder> config) {
      Objects.requireNonNull(config, "config");
      return MLuaLoaderRegistry.register(RegistryKind.CREATIVE_TAB, modId, name, () -> {
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
      return (BlockBehaviour.Properties)MCApiRuntime.copyBlockProperties(block);
   }

   @Override
   public MLuaApi.Registration registerItemType(String owner, String name, String description,
                                                  MLuaItemTypeProvider provider) {
      Objects.requireNonNull(provider, "provider");
      MLuaItemFactory.TypeProvider adapter = new MLuaItemFactory.TypeProvider() {
         @Override public void validateDefinition(java.util.Map<String, Object> definition) {
            provider.validateDefinition(definition);
         }
         @Override public Item createNamed(java.util.Map<String, Object> definition, String registryName) {
            return provider.createNamed(definition, new Item.Properties(), registryName);
         }
         @Override public Item createScripted(java.util.Map<String, Object> definition, String behaviorKey) {
            return provider.createScripted(definition, new Item.Properties(), behaviorKey);
         }
         @Override public Item createNamed(java.util.Map<String, Object> definition, Item.Properties properties,
                                           String registryName) {
            return provider.createNamed(definition, properties, registryName);
         }
         @Override public Item createScripted(java.util.Map<String, Object> definition, Item.Properties properties,
                                              String behaviorKey) {
            return provider.createScripted(definition, properties, behaviorKey);
         }
      };
      return MLuaItemFactory.registerTypeHandle(owner, name, description, adapter)::close;
   }

   @Override
   public MLuaApi.Registration registerBlockType(String owner, String name, String description,
                                                   MLuaBlockTypeProvider provider) {
      Objects.requireNonNull(provider, "provider");
      MLuaBlockFactory.TypeProvider adapter = new MLuaBlockFactory.TypeProvider() {
         @Override public void validateDefinition(java.util.Map<String, Object> definition) {
            provider.validateDefinition(definition);
         }
         @Override public Block createNamed(java.util.Map<String, Object> definition,
               BlockBehaviour.Properties properties, String registryName,
               java.util.function.Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
            return provider.createNamed(definition, properties, registryName, blockEntityType);
         }
         @Override public Block createScripted(java.util.Map<String, Object> definition,
               BlockBehaviour.Properties properties, String behaviorKey,
               java.util.function.Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
            return provider.createScripted(definition, properties, behaviorKey, blockEntityType);
         }
      };
      return MLuaBlockFactory.registerTypeHandle(owner, name, description, adapter)::close;
   }

   @Override
   public MLuaApi.EventSignal registerEventSignal(String owner, String name, String description) {
      MLuaEventsService.AddonSignal signal = MLuaEventsService.registerAddonSignal(owner, name, description);
      return new MLuaApi.EventSignal() {
         @Override public String name() { return signal.name(); }
         @Override public void fire(Varargs payload) { signal.fire(payload); }
         @Override public void close() { signal.close(); }
      };
   }

   private CreativeModeTab.Builder createCreativeTabBuilder() {
      return (CreativeModeTab.Builder)MCApiRuntime.createCreativeTabBuilder();
   }
}
