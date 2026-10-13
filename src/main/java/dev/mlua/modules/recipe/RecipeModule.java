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
        registration.registerClass("ShapedRecipeBuilder", registration.requireInstanceBaseClassName(),
            "A builder for a shaped crafting recipe with a pattern and ingredient keys.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapedRecipeBuilder", "Row",
            "Adds one or more rows to the shaped recipe pattern.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapedRecipeBuilder", "WithKey",
            "Maps one pattern character to an item identifier.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapedRecipeBuilder", "Build",
            "Validates and serializes the shaped recipe.");

        registration.registerClass("ShapelessRecipeBuilder", registration.requireInstanceBaseClassName(),
            "A builder for a shapeless crafting recipe with up to nine ingredients.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapelessRecipeBuilder", "AddIngredient",
            "Adds an item identifier as a recipe ingredient.");
        MLuaClassMethods.registerInstanceMethod(registration, "ShapelessRecipeBuilder", "Build",
            "Validates and serializes the shapeless recipe.");
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Recipe", "MLuaRecipeService", "Service for creating recipe builders.", "services/recipe.html");
        docs.type("RecipeDefinition", null, "Options for a shaped or shapeless crafting recipe.");
        docs.field("RecipeDefinition", "result", "string", "Item identifier and optional count produced by the recipe.");
        docs.field("RecipeDefinition", "pattern?", "string[]", "Crafting grid rows for a shaped recipe.");
        docs.field("RecipeDefinition", "keys?", "table<string, string>", "Pattern character to ingredient identifier mapping.");
        docs.field("RecipeDefinition", "ingredients?", "string[]", "Ingredient identifiers for a shapeless recipe.");
        docs.field("RecipeDefinition", "group?", "string", "Optional recipe book grouping key.");
    }
}
