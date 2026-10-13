package dev.mlua.modules.recipe;

import dev.mlua.modules.recipe.Service.MLuaRecipeService;
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
            .description("Service for creating recipe builders.");
        recipe.function("Shaped", "Creates a builder for a shaped crafting recipe.")
            .function("Shapeless", "Creates a builder for a shapeless crafting recipe.")
            .function("Smelting", "Creates a builder for a furnace recipe.")
            .function("Stonecutting", "Creates a builder for a stonecutting recipe.");
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

        registration.registerClass("ShapelessRecipeBuilder", "RecipeBuilder",
            "A builder for a shapeless crafting recipe with up to nine ingredients.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapelessRecipeBuilder", "AddIngredient",
            "Adds an item identifier as a recipe ingredient.");
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Recipe", "MLuaRecipeService", "Service for creating recipe builders.", "services/recipe.html");
        docs.clazz("RecipeBuilder", "MLuaRecipeService", "Base class for recipe builders that validate and serialize recipe data.", "classes/recipe-builder.html", "Instance");
        docs.method("RecipeBuilder", "RecipeBuilder:Build(): string", "Validates the builder and returns the recipe JSON.", null, "string", "Shared", null);
        docs.clazz("ShapedRecipeBuilder", "MLuaRecipeService", "A builder for a shaped crafting recipe with a pattern and ingredient keys.", "classes/shaped-recipe-builder.html", "RecipeBuilder");
        docs.method("ShapedRecipeBuilder", "ShapedRecipeBuilder:Row(row: string, ...): ShapedRecipeBuilder", "Adds one or more rows to the shaped recipe pattern.", "one or more equally sized pattern rows", "ShapedRecipeBuilder", "Shared", null);
        docs.method("ShapedRecipeBuilder", "ShapedRecipeBuilder:WithKey(symbol: string, itemId: string): ShapedRecipeBuilder", "Maps one pattern character to an item identifier.", "a single pattern character and an item identifier", "ShapedRecipeBuilder", "Shared", null);
        docs.clazz("ShapelessRecipeBuilder", "MLuaRecipeService", "A builder for a shapeless crafting recipe with up to nine ingredients.", "classes/shapeless-recipe-builder.html", "RecipeBuilder");
        docs.method("ShapelessRecipeBuilder", "ShapelessRecipeBuilder:AddIngredient(itemId: string): ShapelessRecipeBuilder", "Adds an item identifier as a recipe ingredient.", "an item identifier", "ShapelessRecipeBuilder", "Shared", null);
        docs.signature("Recipe", "Recipe:Shaped(rows: number, result: string, count?: number)", "ShapedRecipeBuilder");
        docs.signature("Recipe", "Recipe:Shapeless(result: string, count?: number)", "ShapelessRecipeBuilder");
        docs.type("RecipeDefinition", null, "Options for a shaped or shapeless crafting recipe.");
        docs.field("RecipeDefinition", "result", "string", "Item identifier and optional count produced by the recipe.");
        docs.field("RecipeDefinition", "pattern?", "string[]", "Crafting grid rows for a shaped recipe.");
        docs.field("RecipeDefinition", "keys?", "table<string, string>", "Pattern character to ingredient identifier mapping.");
        docs.field("RecipeDefinition", "ingredients?", "string[]", "Ingredient identifiers for a shapeless recipe.");
        docs.field("RecipeDefinition", "group?", "string", "Optional recipe book grouping key.");
    }
}
