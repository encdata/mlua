package dev.mlua.runtime.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.loaderapi.GeneratedAssetKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaForgeGeneratedDataTest {
    @Test
    void storesDefinitionsByValueAndRollsBackOnlyTheExpectedVersion() {
        MLuaForgeGeneratedData data = new MLuaForgeGeneratedData();
        JsonObject original = new JsonObject();
        original.addProperty("value", 1);
        data.registerRecipe("addon:recipe", original);
        original.addProperty("value", 2);

        JsonObject stored = data.recipes().get("addon:recipe");
        assertEquals(1, stored.get("value").getAsInt());
        assertFalse(data.unregister(GeneratedAssetKind.RECIPE, "addon:recipe", original));
        assertTrue(data.unregister(GeneratedAssetKind.RECIPE, "addon:recipe", stored));
        assertFalse(data.recipes().containsKey("addon:recipe"));
        assertThrows(IllegalArgumentException.class,
                () -> data.unregister(GeneratedAssetKind.ITEM_TAG, "addon:tag", new JsonObject()));
    }

    @Test
    void appendsAndReplacesTagValuesWithoutChangingExistingSemantics() {
        MLuaForgeGeneratedData data = new MLuaForgeGeneratedData();
        JsonArray first = new JsonArray();
        first.add("minecraft:stone");
        data.registerTag(GeneratedAssetKind.BLOCK_TAG, "addon:blocks", false, first);

        JsonArray appended = new JsonArray();
        appended.add("minecraft:dirt");
        data.registerTag(GeneratedAssetKind.BLOCK_TAG, "addon:blocks", false, appended);
        JsonObject accumulated = data.blockTags().get("addon:blocks");
        assertEquals(2, accumulated.getAsJsonArray("values").size());
        assertFalse(accumulated.get("replace").getAsBoolean());

        JsonArray replacement = new JsonArray();
        replacement.add("minecraft:glass");
        data.registerTag(GeneratedAssetKind.BLOCK_TAG, "addon:blocks", true, replacement);
        JsonObject replaced = data.blockTags().get("addon:blocks");
        assertTrue(replaced.get("replace").getAsBoolean());
        assertEquals(1, replaced.getAsJsonArray("values").size());
        assertEquals("minecraft:glass", replaced.getAsJsonArray("values").get(0).getAsString());
    }
}
