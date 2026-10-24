package dev.mlua.modules.registry.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.mlua.api.MLuaLuaErrors;
import com.google.gson.JsonObject;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.PushReaction;

import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.binding.MLuaContainerType;
import dev.mlua.minecraft.binding.MLuaRegistryProperties;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.minecraft.scripted.MLuaScriptedBlockItem;
import dev.mlua.runtime.registry.MLuaBlockFactory;
import dev.mlua.runtime.registry.MLuaItemFactory;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;

/** Builds the item and block registration endpoints as one content-focused API unit. */
final class MLuaRegistryItemBlockApi {
   private MLuaRegistryItemBlockApi() { }

   static void install(MLuaRegistryService service, LuaTable items, LuaTable blocks) {
      items.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = service.apiContext().mlua().ownerModId();
            MLuaRegistryContentValidation.requireLocalRegistryName(owner, name, "Registry.Items:Register");
            LuaTable propsTable = MLuaRegistryProperties.table(service.apiContext().mlua().registration(),
               args.arg(3), "ItemProperties", "Registry.Items:Register");
            List<String> description = MLuaRegistryDescriptions.read(propsTable);
            Map<String, Object> props = MLuaRegistryLuaValues.registryPropertiesMap(propsTable,
               "onUse", "onUseOnBlock", "onInventoryTick", "onCrafted", "onConsume", "onAttackEntity");
            try {
               service.itemFactory.validateTypeDefinition(props);
            } catch (IllegalArgumentException invalid) {
               throw service.registryValidationError(invalid);
            }
            String key = owner + ":" + name;
            dev.mlua.module.MLuaRegistration registration = service.apiContext().mlua().registration();
            String className = MLuaRegistryContentValidation.requireContentClass(registration, propsTable, "Item");
            Map<String, LuaValue> itemCallbacks = MLuaRegistryLuaValues.collectCallbacks(propsTable,
               "onUse", "onUseOnBlock", "onInventoryTick", "onCrafted", "onConsume", "onAttackEntity");
            boolean hasCallbacks = !itemCallbacks.isEmpty();
            boolean copyModel = MLuaRegistryPropertyReaders.booleanProperty(propsTable, "copyModel", "CopyModel");
            MLuaTextureDescriptor itemTexture = MLuaRegistryVisualDefinitions.textureDescriptor(propsTable, "itemTexture", "texture", "textures", "ItemTexture", "Texture", "Textures").resolve(owner, "item");
            String itemModel = MLuaRegistryVisualDefinitions.resolvedModel(owner, "item", propsTable, "model", "Model", "itemModel", "ItemModel");
            if (hasCallbacks) {
               MLuaLoaderRegistry.register(RegistryKind.ITEM, owner, name, () -> {
                  return service.itemFactory.createScripted(props, key);
               });
            } else {
               MLuaLoaderRegistry.register(RegistryKind.ITEM, owner, name, () -> {
                  return service.itemFactory.createNamed(props, key);
               });
            }

            service.updateVisualMetadata(RegistryKind.ITEM, owner, name, previous ->
               new MLuaRegistryVisualOperations.Metadata(propsTable != null ? propsTable.get("copy").optjstring((String)null) : null,
                  copyModel, description, itemTexture, itemModel));
            service.replaceBehaviors("item", key, itemCallbacks);
            service.registerContentClass("item", key, "Item".equals(className) ? null : className);
            return service.registryEntry(RegistryKind.ITEM, owner, name);
         }
      });
      items.set("GetTiers", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return service.stringArray(MLuaItemFactory.tierNames());
         }
      });
      items.set("Properties", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryProperties.create(service.apiContext().mlua().registration(), "ItemProperties");
         }
      });
      items.set("GetRarities", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return service.stringArray(MLuaItemFactory.rarityNames());
         }
      });
      blocks.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = service.apiContext().mlua().ownerModId();
            MLuaRegistryContentValidation.requireLocalRegistryName(owner, name, "Registry.Blocks:Register");
            LuaTable propsTable = MLuaRegistryProperties.table(service.apiContext().mlua().registration(),
               args.arg(3), "BlockProperties", "Registry.Blocks:Register");
            List<String> description = MLuaRegistryDescriptions.read(propsTable);
            Map<String, Object> props = new HashMap(MLuaRegistryLuaValues.registryPropertiesMap(propsTable,
               "onRightClick", "onPlace", "onBreak", "onStepOn", "onEntityInside",
               "onNeighborChanged", "onRemove", "onRandomTick", "onTick", "onFallOn", "onProjectileHit"));
            try {
               MLuaBlockFactory.validateTypeDefinition(props);
            } catch (IllegalArgumentException invalid) {
               throw service.registryValidationError(invalid);
            }
            String key = owner + ":" + name;
            dev.mlua.module.MLuaRegistration registration = service.apiContext().mlua().registration();
            String className = MLuaRegistryContentValidation.requireContentClass(registration, propsTable, "Block");
            LuaValue blockEntityDefinition = propsTable == null ? LuaValue.NIL : propsTable.get("blockEntity");
            if (blockEntityDefinition.isnil() && propsTable != null) blockEntityDefinition = propsTable.get("BlockEntity");
            LuaTable blockEntityProperties = blockEntityDefinition.istable() ? blockEntityDefinition.checktable() : null;
            String blockEntityClass = blockEntityProperties != null
               ? MLuaRegistryContentValidation.requireContentClass(registration, blockEntityProperties, "BlockEntity") : "BlockEntity";
            if (propsTable != null && !propsTable.get("blockEntityClass").isnil()) {
               blockEntityClass = propsTable.get("blockEntityClass").checkjstring();
               if (registration.classTable(blockEntityClass) == null || !registration.isSubclassOf(blockEntityClass, "BlockEntity")) {
                  throw new LuaError("[MLua] Content class '" + blockEntityClass + "' must be registered and inherit from BlockEntity");
               }
            }
            Map<String, LuaValue> blockEntityCallbacks = MLuaRegistryLuaValues.collectCallbacks(blockEntityProperties,
               "onLoad", "onUnload", "onOpen", "onClose", "onContainerChanged");
            Map<String, LuaValue> blockCallbacks = MLuaRegistryLuaValues.collectCallbacks(propsTable,
               "onRightClick", "onPlace", "onBreak", "onStepOn", "onEntityInside",
               "onNeighborChanged", "onRemove", "onRandomTick", "onTick", "onFallOn", "onProjectileHit");
            MLuaBehaviorRegistry.ContainerDefinition requestedContainer = null;
            if (propsTable != null) {
               LuaValue containerValue = propsTable.get("container");
               if (containerValue.isnil()) containerValue = propsTable.get("Container");
               if (!containerValue.isnil()) {
                  if (MLuaContainerType.isInstance(containerValue)) {
                     String typeId = MLuaContainerType.id(containerValue);
                     requestedContainer = MLuaBehaviorRegistry.getContainerType(typeId);
                     if (requestedContainer == null) {
                        throw new LuaError("[MLua] Registry.Blocks:Register references an unregistered ContainerType '" + typeId + "'.");
                     }
                  } else if (containerValue.istable() && registration.isInstanceOf(containerValue, "ContainerType")) {
                     throw new LuaError("[MLua] Registry.Blocks:Register received an invalid ContainerType instance.");
                  } else if (containerValue.istable()) {
                     LuaTable containerTable = containerValue.checktable();
                     int rows = MLuaLuaArguments.optionalWholeInt(MLuaRegistryContainerDefinitions.property(containerTable, "rows", "Rows"), 3,
                        "Registry.Blocks:Register", "container.Rows");
                     LuaValue titleValue = MLuaRegistryContainerDefinitions.property(containerTable, "title", "Title");
                     if (!titleValue.isnil() && titleValue.type() != LuaValue.TSTRING) {
                        throw new LuaError("[MLua] Registry.Blocks:Register container.title must be a string");
                     }
                     String title = titleValue.optjstring(MLuaRegistryNameHelper.displayName(name));
                     try {
                        requestedContainer = MLuaBehaviorRegistry.validateContainerDefinition(
                           new MLuaBehaviorRegistry.ContainerDefinition(rows, title,
                              MLuaRegistryContainerDefinitions.allowedItems(owner, containerTable),
                              MLuaRegistryContainerDefinitions.slotAllowedItems(owner, containerTable, rows * 9)));
                     } catch (IllegalArgumentException invalid) {
                        throw MLuaLuaErrors.javaFailure("Block container definition", invalid);
                     }
                  } else if (containerValue.type() == LuaValue.TSTRING) {
                     String typeName = containerValue.checkjstring();
                     String typeKey = typeName.contains(":") ? typeName : owner + ":" + typeName;
                     requestedContainer = MLuaBehaviorRegistry.getContainerType(typeKey);
                     if (requestedContainer == null) throw new LuaError("[MLua] Block Container references unregistered container type '" + typeName + "'");
                  } else {
                     throw new LuaError("[MLua] Block Container must be a registered ContainerType, its id, or an inline definition table.");
                  }
               }
            }
            boolean copyModel = MLuaRegistryPropertyReaders.booleanProperty(propsTable, "copyModel", "CopyModel");
            MLuaTextureDescriptor texture = MLuaRegistryVisualDefinitions.textureDescriptor(propsTable, "textures", "texture", "Textures", "Texture", "blockTexture", "BlockTexture").resolve(owner, "block");
            String model = MLuaRegistryVisualDefinitions.resolvedModel(owner, "block", propsTable, "model", "Model", "blockModel", "BlockModel");
            JsonObject blockState = MLuaRegistryPropertyReaders.optionalJsonObject(propsTable, "blockState", "blockstate", "BlockState");
            MLuaRegistryMiningProperties mining = MLuaRegistryMiningProperties.read(propsTable, props);
            if (blockState != null) {
               service.registerGeneratedAsset(GeneratedAssetKind.BLOCKSTATE, owner, name, blockState);
            }
            if (propsTable != null && !propsTable.get("onRandomTick").isnil()) {
               ((Map)props).put("randomTicks", true);
            }
            boolean hasBlockEntityBehavior = blockEntityProperties != null || !"BlockEntity".equals(blockEntityClass);
            boolean scripted = requestedContainer != null || hasBlockEntityBehavior || !blockCallbacks.isEmpty();
            MLuaLoaderRegistry.register(RegistryKind.BLOCK, owner, name, () -> {
               return scripted
                  ? service.blockFactory.createScripted(
                     props, key, MLuaLoaderRegistry.visualBlockEntitySupplier(owner))
                  : service.blockFactory.create(
                     props, key, MLuaLoaderRegistry.visualBlockEntitySupplier(owner));
            });
            service.updateVisualMetadata(RegistryKind.BLOCK, owner, name, previous ->
               new MLuaRegistryVisualOperations.Metadata(propsTable != null ? propsTable.get("copy").optjstring((String)null) : null,
                  copyModel, description, texture, model));
            service.registerMiningProperties(owner, name, mining);
            service.replaceBlockContainer(registration, key, requestedContainer);
            service.replaceBehaviors("block_entity", key, blockEntityCallbacks);
            service.replaceBehaviors("block", key, blockCallbacks);
            service.registerContentClass("block_entity", key,
               "BlockEntity".equals(blockEntityClass) ? null : blockEntityClass);
            service.registerContentClass("block", key, "Block".equals(className) ? null : className);
            return service.registryEntry(RegistryKind.BLOCK, owner, name);
         }
      });
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
      blocks.set("Properties", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryProperties.create(service.apiContext().mlua().registration(), "BlockProperties");
         }
      });
      blocks.set("GetPushReactions", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return service.enumNames(PushReaction.values());
         }
      });
      blocks.set("GetInstruments", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return service.enumNames(NoteBlockInstrument.values());
         }
      });
      blocks.set("GetSounds", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return service.stringArray(MLuaBlockFactory.soundNames());
         }
      });
      blocks.set("GetMapColors", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return service.stringArray(MLuaBlockFactory.mapColorNames());
         }
      });
   }
}
