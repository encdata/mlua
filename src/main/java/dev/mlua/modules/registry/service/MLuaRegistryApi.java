package dev.mlua.modules.registry.service;

import java.util.List;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.runtime.registry.MLuaContentTypeRegistry;
import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;

/** Assembles Registry's Lua service and typed category tables. Registration behavior stays in MLuaRegistryService. */
final class MLuaRegistryApi {
   private MLuaRegistryApi() { }

   static LuaTable build(MLuaRegistryService service) {
      LuaTable table = new LuaTable();
      LuaTable items = service.registryTable("Items");
      LuaTable blocks = service.registryTable("Blocks");
      LuaTable entities = service.registryTable("Entities");
      LuaTable effects = service.registryTable("Effects");
      LuaTable potions = service.registryTable("Potions");
      LuaTable particles = service.registryTable("Particles");
      LuaTable recipes = service.registryTable("Recipes");
      LuaTable loot = service.registryTable("Loot");
      LuaTable tags = service.registryTable("Tags");
      LuaTable containers = service.registryTable("Containers");
      LuaTable projectiles = service.registryTable("Projectiles");
      LuaTable tabs = service.registryTable("Tabs");
      LuaTable config = service.registryTable("Config");
      LuaTable damageSource = service.registryTable("DamageSource");
      LuaTable dataPack = service.registryTable("DataPack");
      LuaTable assets = service.registryTable("Assets");
      LuaTable worldGeneration = service.registryTable("WorldGeneration");
      LuaTable contentTypes = service.registryTable("ContentTypes");
      table.set("__side", "Registry");
      table.set("GetScriptLimits", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaBehaviorRegistry.ScriptLimits active = MLuaBehaviorRegistry.scriptLimits(
               service.apiContext().mlua().ownerModId());
            dev.mlua.runtime.registry.MLuaContentTypeRegistry.Usage contentTypes =
               dev.mlua.runtime.registry.MLuaContentTypeRegistry.usage(
                  service.apiContext().mlua().registration(),
                  service.apiContext().mlua().ownerModId());
            MLuaDynamicRegistryCatalog.Usage dynamic = service.dynamicRegistry
                .usage(service.apiContext().mlua().ownerModId());
            dev.mlua.loaderapi.WorldGenerationBridge.FeatureCounts worldFeatures =
                dev.mlua.loaderapi.LoaderApi.worldGeneration().featureCounts(
                    service.apiContext().mlua().ownerModId());
            LuaTable limits = new LuaTable();
            limits.set("ActiveBehaviors", active.activeBehaviors());
            limits.set("AddonBehaviors", active.activeBehaviorsForAddon());
            limits.set("MaxBehaviors", MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS);
            limits.set("MaxBehaviorsPerAddon", MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS_PER_ADDON);
            limits.set("MaxCallbacksPerDefinition", MLuaBehaviorRegistry.MAX_CALLBACKS_PER_DEFINITION);
            limits.set("MaxBehaviorInstructionsPerCallback", MLuaBehaviorRegistry.MAX_LUA_INSTRUCTIONS_PER_CALLBACK);
            limits.set("ActiveClassMappings", active.activeClassMappings());
            limits.set("AddonClassMappings", active.activeClassMappingsForAddon());
            limits.set("MaxClassMappings", MLuaBehaviorRegistry.MAX_CONTENT_CLASS_MAPPINGS);
            limits.set("MaxClassMappingsPerAddon", MLuaBehaviorRegistry.MAX_CONTENT_CLASS_MAPPINGS_PER_ADDON);
            limits.set("ActiveBlockContainers", active.activeBlockContainers());
            limits.set("AddonBlockContainers", active.blockContainersForAddon());
            limits.set("MaxBlockContainers", MLuaBehaviorRegistry.MAX_BLOCK_CONTAINERS);
            limits.set("ActiveContainerTypes", active.activeContainerTypes());
            limits.set("AddonContainerTypes", active.containerTypesForAddon());
            limits.set("MaxContainerTypes", MLuaBehaviorRegistry.MAX_CONTAINER_TYPES);
            limits.set("MaxContainersPerAddon", MLuaBehaviorRegistry.MAX_CONTAINERS_PER_ADDON);
            limits.set("MaxAllowedItemsPerRule", MLuaBehaviorRegistry.MAX_ALLOWED_ITEMS_PER_RULE);
            limits.set("MaxAllowedItemReferencesPerContainer", MLuaBehaviorRegistry.MAX_ALLOWED_ITEM_REFERENCES_PER_CONTAINER);
            limits.set("MaxContainerTitleLength", MLuaBehaviorRegistry.MAX_CONTAINER_TITLE_LENGTH);
            limits.set("ActiveEntityAIConfigs", active.activeEntityAIConfigs());
            limits.set("AddonEntityAIConfigs", active.entityAIConfigsForAddon());
            limits.set("MaxEntityAIConfigs", MLuaBehaviorRegistry.MAX_ENTITY_AI_CONFIGS);
            limits.set("MaxEntityAIConfigsPerAddon", MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS_PER_ADDON);
            limits.set("MaxEntityAIStatesPerDefinition", MLuaBehaviorRegistry.MAX_ENTITY_AI_STATES_PER_DEFINITION);
            limits.set("MaxEntityAITableEntries", MLuaBehaviorRegistry.MAX_ENTITY_AI_TABLE_ENTRIES);
            limits.set("MaxEntityAITableDepth", MLuaBehaviorRegistry.MAX_ENTITY_AI_TABLE_DEPTH);
            limits.set("MaxIdentifierLength", MLuaBehaviorRegistry.MAX_BEHAVIOR_KEY_LENGTH);
            limits.set("MaxBehaviorNameLength", MLuaBehaviorRegistry.MAX_BEHAVIOR_NAME_LENGTH);
            limits.set("ActiveContentTypes", contentTypes.activeTotal());
            limits.set("AddonContentTypes", contentTypes.activeForOwner());
            limits.set("MaxContentTypes", MLuaContentTypeRegistry.MAX_TYPES_PER_SCOPE);
            limits.set("MaxContentTypesPerDomain", MLuaContentTypeRegistry.MAX_TYPES_PER_DOMAIN);
            limits.set("MaxContentTypeNameLength", MLuaContentTypeRegistry.MAX_QUALIFIED_NAME_LENGTH);
            limits.set("MaxContentTypeDescriptionLength", MLuaContentTypeRegistry.MAX_DESCRIPTION_LENGTH);
            limits.set("MaxContentTypeBaseLength", MLuaContentTypeRegistry.MAX_BASE_REFERENCE_LENGTH);
            limits.set("MaxContentTypeOwnerLength", MLuaContentTypeRegistry.MAX_OWNER_LENGTH);
            limits.set("MaxContentTypeDomainLength", MLuaContentTypeRegistry.MAX_DOMAIN_LENGTH);
            limits.set("MaxContentTypeDefaultEntries", MLuaContentTypeRegistry.MAX_DEFAULT_ENTRIES_PER_TYPE);
            limits.set("ActiveContentTypeDefaultTableEntries", contentTypes.defaultTableEntries());
            limits.set("AddonContentTypeDefaultTableEntries", contentTypes.defaultTableEntriesForOwner());
            limits.set("MaxContentTypeDefaultTableEntriesPerAddon", MLuaContentTypeRegistry.MAX_DEFAULT_ENTRIES_PER_OWNER);
            limits.set("MaxContentTypeDefaultTableEntriesTotal", MLuaContentTypeRegistry.MAX_TOTAL_DEFAULT_ENTRIES);
            limits.set("MaxContentTypeTableDepth", MLuaContentTypeRegistry.MAX_TABLE_DEPTH);
            limits.set("ActiveRegistryCategories", dynamic.categories());
            limits.set("AddonRegistryCategories", dynamic.categoriesForOwner());
            limits.set("MaxRegistryCategories", MLuaDynamicRegistryCatalog.MAX_CATEGORIES);
            limits.set("MaxRegistryCategoriesPerAddon", MLuaDynamicRegistryCatalog.MAX_CATEGORIES_PER_OWNER);
            limits.set("ActiveRegistryDefinitions", dynamic.definitions());
            limits.set("AddonRegistryDefinitions", dynamic.definitionsForOwner());
            limits.set("ActiveRegistryDefinitionTableEntries", dynamic.definitionTableEntries());
            limits.set("AddonRegistryDefinitionTableEntries", dynamic.definitionTableEntriesForOwner());
            limits.set("PendingRegistryDefinitions", dynamic.pendingDefinitions());
            limits.set("AddonPendingRegistryDefinitions", dynamic.pendingDefinitionsForOwner());
            limits.set("MaxRegistryDefinitions", MLuaDynamicRegistryCatalog.MAX_TOTAL_DEFINITIONS);
            limits.set("MaxRegistryDefinitionsPerCategory", MLuaDynamicRegistryCatalog.MAX_DEFINITIONS_PER_CATEGORY);
            limits.set("MaxRegistryDefinitionsPerAddon", MLuaDynamicRegistryCatalog.MAX_DEFINITIONS_PER_OWNER);
            limits.set("MaxPendingRegistryDefinitionsPerCategory", MLuaDynamicRegistryCatalog.MAX_PENDING_PER_CATEGORY);
            limits.set("MaxRegistryDefinitionIdLength", MLuaDynamicRegistryCatalog.MAX_DEFINITION_ID_LENGTH);
            limits.set("MaxRegistryDefinitionTableEntries", MLuaDynamicRegistryCatalog.MAX_TABLE_ENTRIES_PER_DEFINITION);
            limits.set("MaxRegistryDefinitionTableEntriesPerAddon",
               MLuaDynamicRegistryCatalog.MAX_DEFINITION_TABLE_ENTRIES_PER_OWNER);
            limits.set("MaxRegistryDefinitionTableEntriesTotal",
               MLuaDynamicRegistryCatalog.MAX_TOTAL_DEFINITION_TABLE_ENTRIES);
            limits.set("MaxRegistryDefinitionTableDepth", MLuaDynamicRegistryCatalog.MAX_TABLE_DEPTH);
            limits.set("ActivePlacedFeatures", worldFeatures.total());
            limits.set("AddonPlacedFeatures", worldFeatures.forAddon());
            limits.set("MaxPlacedFeatures", dev.mlua.loaderapi.WorldGenerationBridge.MAX_PLACED_FEATURES_TOTAL);
            limits.set("MaxPlacedFeaturesPerAddon", dev.mlua.loaderapi.WorldGenerationBridge.MAX_PLACED_FEATURES_PER_ADDON);
            return limits;
         }
      });
      table.set("Items", items);
      table.set("Blocks", blocks);
      table.set("Entities", entities);
      table.set("Effects", effects);
      table.set("Potions", potions);
      table.set("Particles", particles);
      table.set("Recipes", recipes);
      table.set("Loot", loot);
      table.set("Tags", tags);
      table.set("Containers", containers);
      table.set("Projectiles", projectiles);
      table.set("Tabs", tabs);
      table.set("Config", config);
      table.set("DamageSource", damageSource);
      table.set("DataPack", dataPack);
      table.set("Assets", assets);
      table.set("WorldGeneration", worldGeneration);
      table.set("ContentTypes", contentTypes);
      for (MLuaDynamicRegistryCatalog.Category category : service.dynamicRegistry.categories()) {
         service.installDynamicCategory(table, category);
      }
      service.installDynamicCategoryRegistration(table);
      MLuaRegistryGeneratedResourceApi.install(service, dataPack, assets, worldGeneration);
      MLuaRegistryContainerApi.install(service, containers);
      MLuaRegistryCreativeTabApi.install(service, tabs);
      MLuaRegistryDataApi.install(service, recipes, loot, tags, blocks);
      MLuaRegistryVisualContentApi.install(service, particles, potions, effects);
      MLuaRegistryConfigApi.install(service, config);
      MLuaRegistryDamageSourceApi.install(service, damageSource);
      MLuaRegistryItemBlockApi.install(service, items, blocks);
      MLuaRegistryEntityApi.install(service, entities);
      MLuaRegistryProjectileApi.install(service, projectiles);
      table.set("Register", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             String category = args.checkjstring(2);
             RegistryEndpoint endpoint = service.endpointResolver.resolve(category, "category", table);
             LuaTable target = table.get(endpoint.table()).checktable();
             LuaValue register = target.get(endpoint.registerMethod());
             if (!register.isfunction()) throw new LuaError("[MLua] Registry category '" + category + "' does not support generic registration");
             LuaValue[] registrationArgs = new LuaValue[endpoint.genericArgumentCount() + 1];
             registrationArgs[0] = target;
             for (int index = 1; index < registrationArgs.length; index++) {
                registrationArgs[index] = args.arg(index + 2);
             }
             return register.invoke(LuaValue.varargsOf(registrationArgs));
          }
       });
      table.set("RegisterType", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryEndpoint endpoint = service.endpointResolver.resolve(args.checkjstring(2), "type domain", table);
             LuaTable target = table.get(endpoint.typeTable()).checktable();
             return target.get("RegisterType").invoke(LuaValue.varargsOf(new LuaValue[]{target, args.arg(3), args.arg(4)}));
          }
       });
       service.installRootContentTypeApi(contentTypes);
      MLuaRegistryRootQueries.install(service, table);
       service.installGeneratedAssetLookupApi(recipes, GeneratedAssetKind.RECIPE);
       service.installGeneratedAssetLookupApi(loot, GeneratedAssetKind.LOOT_TABLE);
       for (RegistryEndpoint endpoint : service.endpointResolver.uniqueEndpoints()) {
          if (!endpoint.table().equals(endpoint.typeTable())) continue;
          if (service.dynamicRegistry.category(endpoint.table()) != null) continue;
          LuaTable target = table.get(endpoint.table()).checktable();
          service.installContentTypeApi(target, endpoint.typeDomain(), endpoint.typedDefinitions());
          if (endpoint.registryKind() != null) service.installRegistryEntryApi(target, endpoint.registryKind());
          service.installEntryLookupApi(target, endpoint.table());
          service.documentRegistryTable(target, endpoint.table());
       }
       service.documentRegistryTable(table, "Registry");
       service.documentRegistryTable(contentTypes, "ContentTypes");
      MLuaRegistryMetadata.install(service, table);
      service.wrapTypedDefinition(entities, "item", List.of(new RegistryEndpoint.TypedDefinition("RegisterSpawnEgg", 3)));
      service.wrapTypedDefinition(blocks, "item", List.of(new RegistryEndpoint.TypedDefinition("RegisterItem", 3)));
       return dev.mlua.core.service.MLuaServiceBase.normalizeServiceCalls(table, service.name());
    
   }
}
