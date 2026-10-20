package dev.mlua.modules.registry.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Lua recipe definitions into the data-pack JSON shapes Minecraft expects. */
final class MLuaRecipeJson {
   static final int MAX_RECIPE_INGREDIENT_ITEMS = 256;

   private MLuaRecipeJson() { }

   static JsonObject shapedRecipe(LuaValue value) {
      LuaTable table = checkedTable(value, "shaped recipe");
      JsonArray pattern = requiredStringArray(table.get("pattern"), "pattern");
      if (pattern.size() < 1 || pattern.size() > 3) {
         throw new LuaError("[MLua] Shaped recipes require 1-3 rows");
      }
      int width = pattern.get(0).getAsString().length();
      if (width < 1 || width > 3) throw new LuaError("[MLua] Shaped rows must contain 1-3 characters");
      for (JsonElement row : pattern) {
         if (row.getAsString().length() != width) {
            throw new LuaError("[MLua] All Shaped rows must have the same width");
         }
      }
      JsonObject keys = requiredObject(table.get("key"), "key");
      boolean hasIngredient = false;
      for (JsonElement row : pattern) {
         for (char symbol : row.getAsString().toCharArray()) {
            if (symbol == ' ') continue;
            hasIngredient = true;
            if (!keys.has(String.valueOf(symbol))) {
               throw new LuaError("[MLua] Shaped pattern has no key for '" + symbol + "'");
            }
         }
      }
      if (!hasIngredient) throw new LuaError("[MLua] Shaped pattern must contain an ingredient");
      for (String symbol : keys.keySet()) {
         if (symbol.length() != 1 || symbol.equals(" ")) {
            throw new LuaError("[MLua] Shaped keys must be one non-space character");
         }
         boolean used = false;
         for (JsonElement row : pattern) {
            if (row.getAsString().contains(symbol)) used = true;
         }
         if (!used) throw new LuaError("[MLua] Shaped key '" + symbol + "' is not used");
      }
      for (Map.Entry<String, JsonElement> entry : new ArrayList<>(keys.entrySet())) {
         keys.add(entry.getKey(), normalizeIngredient(entry.getValue(), "key." + entry.getKey()));
      }
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", "minecraft:crafting_shaped");
      recipe.add("pattern", pattern);
      recipe.add("key", keys);
      recipe.add("result", recipeResult(table.get("result")));
      return recipe;
   }

   static JsonObject shapelessRecipe(LuaValue value) {
      LuaTable table = checkedTable(value, "shapeless recipe");
      JsonArray ingredients = requiredArray(table.get("ingredients"), "ingredients");
      if (ingredients.size() < 1 || ingredients.size() > 9) {
         throw new LuaError("[MLua] Shapeless recipes require 1-9 ingredients");
      }
      for (int index = 0; index < ingredients.size(); index++) {
         ingredients.set(index, normalizeIngredient(ingredients.get(index), "ingredients[" + (index + 1) + "]"));
      }
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", "minecraft:crafting_shapeless");
      recipe.add("ingredients", ingredients);
      recipe.add("result", recipeResult(table.get("result")));
      return recipe;
   }

   static JsonObject cookingRecipe(String type, LuaValue value, String operation) {
      LuaTable table = checkedTable(value, type + " recipe");
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", type);
      recipe.add("ingredient", singleIngredient(table.get("ingredient"), "ingredient"));
      String resultId = requiredString(table.get("result"), "result");
      MLuaResourceLocations.parse(resultId);
      int resultCount = MLuaLuaArguments.optionalWholeInt(table.get("count"), 1, operation, "count");
      if (resultCount < 1) throw new LuaError("[MLua] Cooking recipe result count must be at least 1");
      //--version 1.20.1
      if (resultCount != 1) {
         throw new LuaError("[MLua] Minecraft 1.20.1 cooking recipes only support a result count of 1");
      }
      recipe.addProperty("result", resultId);
      //--endversion
      //--version 1.21.1
      recipe.add("result", itemStackResult(resultId, resultCount));
      //--endversion
      //--version 26.1
      recipe.add("result", itemStackResult(resultId, resultCount));
      //--endversion
      double experience = table.get("experience").optdouble(0.0);
      LuaValue cookingTimeValue = table.get("cookingTime");
      if (cookingTimeValue.isnil()) cookingTimeValue = table.get("cookingtime");
      int cookingTime = MLuaLuaArguments.optionalWholeInt(cookingTimeValue, 200,
            operation, "cookingTime");
      if (!Double.isFinite(experience) || experience < 0) {
         throw new LuaError("[MLua] Cooking experience must be finite and non-negative");
      }
      if (cookingTime < 1) throw new LuaError("[MLua] Cooking time must be at least 1 tick");
      recipe.addProperty("experience", experience);
      recipe.addProperty("cookingtime", cookingTime);
      return recipe;
   }

