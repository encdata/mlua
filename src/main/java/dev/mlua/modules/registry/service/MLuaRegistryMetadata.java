package dev.mlua.modules.registry.service;

import dev.mlua.common.service.MLuaServiceMetadata;
import org.luaj.vm2.LuaTable;
import java.util.Map;

/** Attaches documented method surfaces to the registry root and its category tables. */
final class MLuaRegistryMetadata {
    private MLuaRegistryMetadata() { }

    static void install(MLuaRegistryService service, LuaTable table) {
        LuaTable items = category(table, "Items");
        LuaTable blocks = category(table, "Blocks");
        LuaTable entities = category(table, "Entities");
        LuaTable effects = category(table, "Effects");
        LuaTable potions = category(table, "Potions");
        LuaTable particles = category(table, "Particles");
        LuaTable recipes = category(table, "Recipes");
        LuaTable loot = category(table, "Loot");
        LuaTable containers = category(table, "Containers");
        LuaTable projectiles = category(table, "Projectiles");
        LuaTable tabs = category(table, "Tabs");
        LuaTable dataPack = category(table, "DataPack");
        LuaTable assets = category(table, "Assets");
        LuaTable worldGeneration = category(table, "WorldGeneration");
      Map<String, String> containerDescriptions = new java.util.LinkedHashMap<>(RegistryServiceMethods.methodDescriptions());
      containerDescriptions.put("Register", "Registers a reusable container layout and returns its ContainerType instance.");
      containerDescriptions.put("Get", "Returns the registered ContainerType for an id, or nil when it is unknown.");
      containerDescriptions.put("GetAll", "Returns registered ContainerType instances keyed by namespaced id, optionally filtered by namespace.");
      containerDescriptions.put("GetIds", "Returns sorted container type ids, optionally filtered by namespace.");
      containerDescriptions.put("Exists", "Checks whether a container type id is registered.");
      MLuaServiceMetadata.attachMethodDescriptions(containers, containerDescriptions);
      Map<String, String> dataPackDescriptions = new java.util.LinkedHashMap<>(RegistryServiceMethods.methodDescriptions());
      dataPackDescriptions.put("Register", "Writes a Minecraft data-pack JSON resource under the addon's namespace.");
      dataPackDescriptions.put("Get", "Returns a typed GeneratedDataResource handle for a resource type and id, or nil when missing.");
      dataPackDescriptions.put("GetEntry", "Returns the typed resource handle for a resource type and id, or nil when missing.");
      dataPackDescriptions.put("GetAll", "Returns typed resource handles keyed by namespaced id, filtered by resource type and optional namespace.");
      dataPackDescriptions.put("GetEntries", "Returns sorted typed resource handles filtered by resource type and optional namespace.");
      dataPackDescriptions.put("GetIds", "Returns sorted resource ids filtered by resource type and optional namespace.");
      dataPackDescriptions.put("Exists", "Checks whether a resource type and id are registered.");
      MLuaServiceMetadata.attachMethodDescriptions(dataPack, dataPackDescriptions);
      service.describeRegistryMethod(items, "Register", "Registers an item with this addon's namespace and returns its live RegistryEntry key.");
      service.describeRegistryMethod(items, "Properties", "Creates a chainable ItemProperties instance matching Minecraft Item.Properties.");
      service.describeRegistryMethod(blocks, "Register", "Registers a block and returns its live RegistryEntry key.");
      service.describeRegistryMethod(blocks, "RegisterItem", "Registers the block's item form and returns its live item RegistryEntry key.");
      service.describeRegistryMethod(blocks, "Properties", "Creates a chainable BlockProperties instance matching Minecraft BlockBehaviour.Properties.");
      service.describeRegistryMethod(entities, "Register", "Registers a scripted entity type and returns its live RegistryEntry key.");
      service.describeRegistryMethod(entities, "Properties", "Creates a chainable EntityProperties instance for a scripted mob type.");
      service.describeRegistryMethod(entities, "RegisterSpawnEgg", "Registers a spawn egg for a registered entity and returns its item RegistryEntry key.");
      service.describeRegistryMethod(effects, "Register", "Registers a scripted mob effect and returns its live RegistryEntry key.");
      service.describeRegistryMethod(potions, "Register", "Registers a potion and returns its live RegistryEntry key.");
      service.describeRegistryMethod(particles, "Register", "Registers a particle type and returns its live RegistryEntry key.");
      service.describeRegistryMethod(tabs, "Register", "Registers a creative tab and returns its live RegistryEntry key.");
      Map<String, String> projectileDescriptions = new java.util.LinkedHashMap<>(RegistryServiceMethods.methodDescriptions());
      projectileDescriptions.put("Register", "Registers a reusable projectile definition and returns its ProjectileType instance.");
      projectileDescriptions.put("Get", "Returns a registered ProjectileType in this runtime, or nil when the id is unknown.");
      projectileDescriptions.put("GetAll", "Returns registered ProjectileType instances keyed by namespaced id, optionally filtered by namespace.");
      projectileDescriptions.put("GetIds", "Returns sorted projectile ids registered in this runtime, optionally filtered by namespace.");
      projectileDescriptions.put("Exists", "Checks whether a ProjectileType id is registered in this runtime.");
      MLuaServiceMetadata.attachMethodDescriptions(projectiles, projectileDescriptions);
      Map<String, String> generatedAssetDescriptions = new java.util.LinkedHashMap<>(RegistryServiceMethods.methodDescriptions());
      generatedAssetDescriptions.put("Get", "Returns a live GeneratedAssetResource for an MLua-generated recipe or loot table, or nil when missing.");
      generatedAssetDescriptions.put("GetEntry", "Returns the same live generated-asset handle as Get.");
      generatedAssetDescriptions.put("GetIds", "Returns sorted generated-asset ids, optionally filtered by namespace.");
      generatedAssetDescriptions.put("GetEntries", "Returns sorted GeneratedAssetResource handles, optionally filtered by namespace.");
      generatedAssetDescriptions.put("GetAll", "Returns GeneratedAssetResource handles keyed by full namespaced id.");
      generatedAssetDescriptions.put("Exists", "Checks whether an MLua-generated recipe or loot table is registered.");
      MLuaServiceMetadata.attachMethodDescriptions(recipes, generatedAssetDescriptions);
      MLuaServiceMetadata.attachMethodDescriptions(loot, generatedAssetDescriptions);
      Map<String, String> assetDescriptions = new java.util.LinkedHashMap<>(RegistryServiceMethods.methodDescriptions());
      assetDescriptions.put("Register", "Registers a JSON resource in the addon's assets namespace, merging distinct top-level keys in shared files such as sounds.json.");
      assetDescriptions.put("register", "Registers a JSON resource in the addon's assets namespace, merging distinct top-level keys in shared files such as sounds.json.");
      MLuaServiceMetadata.attachMethodDescriptions(assets, assetDescriptions);
      Map<String, String> worldGenerationDescriptions = new java.util.LinkedHashMap<>(RegistryServiceMethods.methodDescriptions());
      worldGenerationDescriptions.put("Register", "Writes a biome, dimension, dimension type, or worldgen JSON resource to the generated data pack.");
      worldGenerationDescriptions.put("Get", "Returns a typed GeneratedDataResource handle for a resource type and id, or nil when missing.");
      worldGenerationDescriptions.put("GetEntry", "Returns the typed resource handle for a resource type and id, or nil when missing.");
      worldGenerationDescriptions.put("GetAll", "Returns typed resource handles keyed by namespaced id, filtered by resource type and optional namespace.");
      worldGenerationDescriptions.put("GetEntries", "Returns sorted typed resource handles filtered by resource type and optional namespace.");
      worldGenerationDescriptions.put("GetIds", "Returns sorted resource ids filtered by resource type and optional namespace.");
      worldGenerationDescriptions.put("Exists", "Checks whether a resource type and id are registered.");
      worldGenerationDescriptions.put("AddFeature", "Adds a placed feature to matching biomes at the selected generation step. Contributions are owned by the registry script, removed on rollback, and limited to "
         + dev.mlua.loaderapi.WorldGenerationBridge.MAX_PLACED_FEATURES_PER_ADDON + " per addon and "
         + dev.mlua.loaderapi.WorldGenerationBridge.MAX_PLACED_FEATURES_TOTAL + " total.");
      worldGenerationDescriptions.put("register", "Writes a biome, dimension, dimension type, or worldgen JSON resource to the generated data pack.");
      worldGenerationDescriptions.put("addFeature", "Adds a placed feature to matching biomes at the selected generation step. Contributions are owned by the registry script, removed on rollback, and limited to "
         + dev.mlua.loaderapi.WorldGenerationBridge.MAX_PLACED_FEATURES_PER_ADDON + " per addon and "
         + dev.mlua.loaderapi.WorldGenerationBridge.MAX_PLACED_FEATURES_TOTAL + " total.");
      worldGenerationDescriptions.put("RegisterBiome", "Registers a biome resource under worldgen/biome.");
      worldGenerationDescriptions.put("GetBiome", "Returns a typed handle for a registered biome resource, or nil when missing.");
      worldGenerationDescriptions.put("registerBiome", "Registers a biome resource under worldgen/biome.");
      worldGenerationDescriptions.put("RegisterDimensionType", "Registers a dimension type resource.");
      worldGenerationDescriptions.put("GetDimensionType", "Returns a typed handle for a registered dimension type, or nil when missing.");
      worldGenerationDescriptions.put("registerDimensionType", "Registers a dimension type resource.");
      worldGenerationDescriptions.put("RegisterDimension", "Registers a dimension resource.");
      worldGenerationDescriptions.put("GetDimension", "Returns a typed handle for a registered dimension, or nil when missing.");
      worldGenerationDescriptions.put("registerDimension", "Registers a dimension resource.");
      worldGenerationDescriptions.put("RegisterNoiseSettings", "Registers terrain noise settings under worldgen/noise_settings.");
      worldGenerationDescriptions.put("registerNoiseSettings", "Registers terrain noise settings under worldgen/noise_settings.");
      worldGenerationDescriptions.put("RegisterDensityFunction", "Registers a terrain density function under worldgen/density_function.");
      worldGenerationDescriptions.put("registerDensityFunction", "Registers a terrain density function under worldgen/density_function.");
      worldGenerationDescriptions.put("RegisterConfiguredFeature", "Registers a configured world generation feature.");
      worldGenerationDescriptions.put("registerConfiguredFeature", "Registers a configured world generation feature.");
      worldGenerationDescriptions.put("RegisterPlacedFeature", "Registers a placed world generation feature.");
      worldGenerationDescriptions.put("registerPlacedFeature", "Registers a placed world generation feature.");
      worldGenerationDescriptions.put("RegisterConfiguredCarver", "Registers a configured cave or canyon carver.");
      worldGenerationDescriptions.put("registerConfiguredCarver", "Registers a configured cave or canyon carver.");
      worldGenerationDescriptions.put("RegisterStructure", "Registers a world structure definition.");
      worldGenerationDescriptions.put("registerStructure", "Registers a world structure definition.");
      worldGenerationDescriptions.put("RegisterStructureSet", "Registers structure placement and spacing rules.");
      worldGenerationDescriptions.put("registerStructureSet", "Registers structure placement and spacing rules.");
      worldGenerationDescriptions.put("RegisterTemplatePool", "Registers a jigsaw structure template pool.");
      worldGenerationDescriptions.put("registerTemplatePool", "Registers a jigsaw structure template pool.");
      MLuaServiceMetadata.attachMethodDescriptions(worldGeneration, worldGenerationDescriptions);
    }

    private static LuaTable category(LuaTable registry, String name) {
        return registry.get(name).checktable();
    }
}
