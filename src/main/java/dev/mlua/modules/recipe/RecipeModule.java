package dev.mlua.modules.recipe;

import dev.mlua.modules.recipe.service.MLuaRecipeService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import java.util.List;
import java.util.Map;

public class RecipeModule implements MLuaModule {
    @Override
    public String id() { return "recipe"; }

    @Override
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public int priority() { return 12; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder recipe = ServiceEntry.builder("Recipe", MLuaRecipeService::new)
            .reflective(MLuaRecipeService.class);
        return List.of(recipe.build());
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("RecipeBuilder", registration.requireInstanceBaseClassName(),
            "Base class for recipe builders that validate and serialize recipe data.");
        MLuaClassMethods.registerInstanceMethod(registration, "RecipeBuilder", "Build",
            "Validates and serializes the recipe definition.");

        registration.registerClass("ShapedRecipeBuilder", "RecipeBuilder",
            "A builder for a shaped crafting recipe with a pattern and ingredient keys.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapedRecipeBuilder", "Row",
            "Adds one or more rows to the shaped recipe pattern.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapedRecipeBuilder", "WithKey",
            "Maps one pattern character to an item identifier.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapedRecipeBuilder", "WithTagKey",
            "Maps one pattern character to an item tag identifier.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapedRecipeBuilder", "SetGroup",
            "Sets an optional recipe book group.");

        registration.registerClass("ShapelessRecipeBuilder", "RecipeBuilder",
            "A builder for a shapeless crafting recipe with up to nine ingredients.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapelessRecipeBuilder", "AddIngredient",
            "Adds an item identifier as a recipe ingredient.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapelessRecipeBuilder", "AddTagIngredient",
            "Adds an item tag identifier as a recipe ingredient.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapelessRecipeBuilder", "SetGroup",
            "Sets an optional recipe book group.");
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Recipe", "MLuaRecipeService", "Creates and validates recipe definitions for registration through Registry.Recipes.", "services/recipe.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("RecipeBuilder", "MLuaRecipeService", "Base class for recipe builders that validate and serialize recipe data.", "classes/recipe-builder.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.method("RecipeBuilder", "RecipeBuilder:Build(): string", "Validates the builder and returns the recipe JSON.", null, "string", "Shared", null));
        docs.register(MLuaDocBuilder.Registration.clazz("ShapedRecipeBuilder", "MLuaRecipeService", "A builder for a shaped crafting recipe with a pattern and ingredient keys.", "classes/shaped-recipe-builder.html", "RecipeBuilder"));
        docs.register(MLuaDocBuilder.Registration.method("ShapedRecipeBuilder", "ShapedRecipeBuilder:Row(row: string, ...): ShapedRecipeBuilder", "Adds one or more rows to the shaped recipe pattern.", "one or more equally sized pattern rows", "ShapedRecipeBuilder", "Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("ShapedRecipeBuilder", "ShapedRecipeBuilder:WithKey(symbol: string, itemId: string): ShapedRecipeBuilder", "Maps one pattern character to an item identifier.", "a single pattern character and an item identifier", "ShapedRecipeBuilder", "Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("ShapedRecipeBuilder", "ShapedRecipeBuilder:WithTagKey(symbol: string, tagId: string): ShapedRecipeBuilder", "Maps one pattern character to an item tag identifier.", "a single pattern character and an item tag identifier without a leading #", "ShapedRecipeBuilder", "Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("ShapedRecipeBuilder", "ShapedRecipeBuilder:SetGroup(group: string): ShapedRecipeBuilder", "Sets the optional recipe book group. An empty string clears the group.", "recipe book group name", "ShapedRecipeBuilder", "Shared", null));
        docs.register(MLuaDocBuilder.Registration.clazz("ShapelessRecipeBuilder", "MLuaRecipeService", "A builder for a shapeless crafting recipe with up to nine ingredients.", "classes/shapeless-recipe-builder.html", "RecipeBuilder"));
        docs.register(MLuaDocBuilder.Registration.method("ShapelessRecipeBuilder", "ShapelessRecipeBuilder:AddIngredient(itemId: string): ShapelessRecipeBuilder", "Adds an item identifier as a recipe ingredient.", "an item identifier", "ShapelessRecipeBuilder", "Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("ShapelessRecipeBuilder", "ShapelessRecipeBuilder:AddTagIngredient(tagId: string): ShapelessRecipeBuilder", "Adds an item tag identifier as a recipe ingredient.", "an item tag identifier without a leading #", "ShapelessRecipeBuilder", "Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("ShapelessRecipeBuilder", "ShapelessRecipeBuilder:SetGroup(group: string): ShapelessRecipeBuilder", "Sets the optional recipe book group. An empty string clears the group.", "recipe book group name", "ShapelessRecipeBuilder", "Shared", null));
        docs.register(MLuaDocBuilder.Registration.signature("Recipe", "Recipe:Shaped(rows: number, result: string, count?: number)", "ShapedRecipeBuilder"));
        docs.register(MLuaDocBuilder.Registration.signature("Recipe", "Recipe:Shapeless(result: string, count?: number)", "ShapelessRecipeBuilder"));
        docs.register(MLuaDocBuilder.Registration.signature("Recipe", "Recipe:Smelting(input: string, output: string, experience?: number, cookTime?: number)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Recipe", "Recipe:Blasting(input: string, output: string, experience?: number, cookTime?: number)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Recipe", "Recipe:Smoking(input: string, output: string, experience?: number, cookTime?: number)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Recipe", "Recipe:CampfireCooking(input: string, output: string, experience?: number, cookTime?: number)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Recipe", "Recipe:Stonecutting(input: string, output: string, count?: number)", "string"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Recipe", "Build and register a shaped recipe",
            "Recipe builders validate their pattern and ingredient keys, then Build returns a JSON definition accepted by Registry.Recipes:Register. SetGroup is optional and assigns the recipe book group. Put both calls in a .registry.lua script so the recipe is available when the generated data pack loads.",
            "local Recipe = Minecraft:GetService(\"Recipe\")\nlocal Recipes = Minecraft:GetService(\"Registry\").Recipes\n\nlocal definition = Recipe:Shaped(2, \"myaddon:signal_flare\")\n    :Row(\" I \", \"ISI\")\n    :WithTagKey(\"I\", \"minecraft:ingots/iron\")\n    :WithKey(\"S\", \"minecraft:stick\")\n    :Build()\n\nRecipes:Register(\"signal_flare\", definition)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Recipe", "Create cooking and stonecutting recipes",
            "Smelting, blasting, smoking, and campfire cooking share the same arguments. Their default cook times match the vanilla station. Each method returns a version-shaped JSON definition for Registry.Recipes:Register.",
            "local Recipe = Minecraft:GetService(\"Recipe\")\nlocal Recipes = Minecraft:GetService(\"Registry\").Recipes\n\nRecipes:Register(\"iron_from_ore\", Recipe:Smelting(\"minecraft:raw_iron\", \"minecraft:iron_ingot\"))\nRecipes:Register(\"gold_from_ore\", Recipe:Blasting(\"minecraft:raw_gold\", \"minecraft:gold_ingot\"))\nRecipes:Register(\"cooked_beef\", Recipe:Smoking(\"minecraft:beef\", \"minecraft:cooked_beef\"))\nRecipes:Register(\"charcoal\", Recipe:CampfireCooking(\"minecraft:oak_log\", \"minecraft:charcoal\"))\nRecipes:Register(\"stone_slab\", Recipe:Stonecutting(\"minecraft:stone\", \"minecraft:stone_slab\", 2))"));
        docs.register(MLuaDocBuilder.Registration.type("RecipeDefinition", null, "Options for a shaped or shapeless crafting recipe."));
        docs.register(MLuaDocBuilder.Registration.field("RecipeDefinition", "result", "string", "Item identifier and optional count produced by the recipe."));
        docs.register(MLuaDocBuilder.Registration.field("RecipeDefinition", "pattern?", "string[]", "Crafting grid rows for a shaped recipe."));
        docs.register(MLuaDocBuilder.Registration.field("RecipeDefinition", "keys?", "table<string, string>", "Pattern character to ingredient identifier mapping."));
        docs.register(MLuaDocBuilder.Registration.field("RecipeDefinition", "ingredients?", "string[]", "Ingredient identifiers for a shapeless recipe."));
        docs.register(MLuaDocBuilder.Registration.field("RecipeDefinition", "group?", "string", "Optional recipe book grouping key."));
    }
}
