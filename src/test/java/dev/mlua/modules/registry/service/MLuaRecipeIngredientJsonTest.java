package dev.mlua.modules.registry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

class MLuaRecipeIngredientJsonTest {
    @Test
    void encodesItemAndTagSelectorsInLegacyRecipeFormat() {
        JsonElement item = MLuaRecipeIngredientJson.single(LuaValue.valueOf("minecraft:stone"), "ingredient");
        JsonElement tag = MLuaRecipeIngredientJson.single(LuaValue.valueOf("#minecraft:logs"), "ingredient");

        assertEquals(JsonParser.parseString("{\"item\":\"minecraft:stone\"}"), item);
        assertEquals(JsonParser.parseString("{\"tag\":\"minecraft:logs\"}"), tag);
    }

    @Test
    void encodesAlternativeItemsAndRejectsInvalidAlternatives() {
        JsonElement alternatives = MLuaRecipeIngredientJson.normalize(
            JsonParser.parseString("{\"items\":[\"minecraft:stone\",\"minecraft:granite\"]}"), "ingredient");

        assertEquals(JsonParser.parseString("[{\"item\":\"minecraft:stone\"},{\"item\":\"minecraft:granite\"}]"), alternatives);
        assertThrows(LuaError.class, () -> MLuaRecipeIngredientJson.normalize(
            JsonParser.parseString("{\"items\":[\"minecraft:stone\",\"minecraft:stone\"]}"), "ingredient"));
        assertThrows(LuaError.class, () -> MLuaRecipeIngredientJson.normalize(
            JsonParser.parseString("{\"items\":[\"minecraft:air\"]}"), "ingredient"));
    }

    @Test
    void keepsAlternativeLimitSmallEnoughForSafeGeneratedRecipes() {
        assertEquals(256, MLuaRecipeIngredientJson.MAX_ALTERNATIVE_ITEMS);
        assertEquals("minecraft:stone", MLuaRecipeIngredientJson.validateId("minecraft:stone", "result"));
        assertThrows(LuaError.class, () -> MLuaRecipeIngredientJson.validateId("not a resource id", "result"));
        assertThrows(LuaError.class, () -> MLuaRecipeIngredientJson.normalize(
            JsonParser.parseString("{\"items\":[]}"), "ingredient"));
    }
}
