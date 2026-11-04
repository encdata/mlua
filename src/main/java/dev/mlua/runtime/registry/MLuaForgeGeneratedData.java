package dev.mlua.runtime.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.loaderapi.GeneratedAssetKind;
import java.util.HashMap;
import java.util.Map;

/** Owns generated data-pack definitions and their compare-before-rollback lifecycle on Forge. */
final class MLuaForgeGeneratedData {
    private final Map<String, JsonObject> recipes = new HashMap<>();
    private final Map<String, JsonObject> lootTables = new HashMap<>();
    private final Map<String, JsonObject> blockStates = new HashMap<>();
    private final Map<String, JsonObject> itemTags = new HashMap<>();
    private final Map<String, JsonObject> blockTags = new HashMap<>();
    private final Map<String, JsonObject> entityTypeTags = new HashMap<>();

    void registerRecipe(String id, JsonObject definition) {
        putUnique(recipes, "recipe", id, definition.deepCopy());
    }

    void registerLootTable(String id, JsonObject definition) {
        putUnique(lootTables, "loot table", id, definition.deepCopy());
    }

    void registerBlockState(String id, JsonObject definition) {
        putUnique(blockStates, "blockstate", id, definition.deepCopy());
    }

    boolean unregister(GeneratedAssetKind kind, String id, JsonObject expectedDefinition) {
        if (kind == GeneratedAssetKind.ITEM_TAG || kind == GeneratedAssetKind.BLOCK_TAG
                || kind == GeneratedAssetKind.ENTITY_TYPE_TAG) {
            throw new IllegalArgumentException("Use tag rollback for tag assets.");
        }
        Map<String, JsonObject> assets = map(kind);
        JsonObject current = assets.get(id);
        return current != null && current.equals(expectedDefinition) && assets.remove(id, current);
    }

    boolean restore(GeneratedAssetKind kind, String id, JsonObject expectedCurrent,
                    JsonObject previousDefinition) {
        Map<String, JsonObject> assets = map(kind);
        JsonObject current = assets.get(id);
        if (current == null || !current.equals(expectedCurrent)) return false;
        if (previousDefinition == null) return assets.remove(id, current);
        assets.put(id, previousDefinition.deepCopy());
        return true;
    }

    void registerTag(GeneratedAssetKind kind, String id, boolean replace, JsonArray values) {
        JsonObject tag = tagMap(kind).computeIfAbsent(id, ignored -> {
            JsonObject created = new JsonObject();
            created.addProperty("replace", false);
            created.add("values", new JsonArray());
            return created;
        });
        if (replace) {
            JsonArray freshValues = new JsonArray();
            values.forEach(freshValues::add);
            tag.addProperty("replace", true);
            tag.add("values", freshValues);
        } else {
            JsonArray currentValues = tag.getAsJsonArray("values");
            values.forEach(currentValues::add);
        }
    }

    Map<String, JsonObject> recipes() { return recipes; }
    Map<String, JsonObject> lootTables() { return lootTables; }
    Map<String, JsonObject> blockStates() { return blockStates; }
    Map<String, JsonObject> itemTags() { return itemTags; }
    Map<String, JsonObject> blockTags() { return blockTags; }
    Map<String, JsonObject> entityTypeTags() { return entityTypeTags; }

    private Map<String, JsonObject> map(GeneratedAssetKind kind) {
        return switch (kind) {
            case RECIPE -> recipes;
            case LOOT_TABLE -> lootTables;
            case BLOCKSTATE -> blockStates;
            case ITEM_TAG -> itemTags;
            case BLOCK_TAG -> blockTags;
            case ENTITY_TYPE_TAG -> entityTypeTags;
        };
    }

    private Map<String, JsonObject> tagMap(GeneratedAssetKind kind) {
        return switch (kind) {
            case ITEM_TAG -> itemTags;
            case BLOCK_TAG -> blockTags;
            case ENTITY_TYPE_TAG -> entityTypeTags;
            default -> throw new IllegalArgumentException("Not a tag asset: " + kind);
        };
    }

    private static void putUnique(Map<String, JsonObject> destination, String type, String id,
                                  JsonObject definition) {
        if (destination.putIfAbsent(id, definition) != null) {
            throw new IllegalArgumentException("[MLua] Duplicate " + type + " registration: " + id);
        }
    }
}
