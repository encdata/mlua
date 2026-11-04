package dev.mlua.modules.registry.service;

import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;
import dev.mlua.runtime.registry.MLuaContentTypeRegistry;
import java.util.List;
import java.util.Map;

/** Central descriptions for the Registry service and its category tables. */
final class RegistryServiceMethods {
   private static final Map<String, String> METHOD_DESCRIPTIONS = registryMethodDescriptions();
   private static final List<String> SERVICE_METHODS = List.of(
      "Register", "RegisterCategory", "RegisterType", "ResolveType", "GetKinds", "GetCategories",
      "GetCategory", "Get", "GetAll", "GetIds", "GetTypes", "GetRegistered", "GetEntries", "GetId", "GetEntry", "Exists", "GetScriptLimits");
   private static final Map<String, String> SERVICE_METHOD_DESCRIPTIONS = createServiceMethodDescriptions();

   private RegistryServiceMethods() { }

   static Map<String, String> methodDescriptions() { return METHOD_DESCRIPTIONS; }

   static Map<String, String> serviceMethodDescriptions() { return SERVICE_METHOD_DESCRIPTIONS; }

   private static Map<String, String> createServiceMethodDescriptions() {
      Map<String, String> methods = new java.util.LinkedHashMap<>();
      for (String name : SERVICE_METHODS) methods.put(name, METHOD_DESCRIPTIONS.get(name));
      return java.util.Collections.unmodifiableMap(methods);
   }

   private static Map<String, String> registryMethodDescriptions() {
      Map<String, String> descriptions = new java.util.LinkedHashMap<>();
      descriptions.put("Register", "Routes a named content definition through its typed registry.");
      descriptions.put("RegisterCategory", "Adds a lowercase namespaced category under the addon's namespace. Category names are limited to "
         + MLuaDynamicRegistryCatalog.MAX_CATEGORY_NAME_LENGTH + " characters and descriptions to "
         + MLuaDynamicRegistryCatalog.MAX_CATEGORY_DESCRIPTION_LENGTH
         + " characters. Its register callback runs with the registering addon's script context.");
      descriptions.put("RegisterType", "Registers an addon-scoped reusable content type with inherited defaults. Qualified names and base references are limited to "
         + dev.mlua.runtime.registry.MLuaContentTypeRegistry.MAX_QUALIFIED_NAME_LENGTH + " characters, descriptions to "
         + dev.mlua.runtime.registry.MLuaContentTypeRegistry.MAX_DESCRIPTION_LENGTH
         + " characters. Defaults are snapshotted; cyclic tables and definitions nested more than "
         + dev.mlua.runtime.registry.MLuaContentTypeRegistry.MAX_TABLE_DEPTH + " tables deep are rejected.");
      descriptions.put("ResolveType", "Returns a definition with its reusable type defaults merged in.");
      descriptions.put("GetKinds", "Lists the registry kinds supported by this loader.");
      descriptions.put("GetCategories", "Lists the built-in and addon-owned registration categories.");
      descriptions.put("GetCategory", "Returns the live category API table for a built-in or addon-owned category name or alias.");
      descriptions.put("Get", "Returns a live typed handle for a registered entry, projectile type, or addon-owned definition; returns nil when a definition is missing.");
      descriptions.put("GetAll", "Returns live typed handles for entries currently exposed by Minecraft's built-in registries and MLua's pending registrations, keyed by namespaced identifier and optionally filtered by namespace.");
      descriptions.put("GetIds", "Returns sorted identifiers currently exposed by Minecraft's built-in registries plus MLua's pending registrations, or from an addon-owned category, optionally filtered by namespace.");
      descriptions.put("GetTypes", "Lists reusable implementation and addon-defined content types for a category.");
      descriptions.put("GetRegistered", "Returns identifiers registered through MLua for a supported Minecraft registry kind.");
      descriptions.put("GetEntries", "Returns typed entry objects for identifiers currently exposed by Minecraft's built-in registries and MLua's pending registrations as a sorted array, optionally filtered by namespace.");
      descriptions.put("GetId", "Resolves a short name in the addon's namespace or validates a full namespaced identifier.");
      descriptions.put("GetEntry", "Returns the category's typed entry handle for an identifier. Addon-owned definition categories return nil when the identifier is not registered.");
      descriptions.put("Exists", "Checks whether an identifier currently resolves in its registry.");
      descriptions.put("GetScriptLimits", "Returns active and addon-owned scripted behavior, content type, dynamic registry, and world generation feature counts with their configured limits, including stored table-entry budgets for addon-defined registry data and maximum Lua instructions per gameplay callback.");
      descriptions.put("RegisterItem", "Registers an item form for a previously registered block.");
      descriptions.put("GetPushReactions", "Lists the piston reactions supported by this Minecraft version.");
      descriptions.put("GetInstruments", "Lists the note block instruments supported by this Minecraft version.");
      descriptions.put("GetSounds", "Lists block sound families available in this Minecraft version.");
      descriptions.put("GetMapColors", "Lists block map colors available in this Minecraft version.");
      descriptions.put("GetTiers", "Lists item tool tiers available in this Minecraft version.");
      descriptions.put("GetRarities", "Lists item rarities available in this Minecraft version.");
      descriptions.put("RegisterCooking", "Registers a vanilla smelting, blasting, smoking, or campfire recipe. Set count for the output stack; Minecraft 1.20.1 only accepts count = 1, while newer supported versions accept an item-stack result. For modded recipe types, use Register with the complete recipe JSON.");
      descriptions.put("RegisterSmelting", "Registers a vanilla smelting recipe. Use RegisterCooking when selecting another vanilla cooking type.");
      descriptions.put("RegisterStonecutting", "Registers a vanilla stonecutting recipe.");
      descriptions.put("register", "Writes a biome, dimension, dimension type, or worldgen JSON resource to the generated data pack.");
      descriptions.put("addFeature", "Adds a placed feature to matching biomes at the selected generation step.");
      descriptions.put("AddFeature", "Adds a placed feature to matching biomes at the selected generation step.");
      return java.util.Collections.unmodifiableMap(descriptions);
   }

}