   static String cookingRecipeType(LuaValue value) {
      if (!value.isstring()) {
         throw new LuaError("[MLua] Cooking recipe type must be smelting, blasting, smoking, or campfire_cooking.");
      }
      return switch (value.checkjstring().trim().toLowerCase(java.util.Locale.ROOT)) {
         case "smelting", "minecraft:smelting" -> "minecraft:smelting";
         case "blasting", "minecraft:blasting" -> "minecraft:blasting";
         case "smoking", "minecraft:smoking" -> "minecraft:smoking";
         case "campfire_cooking", "campfire", "minecraft:campfire_cooking" -> "minecraft:campfire_cooking";
         default -> throw new LuaError("[MLua] Unsupported cooking recipe type '" + value.checkjstring()
            + "'. Expected smelting, blasting, smoking, or campfire_cooking.");
      };
   }

   static JsonObject stonecuttingRecipe(LuaValue value) {
      LuaTable table = checkedTable(value, "stonecutting recipe");
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", "minecraft:stonecutting");
      recipe.add("ingredient", singleIngredient(table.get("ingredient"), "ingredient"));
      String resultId = requiredString(table.get("result"), "result");
      MLuaResourceLocations.parse(resultId);
      int count = MLuaLuaArguments.optionalWholeInt(table.get("count"), 1,
            "Registry.Recipes:RegisterStonecutting", "count");
      if (count < 1) throw new LuaError("[MLua] Stonecutting count must be at least 1");
      //--version 1.20.1
      recipe.addProperty("result", resultId);
      recipe.addProperty("count", count);
      //--endversion
      //--version 1.21.1
      recipe.add("result", itemStackResult(resultId, count));
      //--endversion
      //--version 26.1
      recipe.add("result", itemStackResult(resultId, count));
      //--endversion
      return recipe;
   }

   static JsonObject smithingTransformRecipe(LuaValue value) {
      LuaTable table = checkedTable(value, "smithing transform recipe");
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", "minecraft:smithing_transform");
      recipe.add("template", singleIngredient(table.get("template"), "template"));
      recipe.add("base", singleIngredient(table.get("base"), "base"));
      recipe.add("addition", singleIngredient(table.get("addition"), "addition"));
      String resultId = requiredString(table.get("result"), "result");
      MLuaResourceLocations.parse(resultId);
      int count = MLuaLuaArguments.optionalWholeInt(table.get("count"), 1,
            "Registry.Recipes:RegisterSmithingTransform", "count");
      if (count < 1) throw new LuaError("[MLua] Smithing transform count must be at least 1");
      //--version 1.20.1
      JsonObject result = new JsonObject();
      result.addProperty("item", resultId);
      if (count != 1) result.addProperty("count", count);
      recipe.add("result", result);
      //--endversion
      //--version 1.21.1
      recipe.add("result", itemStackResult(resultId, count));
      //--endversion
      //--version 26.1
      recipe.add("result", itemStackResult(resultId, count));
      //--endversion
      return recipe;
   }

   static JsonObject smithingTrimRecipe(LuaValue value) {
      LuaTable table = checkedTable(value, "smithing trim recipe");
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", "minecraft:smithing_trim");
      recipe.add("template", singleIngredient(table.get("template"), "template"));
      recipe.add("base", singleIngredient(table.get("base"), "base"));
      recipe.add("addition", singleIngredient(table.get("addition"), "addition"));
      return recipe;
   }

   private static JsonObject itemStackResult(String itemId, int count) {
      JsonObject result = new JsonObject();
      result.addProperty("id", itemId);
      result.addProperty("count", count);
      return result;
   }

   private static LuaTable checkedTable(LuaValue value, String type) {
      if (!value.istable()) {
         throw new LuaError("[MLua] " + type + " definition must be a table");
      }

      return value.checktable();
   }

   private static JsonArray requiredArray(LuaValue value, String field) {
      JsonElement element = MLuaLuaJson.fromLua(value);
      if (!element.isJsonArray()) {
         throw new LuaError("[MLua] " + field + " must be an array-style table");
      }

      return element.getAsJsonArray();
   }

   private static JsonArray requiredStringArray(LuaValue value, String field) {
      if (!value.istable()) {
         throw new LuaError("[MLua] " + field + " must be an array-style table of strings");
      }

      LuaTable table = value.checktable();
      JsonArray result = new JsonArray();
      for(int i = 1; !table.get(i).isnil(); ++i) {
         LuaValue entry = table.get(i);
         if (entry.type() != LuaValue.TSTRING) {
            throw new LuaError("[MLua] " + field + " entries must be strings");
         }

         result.add(entry.tojstring());
      }

      return result;
   }

   private static JsonObject requiredObject(LuaValue value, String field) {
      JsonElement element = MLuaLuaJson.fromLua(value);
      if (!element.isJsonObject()) {
         throw new LuaError("[MLua] " + field + " must be a table with string keys");
      }

      return element.getAsJsonObject();
   }

