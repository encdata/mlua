package dev.mlua.modules.registry.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;

/** Owns generated tag definitions and their registration rollback behavior. */
final class MLuaRegistryTagOperations {
   private final MLuaRegistryService service;

   MLuaRegistryTagOperations(MLuaRegistryService service) {
      this.service = service;
   }

   String register(String kind, String name, LuaValue definition) {
      TagRegistration tag = parse(definition);
      String owner = service.apiContext().mlua().ownerModId();
      return register(kind, owner, name, tag.replace(), tag.values());
   }

   String append(String kind, String name, LuaValue valuesValue) {
      if (!valuesValue.istable()) throw new LuaError("[MLua] Tag values must be a table");
      JsonElement valuesElement = MLuaLuaJson.fromLua(valuesValue);
      if (!valuesElement.isJsonArray()) {
         throw new LuaError("[MLua] Tag values must be an array-style table");
      }
      return register(kind, service.apiContext().mlua().ownerModId(), name, false,
         valuesElement.getAsJsonArray());
   }

   void registerMiningProperties(String owner, String name, MLuaRegistryMiningProperties properties) {
      if (properties.mineableTag() != null) registerSingleBlock(owner, properties.mineableTag(), owner + ":" + name);
      if (properties.requiredTierTag() != null) registerSingleBlock(owner, properties.requiredTierTag(), owner + ":" + name);
   }

   private String register(String kind, String owner, String name, boolean replace, JsonArray values) {
      return switch (normalizeKind(kind)) {
         case "items" -> registerWithRollback(RegistryKind.ITEM, owner, name, replace, values);
         case "blocks" -> registerWithRollback(RegistryKind.BLOCK, owner, name, replace, values);
         case "entity_types" -> registerWithRollback(RegistryKind.ENTITY, owner, name, replace, values);
         default -> throw new LuaError("[MLua] Unsupported tag kind: " + kind);
      };
   }

   private String registerWithRollback(RegistryKind registryKind, String owner, String name,
                                       boolean replace, JsonArray values) {
      GeneratedAssetKind assetKind = switch (registryKind) {
         case ITEM -> GeneratedAssetKind.ITEM_TAG;
         case BLOCK -> GeneratedAssetKind.BLOCK_TAG;
         case ENTITY -> GeneratedAssetKind.ENTITY_TYPE_TAG;
         default -> throw new LuaError("[MLua] Unsupported tag registry: " + registryKind);
      };
      String id = service.registryId(name);
      JsonObject previous = MLuaLoaderRegistry.getGeneratedAssets(assetKind).get(id);
      MLuaLoaderRegistry.registerTag(registryKind, owner, name, replace, values);
      JsonObject expected = MLuaLoaderRegistry.getGeneratedAssets(assetKind).get(id);
      if (expected == null) throw new LuaError("[MLua] Tag registration did not produce '" + id + "'");
      service.apiContext().mlua().registration().trackRollback(() ->
         MLuaLoaderRegistry.restoreGeneratedAsset(assetKind, owner, name, expected, previous));
      return id;
   }

   private void registerSingleBlock(String owner, String tagName, String blockId) {
      JsonArray values = new JsonArray();
      values.add(blockId);
      registerWithRollback(RegistryKind.BLOCK, owner, tagName, false, values);
   }

   private static TagRegistration parse(LuaValue value) {
      if (value.type() == LuaValue.TSTRING) {
         JsonObject object = MLuaLuaJson.objectArg(value, "tag");
         JsonElement entries = object.get("values");
         if (entries == null || !entries.isJsonArray()) {
            throw new LuaError("[MLua] Tag JSON must contain a values array");
         }
         JsonElement replaceValue = object.get("replace");
         if (replaceValue != null && (!replaceValue.isJsonPrimitive()
             || !replaceValue.getAsJsonPrimitive().isBoolean())) {
            throw new LuaError("[MLua] Tag replace must be a boolean");
         }
         return new TagRegistration(replaceValue != null && replaceValue.getAsBoolean(), entries.getAsJsonArray());
      }
      if (!value.istable()) throw new LuaError("[MLua] Tag definition must be a table or JSON object");
      LuaTable table = value.checktable();
      boolean structured = !table.get("values").isnil() || !table.get("replace").isnil();
      boolean replace = structured && table.get("replace").optboolean(false);
      LuaValue valuesValue = structured ? table.get("values") : value;
      JsonElement valuesElement = MLuaLuaJson.fromLua(valuesValue);
      if (!valuesElement.isJsonArray()) throw new LuaError("[MLua] Tag values must be an array-style table");
      return new TagRegistration(replace, valuesElement.getAsJsonArray());
   }

   private static String normalizeKind(String value) {
      return switch (value.trim().toLowerCase()) {
         case "item", "items" -> "items";
         case "block", "blocks" -> "blocks";
         case "entity", "entities", "entity_type", "entity_types" -> "entity_types";
         default -> value.trim().toLowerCase();
      };
   }

   private record TagRegistration(boolean replace, JsonArray values) { }
}
