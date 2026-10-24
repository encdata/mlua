package dev.mlua.modules.registry.service;

import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.binding.MLuaRegistryProperties;
import dev.mlua.runtime.registry.MLuaItemFactory;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;

/** Registers item content with Minecraft. */
final class MLuaRegistryItemApi {
   private MLuaRegistryItemApi() { }

   static void install(MLuaRegistryService service, LuaTable items) {
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
   }
}
