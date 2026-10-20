package dev.mlua.modules.registry.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaLuaArguments;

/** Installs generated recipe, loot, tag, and block-state data endpoints. */
final class MLuaRegistryDataApi {
   private MLuaRegistryDataApi() { }

   static void install(MLuaRegistryService service, LuaTable recipes, LuaTable loot, LuaTable tags, LuaTable blocks) {
      recipes.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = service.requiredLuaString(args, 2, "Registry.Recipes:Register", "id");
            service.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name, MLuaLuaJson.objectArg(args.arg(3), "recipe"));
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      recipes.set("RegisterShaped", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = service.requiredLuaString(args, 2, "Registry.Recipes:RegisterShaped", "id");
            service.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name, MLuaRecipeJson.shapedRecipe(args.arg(3)));
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      recipes.set("RegisterShapeless", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = service.requiredLuaString(args, 2, "Registry.Recipes:RegisterShapeless", "id");
            service.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name, MLuaRecipeJson.shapelessRecipe(args.arg(3)));
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      recipes.set("RegisterSmelting", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = service.requiredLuaString(args, 2, "Registry.Recipes:RegisterSmelting", "id");
            service.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name,
               MLuaRecipeJson.cookingRecipe("minecraft:smelting", args.arg(3), "Registry.Recipes:RegisterSmelting"));
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      recipes.set("RegisterCooking", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = service.requiredLuaString(args, 2, "Registry.Recipes:RegisterCooking", "id");
            String type = MLuaRecipeJson.cookingRecipeType(args.arg(3));
            service.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name,
               MLuaRecipeJson.cookingRecipe(type, args.arg(4), "Registry.Recipes:RegisterCooking"));
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      recipes.set("RegisterStonecutting", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = service.requiredLuaString(args, 2, "Registry.Recipes:RegisterStonecutting", "id");
            service.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name, MLuaRecipeJson.stonecuttingRecipe(args.arg(3)));
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      recipes.set("RegisterSmithingTransform", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = service.requiredLuaString(args, 2, "Registry.Recipes:RegisterSmithingTransform", "id");
            service.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name,
               MLuaRecipeJson.smithingTransformRecipe(args.arg(3)));
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      recipes.set("RegisterSmithingTrim", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = service.requiredLuaString(args, 2, "Registry.Recipes:RegisterSmithingTrim", "id");
            service.registerGeneratedAsset(GeneratedAssetKind.RECIPE, owner, name,
               MLuaRecipeJson.smithingTrimRecipe(args.arg(3)));
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      loot.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = service.requiredLuaString(args, 2, "Registry.Loot:Register", "id");
            service.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, name, MLuaLuaJson.objectArg(args.arg(3), "loot table"));
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      loot.set("RegisterEntity", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String entityName = service.requiredLuaString(args, 2, "Registry.Loot:RegisterEntity", "entity id");
            String entityId = entityName.contains(":") ? entityName : owner + ":" + entityName;
            var parsedEntityId = MLuaResourceLocations.tryParseOrNull(entityId);
            if (parsedEntityId == null || !parsedEntityId.toString().equals(entityId)) {
               throw new LuaError("[MLua] Registry.Loot:RegisterEntity entity must be a valid lowercase namespaced id.");
            }
            String lootName = "entities/" + MLuaResourceLocations.path(parsedEntityId);
            service.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, lootName, MLuaLuaJson.objectArg(args.arg(3), "entity loot table"));
            return LuaValue.valueOf(service.generatedAssetId(owner, lootName));
         }
      });
      blocks.set("RegisterState", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String name = service.requiredLuaString(args, 2, "Registry.Blocks:RegisterState", "id");
            service.registerGeneratedAsset(GeneratedAssetKind.BLOCKSTATE, owner, name, MLuaLuaJson.objectArg(args.arg(3), "blockstate"));
            return LuaValue.valueOf(service.generatedAssetId(owner, name));
         }
      });
      loot.set("RegisterSimpleBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String blockName = service.requiredLuaString(args, 2, "Registry.Loot:RegisterSimpleBlock", "block id");
            String dropId = args.narg() >= 3 && !args.arg(3).isnil()
               ? service.requiredLuaString(args, 3, "Registry.Loot:RegisterSimpleBlock", "item id")
               : service.qualifyOwnBlock(blockName);
            String lootName = blockName.contains(":") ? "blocks/" + blockName.substring(blockName.indexOf(58) + 1) : "blocks/" + blockName;
            service.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, lootName, MLuaLootJson.simpleBlockLoot(dropId));
            return LuaValue.valueOf(service.generatedAssetId(owner, lootName));
         }
      });
      loot.set("RegisterSelfBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String blockName = service.requiredLuaString(args, 2, "Registry.Loot:RegisterSelfBlock", "block id");
            String lootName = blockName.contains(":") ? "blocks/" + blockName.substring(blockName.indexOf(58) + 1) : "blocks/" + blockName;
            service.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, lootName, MLuaLootJson.simpleBlockLoot(service.qualifyOwnBlock(blockName)));
            return LuaValue.valueOf(service.generatedAssetId(owner, lootName));
         }
      });
      loot.set("RegisterSilkTouchBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String blockName = service.requiredLuaString(args, 2, "Registry.Loot:RegisterSilkTouchBlock", "block id");
            String normalDrop = service.requiredLuaString(args, 3, "Registry.Loot:RegisterSilkTouchBlock", "item id");
            int count = MLuaLuaArguments.optionalWholeInt(args, 4, 1,
               "Registry.Loot:RegisterSilkTouchBlock", "count");
            if (count < 1) {
               throw new LuaError("[MLua] Registry.Loot:RegisterSilkTouchBlock count must be at least one.");
            }
            String lootName = blockName.contains(":") ? "blocks/" + blockName.substring(blockName.indexOf(58) + 1) : "blocks/" + blockName;
            service.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, lootName, MLuaLootJson.silkTouchBlockLoot(service.qualifyOwnBlock(blockName), normalDrop, count));
            return LuaValue.valueOf(service.generatedAssetId(owner, lootName));
         }
      });
      loot.set("RegisterOreBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String owner = service.apiContext().mlua().ownerModId();
            String blockName = service.requiredLuaString(args, 2, "Registry.Loot:RegisterOreBlock", "block id");
            String dropId = service.requiredLuaString(args, 3, "Registry.Loot:RegisterOreBlock", "item id");
            int minCount = MLuaLuaArguments.optionalWholeInt(args, 4, 1,
               "Registry.Loot:RegisterOreBlock", "minCount");
            int maxCount = MLuaLuaArguments.optionalWholeInt(args, 5, minCount,
               "Registry.Loot:RegisterOreBlock", "maxCount");
            if (minCount < 1 || maxCount < minCount) {
               throw new LuaError("[MLua] Registry.Loot:RegisterOreBlock requires minCount >= 1 and maxCount >= minCount.");
            }
            String lootName = blockName.contains(":") ? "blocks/" + blockName.substring(blockName.indexOf(58) + 1) : "blocks/" + blockName;
            service.registerGeneratedAsset(GeneratedAssetKind.LOOT_TABLE, owner, lootName, MLuaLootJson.oreBlockLoot(service.qualifyOwnBlock(blockName), dropId, minCount, maxCount));
            return LuaValue.valueOf(service.generatedAssetId(owner, lootName));
         }
      });
      tags.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            return LuaValue.valueOf(service.registerTag(
               service.requiredLuaString(args, 2, "Registry.Tags:Register", "kind"),
               service.requiredLuaString(args, 3, "Registry.Tags:Register", "id"), args.arg(4)));
         }
      });
      tags.set("RegisterItem", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            return LuaValue.valueOf(service.registerTag("items",
               service.requiredLuaString(args, 2, "Registry.Tags:RegisterItem", "id"), args.arg(3)));
         }
      });
      tags.set("AddToItem", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            return LuaValue.valueOf(service.appendTagValues("items",
               service.requiredLuaString(args, 2, "Registry.Tags:AddToItem", "id"), args.arg(3)));
         }
      });
      tags.set("RegisterBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            return LuaValue.valueOf(service.registerTag("blocks",
               service.requiredLuaString(args, 2, "Registry.Tags:RegisterBlock", "id"), args.arg(3)));
         }
      });
      tags.set("AddToBlock", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            return LuaValue.valueOf(service.appendTagValues("blocks",
               service.requiredLuaString(args, 2, "Registry.Tags:AddToBlock", "id"), args.arg(3)));
         }
      });
      tags.set("RegisterEntity", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            return LuaValue.valueOf(service.registerTag("entity_types",
               service.requiredLuaString(args, 2, "Registry.Tags:RegisterEntity", "id"), args.arg(3)));
         }
      });
      tags.set("AddToEntity", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            return LuaValue.valueOf(service.appendTagValues("entity_types",
               service.requiredLuaString(args, 2, "Registry.Tags:AddToEntity", "id"), args.arg(3)));
         }
      });
   }
}
