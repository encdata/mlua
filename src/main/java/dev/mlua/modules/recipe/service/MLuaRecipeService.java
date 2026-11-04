package dev.mlua.modules.recipe.service;

import com.google.gson.JsonObject;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.Optional;

@MLuaDescription("Creates and validates recipe definitions for registration through Registry.Recipes.")
public final class MLuaRecipeService extends MLuaReflectiveService {
    private final MLuaRecipeBuilderFactory builders;

    public MLuaRecipeService(MLuaService.Context context) {
        super(context);
        this.builders = new MLuaRecipeBuilderFactory(context.mlua().registration());
    }

    @Override
    public String name() {
        return "Recipe";
    }

    @MLuaDescription("Creates a builder for a shaped crafting recipe.")
    public LuaTable Shaped(int rows, String output, Optional<Integer> requestedCount) {
        return this.builders.shaped(rows, output, requestedCount.orElse(1));
    }

    @MLuaDescription("Creates a builder for a shapeless crafting recipe.")
    public LuaTable Shapeless(String output, Optional<Integer> requestedCount) {
        return this.builders.shapeless(output, requestedCount.orElse(1));
    }

    @MLuaDescription("Creates a furnace recipe definition.")
    public String Smelting(String input, String output, Optional<Double> requestedExperience,
                       Optional<Integer> requestedCookTime) {
        return cookingRecipe("minecraft:smelting", input, output, requestedExperience, requestedCookTime, 200);
    }

    @MLuaDescription("Creates a blast furnace recipe definition.")
    public String Blasting(String input, String output, Optional<Double> requestedExperience,
                       Optional<Integer> requestedCookTime) {
        return cookingRecipe("minecraft:blasting", input, output, requestedExperience, requestedCookTime, 100);
    }

    @MLuaDescription("Creates a smoker recipe definition.")
    public String Smoking(String input, String output, Optional<Double> requestedExperience,
                      Optional<Integer> requestedCookTime) {
        return cookingRecipe("minecraft:smoking", input, output, requestedExperience, requestedCookTime, 100);
    }

    @MLuaDescription("Creates a campfire cooking recipe definition.")
    public String CampfireCooking(String input, String output, Optional<Double> requestedExperience,
                              Optional<Integer> requestedCookTime) {
        return cookingRecipe("minecraft:campfire_cooking", input, output, requestedExperience, requestedCookTime, 600);
    }

    @MLuaDescription("Creates a stonecutting recipe definition.")
    public String Stonecutting(String input, String output, Optional<Integer> requestedCount) {
        int count = requestedCount.orElse(1);
        MLuaResourceLocations.parse(input);
        MLuaResourceLocations.parse(output);
        if (count < 1) throw new LuaError("[MLua] Stonecutting count must be at least 1");

        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", input);
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:stonecutting");
        recipe.add("ingredient", ingredient);
        //--version 1.20.1
        recipe.addProperty("result", output);
        recipe.addProperty("count", count);
        //--endversion
        //--version 1.21.1
        recipe.add("result", MLuaRecipeDefinitionJson.itemStackResult(output, count));
        //--endversion
        //--version 26.1
        recipe.add("result", MLuaRecipeDefinitionJson.itemStackResult(output, count));
        //--endversion
        return recipe.toString();
    }

    private static String cookingRecipe(String type, String input, String output,
                                        Optional<Double> requestedExperience,
                                        Optional<Integer> requestedCookTime, int defaultCookTime) {
        double experience = requestedExperience.orElse(0.35);
        int cookTime = requestedCookTime.orElse(defaultCookTime);
        MLuaResourceLocations.parse(input);
        MLuaResourceLocations.parse(output);
        if (!Double.isFinite(experience) || experience < 0) {
            throw new LuaError("[MLua] Cooking recipe experience must be finite and non-negative");
        }
        if (cookTime < 1) throw new LuaError("[MLua] Cooking recipe cook time must be at least 1");

        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", input);
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);
        recipe.add("ingredient", ingredient);
        //--version 1.20.1
        recipe.addProperty("result", output);
        //--endversion
        //--version 1.21.1
        JsonObject cookingResult = new JsonObject();
        cookingResult.addProperty("id", output);
        recipe.add("result", cookingResult);
        //--endversion
        //--version 26.1
        recipe.add("result", MLuaRecipeDefinitionJson.itemStackResult(output, 1));
        //--endversion
        recipe.addProperty("experience", experience);
        recipe.addProperty("cookingtime", cookTime);
        return recipe.toString();
    }

}