   private static String requiredString(LuaValue value, String field) {
      if (value.type() != LuaValue.TSTRING) {
         throw new LuaError("[MLua] " + field + " must be a string");
      }

      return value.tojstring();
   }

   private static JsonElement singleIngredient(LuaValue value, String field) {
      JsonElement element;
      if (value.isstring()) element = new JsonPrimitive(value.tojstring());
      else if (value.istable()) element = MLuaLuaJson.fromLua(value);
      else throw new LuaError("[MLua] " + field + " must be an item id, '#tag' selector, or { item = \"namespace:id\" } / { tag = \"namespace:path\" }");
      return normalizeIngredient(element, field);
   }

   private static JsonElement normalizeIngredient(JsonElement element, String field) {
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
                  || items.getAsJsonArray().size() > MAX_RECIPE_INGREDIENT_ITEMS) {
               throw new LuaError("[MLua] " + field + ".items must be an array with 1-"
                  + MAX_RECIPE_INGREDIENT_ITEMS + " item identifiers");
            }
            itemIds = new ArrayList<>(items.getAsJsonArray().size());
            java.util.HashSet<String> uniqueItems = new java.util.HashSet<>();
            for (JsonElement item : items.getAsJsonArray()) {
               if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                  throw new LuaError("[MLua] " + field + ".items entries must be resource identifier strings");
               }
               String id = validateRecipeIngredientId(item.getAsString().trim(), field + ".items");
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
         //--version 1.20.1
         JsonArray legacyAlternatives = new JsonArray();
         for (String id : itemIds) {
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", id);
            legacyAlternatives.add(ingredient);
         }
         return legacyAlternatives;
         //--endversion
         //--version 1.21.1
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
      id = validateRecipeIngredientId(id, field);
      if (!isTag && "minecraft:air".equals(id)) {
         throw new LuaError("[MLua] " + field + " cannot use minecraft:air as a recipe ingredient");
      }

      //--version 1.20.1
      JsonObject legacy = new JsonObject();
      legacy.addProperty(isTag ? "tag" : "item", id);
      return legacy;
      //--endversion
      //--version 1.21.1
      JsonObject legacy = new JsonObject();
      legacy.addProperty(isTag ? "tag" : "item", id);
      return legacy;
      //--endversion
      //--version 26.1
      return new JsonPrimitive(isTag ? "#" + id : id);
      //--endversion
   }

   private static String validateRecipeIngredientId(String id, String field) {
      try {
         return MLuaResourceLocations.parse(id).toString();
      } catch (RuntimeException invalid) {
         throw new LuaError("[MLua] " + field + " contains an invalid resource identifier '" + id + "'");
      }
   }

   private static JsonObject recipeResult(LuaValue value) {
      JsonElement element;
      if (value.isstring()) element = new JsonPrimitive(value.tojstring());
      else element = MLuaLuaJson.fromLua(value);
      if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
         JsonObject result = new JsonObject();
         result.addProperty("item", element.getAsString());
         element = result;
      }
      if (!element.isJsonObject()) throw new LuaError("[MLua] result must be an item id or a result table");
      JsonObject result = element.getAsJsonObject();
      if (result.has("item") && result.has("id")) {
         throw new LuaError("[MLua] result must use only one of item or id");
      }
      String itemId = null;
      JsonElement itemValue = result.has("item") ? result.get("item") : result.get("id");
      if (itemValue != null && itemValue.isJsonPrimitive() && itemValue.getAsJsonPrimitive().isString()) {
         itemId = validateRecipeIngredientId(itemValue.getAsString().trim(), "result");
      }
      if (itemId == null) throw new LuaError("[MLua] result must contain an item or id resource identifier string");
      if ("minecraft:air".equals(itemId)) throw new LuaError("[MLua] result cannot be minecraft:air");
      result.remove("item");
      result.remove("id");
      JsonObject normalized = new JsonObject();
      normalized.addProperty("item", itemId);
      for (Map.Entry<String, JsonElement> entry : result.entrySet()) {
         if (!"count".equals(entry.getKey())) normalized.add(entry.getKey(), entry.getValue());
      }
      if (result.has("count")) {
         JsonElement count = result.get("count");
         if (!count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber()) {
            throw new LuaError("[MLua] result.count must be a whole number of at least 1");
         }
         double countValue = count.getAsDouble();
         if (!Double.isFinite(countValue) || countValue < 1 || countValue != Math.rint(countValue)
               || countValue > Integer.MAX_VALUE) {
            throw new LuaError("[MLua] result.count must be a whole number of at least 1");
         }
         normalized.addProperty("count", (int) countValue);
      }
      //--version 1.21.1
      normalized.add("id", normalized.remove("item"));
      //--endversion
      //--version 26.1
      normalized.add("id", normalized.remove("item"));
      //--endversion
      return normalized;
   }

}
