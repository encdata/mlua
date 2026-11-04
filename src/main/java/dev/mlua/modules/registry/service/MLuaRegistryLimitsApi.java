package dev.mlua.modules.registry.service;

import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.runtime.registry.MLuaContentTypeRegistry;
import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Builds an addon-scoped snapshot of active and maximum registry resources. */
final class MLuaRegistryLimitsApi {
    private MLuaRegistryLimitsApi() { }

    static void install(MLuaRegistryService service, LuaTable registry) {
      registry.set("GetScriptLimits", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            String owner = service.apiContext().mlua().ownerModId();
            MLuaBehaviorRegistry.ScriptLimits active = MLuaBehaviorRegistry.scriptLimits(owner);
            MLuaContentTypeRegistry.Usage contentTypes = MLuaContentTypeRegistry.usage(
                  service.apiContext().mlua().registration(), owner);
            MLuaDynamicRegistryCatalog.Usage dynamic = service.dynamicRegistry.usage(owner);
            dev.mlua.loaderapi.WorldGenerationBridge.FeatureCounts worldFeatures =
                dev.mlua.loaderapi.LoaderApi.worldGeneration().featureCounts(owner);
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
    }
}
