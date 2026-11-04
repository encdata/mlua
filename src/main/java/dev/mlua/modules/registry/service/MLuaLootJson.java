package dev.mlua.modules.registry.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Builds the vanilla loot table JSON used by convenience registry methods. */
final class MLuaLootJson {
   private MLuaLootJson() { }

   static JsonObject simpleBlockLoot(String dropId) {
      JsonObject loot = new JsonObject();
      loot.addProperty("type", "minecraft:block");
      JsonArray pools = new JsonArray();
      JsonObject pool = new JsonObject();
      pool.addProperty("rolls", 1);
      JsonArray entries = new JsonArray();
      JsonObject entry = new JsonObject();
      entry.addProperty("type", "minecraft:item");
      entry.addProperty("name", dropId);
      entries.add(entry);
      pool.add("entries", entries);
      pools.add(pool);
      loot.add("pools", pools);
      return loot;
   }

   static JsonObject silkTouchBlockLoot(String blockId, String normalDropId, int count) {
      JsonObject loot = new JsonObject();
      loot.addProperty("type", "minecraft:block");
      JsonArray pools = new JsonArray();
      JsonObject pool = new JsonObject();
      pool.addProperty("rolls", 1);
      JsonArray entries = new JsonArray();
      JsonObject alternatives = new JsonObject();
      alternatives.addProperty("type", "minecraft:alternatives");
      JsonArray children = new JsonArray();
      JsonObject silk = itemEntry(blockId, 1);
      silk.add("conditions", matchToolEnchantCondition("minecraft:silk_touch", 1));
      children.add(silk);
      children.add(itemEntry(normalDropId, count));
      alternatives.add("children", children);
      entries.add(alternatives);
      pool.add("entries", entries);
      pools.add(pool);
      loot.add("pools", pools);
      return loot;
   }

   static JsonObject oreBlockLoot(String blockId, String dropId, int minCount, int maxCount) {
      JsonObject loot = new JsonObject();
      loot.addProperty("type", "minecraft:block");
      JsonArray pools = new JsonArray();
      JsonObject pool = new JsonObject();
      pool.addProperty("rolls", 1);
      JsonArray entries = new JsonArray();
      JsonObject alternatives = new JsonObject();
      alternatives.addProperty("type", "minecraft:alternatives");
      JsonArray children = new JsonArray();
      JsonObject silk = itemEntry(blockId, 1);
      silk.add("conditions", matchToolEnchantCondition("minecraft:silk_touch", 1));
      children.add(silk);
      JsonObject normal = itemEntry(dropId, 1);
      JsonArray functions = new JsonArray();
      JsonObject setCount = new JsonObject();
      setCount.addProperty("function", "minecraft:set_count");
      JsonObject count = new JsonObject();
      count.addProperty("min", minCount);
      count.addProperty("max", maxCount);
      setCount.add("count", count);
      functions.add(setCount);
      JsonObject bonus = new JsonObject();
      bonus.addProperty("function", "minecraft:apply_bonus");
      bonus.addProperty("enchantment", "minecraft:fortune");
      bonus.addProperty("formula", "minecraft:ore_drops");
      functions.add(bonus);
      normal.add("functions", functions);
      children.add(normal);
      alternatives.add("children", children);
      entries.add(alternatives);
      pool.add("entries", entries);
      pools.add(pool);
      loot.add("pools", pools);
      return loot;
   }

   private static JsonObject itemEntry(String itemId, int count) {
      JsonObject entry = new JsonObject();
      entry.addProperty("type", "minecraft:item");
      entry.addProperty("name", itemId);
      if (count != 1) {
         JsonArray functions = new JsonArray();
         JsonObject setCount = new JsonObject();
         setCount.addProperty("function", "minecraft:set_count");
         setCount.addProperty("count", count);
         functions.add(setCount);
         entry.add("functions", functions);
      }
      return entry;
   }

   private static JsonArray matchToolEnchantCondition(String enchantment, int minLevel) {
      JsonArray conditions = new JsonArray();
      JsonObject matchTool = new JsonObject();
      matchTool.addProperty("condition", "minecraft:match_tool");
      JsonObject predicate = new JsonObject();
      JsonArray enchantments = new JsonArray();
      JsonObject enchantmentEntry = new JsonObject();
      enchantmentEntry.addProperty("enchantment", enchantment);
      JsonObject levels = new JsonObject();
      levels.addProperty("min", minLevel);
      enchantmentEntry.add("levels", levels);
      enchantments.add(enchantmentEntry);
      predicate.add("enchantments", enchantments);
      matchTool.add("predicate", predicate);
      conditions.add(matchTool);
      return conditions;
   }

}
