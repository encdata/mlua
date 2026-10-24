package dev.mlua.modules.registry.service;

import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import net.minecraft.world.level.block.Block;
import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.binding.MLuaRegistryProperties;
import dev.mlua.minecraft.scripted.MLuaScriptedBlockItem;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;

/** Registers block items after their backing blocks have been registered. */
final class MLuaRegistryBlockItemApi {
   private MLuaRegistryBlockItemApi() { }

   static void install(MLuaRegistryService service, LuaTable blocks) {
      blocks.set("RegisterItem", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = service.apiContext().mlua().ownerModId();
            MLuaRegistryContentValidation.requireLocalRegistryName(owner, name, "Registry.Blocks:RegisterItem");
            LuaTable propsTable = MLuaRegistryProperties.table(service.apiContext().mlua().registration(),
               args.arg(3), "ItemProperties", "Registry.Blocks:RegisterItem");
            List<String> description = MLuaRegistryDescriptions.read(propsTable);
            Map<String, Object> props = MLuaRegistryLuaValues.registryPropertiesMap(propsTable,
               "onUse", "onUseOnBlock", "onInventoryTick", "onCrafted", "onConsume", "onAttackEntity");
            try {
               service.itemFactory.validateItemProperties(props);
            } catch (IllegalArgumentException invalid) {
               throw service.registryValidationError(invalid);
            }
            String key = owner + ":" + name;
            dev.mlua.module.MLuaRegistration registration = service.apiContext().mlua().registration();
            String className = MLuaRegistryContentValidation.requireContentClass(registration, propsTable, "BlockItem");
            Map<String, LuaValue> itemCallbacks = MLuaRegistryLuaValues.collectCallbacks(propsTable,
               "onUse", "onUseOnBlock", "onInventoryTick", "onCrafted", "onConsume", "onAttackEntity");
            boolean hasCallbacks = !itemCallbacks.isEmpty();

            RegistryHandle<Block> blockRO = MLuaLoaderRegistry.get(RegistryKind.BLOCK, owner, name);
            if (blockRO == null) {
               throw new LuaError("[MLua] Block '" + key + "' not found. Register the block before its block item.");
            }

            if (hasCallbacks) {
               MLuaLoaderRegistry.register(RegistryKind.ITEM, owner, name, () -> {
                  return new MLuaScriptedBlockItem((Block)blockRO.get(), service.itemFactory.makeRegisteredProperties(props, key), key);
               });
            } else {
               MLuaLoaderRegistry.register(RegistryKind.ITEM, owner, name, () -> {
                  return new dev.mlua.runtime.registry.MLuaNamedBlockItem((Block)blockRO.get(),
                     service.itemFactory.makeRegisteredProperties(props, key), key);
               });
            }

            service.updateVisualMetadata(RegistryKind.ITEM, owner, name, previous ->
               new MLuaRegistryVisualOperations.Metadata(previous.copy(), previous.copyModel(),
                  description, previous.texture(), previous.model()));
            service.replaceBehaviors("item", key, itemCallbacks);
            service.registerContentClass("item", key, className);

            return service.registryEntry(RegistryKind.BLOCK_ITEM, owner, name);
         }
      });
   }
}
