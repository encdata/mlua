package dev.mlua.modules.registry.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.mlua.minecraft.MLuaResourceLocations;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates Lua recipe ingredient selectors and emits version-native JSON shapes. */
final class MLuaRecipeIngredientJson {
   static final int MAX_ALTERNATIVE_ITEMS = 256;

   private MLuaRecipeIngredientJson() { }

   static JsonElement single(LuaValue value, String field) {
      JsonElement element;
      if (value.isstring()) element = new JsonPrimitive(value.tojstring());
      else if (value.istable()) element = MLuaLuaJson.fromLua(value);
      else throw new LuaError("[MLua] " + field + " must be an item id, '#tag' selector, or { item = \"namespace:id\" } / { tag = \"namespace:path\" }");
      return normalize(element, field);
   }

   static JsonElement normalize(JsonElement element, String field) {
      String itemId = null;
      String tagId = null;
      List<String> itemIds = null;
      if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
         String selector = element.getAsString().trim();
         if (selector.startsWith("#")) tagId = selector.substring(1);
         else itemId = selector;
      } else if (element.isJsonObject()) {
         JsonObject definition = element.getAsJsonObject();
         int selectors = (definition.has("item") ? 1 : 0) + (definition.has("tag") ? 1 : 0)
            + (definition.has("items") ? 1 : 0);
         if (selectors != 1 || definition.size() != 1) {
            throw new LuaError("[MLua] " + field + " ingredient table must contain exactly one of item, tag, or items");
         }
         if (definition.has("item") && definition.get("item").isJsonPrimitive()
               && definition.get("item").getAsJsonPrimitive().isString()) {
            itemId = definition.get("item").getAsString().trim();
         } else if (definition.has("item")) {
            throw new LuaError("[MLua] " + field + ".item must be a resource identifier string");
         }
         if (definition.has("tag") && definition.get("tag").isJsonPrimitive()
               && definition.get("tag").getAsJsonPrimitive().isString()) {
            tagId = definition.get("tag").getAsString().trim();
         } else if (definition.has("tag")) {
            throw new LuaError("[MLua] " + field + ".tag must be a resource identifier string");
         }
         if (definition.has("items")) {
            JsonElement items = definition.get("items");
            if (!items.isJsonArray() || items.getAsJsonArray().size() < 1
                  || items.getAsJsonArray().size() > MAX_ALTERNATIVE_ITEMS) {
               throw new LuaError("[MLua] " + field + ".items must be an array with 1-"
                  + MAX_ALTERNATIVE_ITEMS + " item identifiers");
            }
            itemIds = new ArrayList<>(items.getAsJsonArray().size());
            Set<String> uniqueItems = new HashSet<>();
            for (JsonElement item : items.getAsJsonArray()) {
               if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                  throw new LuaError("[MLua] " + field + ".items entries must be resource identifier strings");
               }
               String id = validateId(item.getAsString().trim(), field + ".items");
               if ("minecraft:air".equals(id)) {
                  throw new LuaError("[MLua] " + field + ".items cannot include minecraft:air");
               }
               if (!uniqueItems.add(id)) {
                  throw new LuaError("[MLua] " + field + ".items contains duplicate item '" + id + "'");
               }
               itemIds.add(id);
            }
         }
      } else {
         throw new LuaError("[MLua] " + field + " must be an item id, '#tag' selector, or an item/tag/items definition table");
      }

      if (itemIds != null) {
         //--version 1.20.1,1.21.1
         JsonArray legacyAlternatives = new JsonArray();
         for (String id : itemIds) {
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", id);
            legacyAlternatives.add(ingredient);
         }
         return legacyAlternatives;
         //--endversion
         //--version 26.1
         JsonArray alternatives = new JsonArray();
         for (String id : itemIds) alternatives.add(id);
         return alternatives;
         //--endversion
      }

      boolean isTag = tagId != null;
      String id = isTag ? tagId : itemId;
      if (id == null || id.isBlank()) throw new LuaError("[MLua] " + field + " must specify one non-empty item or tag id");
      id = validateId(id, field);
      if (!isTag && "minecraft:air".equals(id)) {
         throw new LuaError("[MLua] " + field + " cannot use minecraft:air as a recipe ingredient");
      }

      //--version 1.20.1,1.21.1
      JsonObject legacy = new JsonObject();
      legacy.addProperty(isTag ? "tag" : "item", id);
      return legacy;
      //--endversion
      //--version 26.1
      return new JsonPrimitive(isTag ? "#" + id : id);
      //--endversion
   }

   static String validateId(String id, String field) {
      try {
         return MLuaResourceLocations.parse(id).toString();
      } catch (RuntimeException invalid) {
         throw new LuaError("[MLua] " + field + " contains an invalid resource identifier '" + id + "'");
      }
   }
}
