package dev.mlua.modules.recipe.service;

import com.google.gson.JsonObject;

/** Creates version-shaped JSON fragments shared by recipe builders and cooking helpers. */
final class MLuaRecipeDefinitionJson {
    private MLuaRecipeDefinitionJson() { }

    static JsonObject itemStackResult(String itemId, int count) {
        JsonObject result = new JsonObject();
        //--version 1.20.1
        result.addProperty("item", itemId);
        //--endversion
        //--version 1.21.1
        result.addProperty("id", itemId);
        //--endversion
        //--version 26.1
        result.addProperty("id", itemId);
        //--endversion
        result.addProperty("count", count);
        return result;
    }

    static JsonObject ingredient(String key, String id) {
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty(key, id);
        return ingredient;
    }
}
