package dev.mlua.modules.recipe.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Constructs stateful Lua builders for shaped and shapeless recipe definitions. */
final class MLuaRecipeBuilderFactory {
    private final MLuaRegistration registration;

    MLuaRecipeBuilderFactory(MLuaRegistration registration) {
        this.registration = registration;
    }

    LuaTable shaped(int rows, String output, int count) {
        MLuaResourceLocations.parse(output);
        if (rows < 1 || rows > 3) throw new LuaError("[MLua] Shaped recipe row count must be 1-3");
        if (count < 1) throw new LuaError("[MLua] Shaped recipe count must be at least 1");

        final String[] pattern = new String[rows];
        final int[] nextRow = {0};
        final String[] group = {null};
        final Map<String, JsonObject> keyMap = new LinkedHashMap<>();

        LuaTable builder = new LuaTable();
        attachBuilder(builder, "ShapedRecipeBuilder");

        MLuaClassMethods.setInstanceMethod(builder, "Row", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                if (args.narg() < 2) throw new LuaError("[MLua] Shaped:Row requires at least one row");
                if (nextRow[0] + args.narg() - 1 > pattern.length) {
                    throw new LuaError("[MLua] Too many Shaped pattern rows");
                }
                int expectedWidth = nextRow[0] == 0 ? -1 : pattern[0].length();
                List<String> newRows = new ArrayList<>();
                for (int argument = 2; argument <= args.narg(); argument++) {
                    String row = args.checkjstring(argument);
                    if (row.isEmpty() || row.length() > 3) {
                        throw new LuaError("[MLua] Shaped pattern rows must contain 1-3 characters");
                    }
                    if (expectedWidth >= 0 && row.length() != expectedWidth) {
                        throw new LuaError("[MLua] All Shaped pattern rows must have the same length");
                    }
                    expectedWidth = row.length();
                    newRows.add(row);
                }
                for (String row : newRows) {
                    pattern[nextRow[0]++] = row;
                }
                return builder;
            }
        });

        MLuaClassMethods.setInstanceMethod(builder, "WithKey", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String ch = args.checkjstring(2);
                String itemId = args.checkjstring(3);
                MLuaResourceLocations.parse(itemId);
                if (ch.length() != 1) throw new LuaError("[MLua] Shaped recipe key must be a single character");
                if (" ".equals(ch)) throw new LuaError("[MLua] Space is reserved for an empty Shaped slot");
                keyMap.put(ch, MLuaRecipeDefinitionJson.ingredient("item", itemId));
                return builder;
            }
        });

        MLuaClassMethods.setInstanceMethod(builder, "WithTagKey", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String ch = args.checkjstring(2);
                String tagId = args.checkjstring(3);
                MLuaResourceLocations.parse(tagId);
                if (ch.length() != 1) throw new LuaError("[MLua] Shaped recipe key must be a single character");
                if (" ".equals(ch)) throw new LuaError("[MLua] Space is reserved for an empty Shaped slot");
                keyMap.put(ch, MLuaRecipeDefinitionJson.ingredient("tag", tagId));
                return builder;
            }
        });

        MLuaClassMethods.setInstanceMethod(builder, "SetGroup", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String value = args.checkjstring(2);
                group[0] = value.isBlank() ? null : value;
                return builder;
            }
        });

        MLuaClassMethods.setInstanceMethod(builder, "Build", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                for (int i = 0; i < pattern.length; i++) {
                    if (pattern[i] == null) {
                        throw new LuaError("[MLua] Shaped recipe row " + (i + 1) + " not set");
                    }
                }
                JsonArray rows = new JsonArray();
                Set<String> usedKeys = new LinkedHashSet<>();
                for (String row : pattern) {
                    rows.add(row);
                    for (int index = 0; index < row.length(); index++) {
                        String symbol = String.valueOf(row.charAt(index));
                        if (!" ".equals(symbol) && !keyMap.containsKey(symbol)) {
                            throw new LuaError("[MLua] Shaped pattern has no key for '" + symbol + "'");
                        }
                        if (!" ".equals(symbol)) usedKeys.add(symbol);
                    }
                }
                if (usedKeys.isEmpty()) throw new LuaError("[MLua] Shaped pattern must contain an ingredient");
                for (String symbol : keyMap.keySet()) {
                    if (!usedKeys.contains(symbol)) {
                        throw new LuaError("[MLua] Shaped key '" + symbol + "' is not used in the pattern");
                    }
                }
                JsonObject keys = new JsonObject();
                for (Map.Entry<String, JsonObject> entry : keyMap.entrySet()) {
                    keys.add(entry.getKey(), entry.getValue());
                }
                JsonObject recipe = new JsonObject();
                recipe.addProperty("type", "minecraft:crafting_shaped");
                recipe.add("pattern", rows);
                recipe.add("key", keys);
                recipe.add("result", MLuaRecipeDefinitionJson.itemStackResult(output, count));
                if (group[0] != null) recipe.addProperty("group", group[0]);
                return LuaValue.valueOf(recipe.toString());
            }
        });

        return builder;
    }

    LuaTable shapeless(String output, int count) {
        MLuaResourceLocations.parse(output);
        if (count < 1) throw new LuaError("[MLua] Shapeless recipe count must be at least 1");

        final List<JsonObject> ingredients = new ArrayList<>();
        final String[] group = {null};

        LuaTable builder = new LuaTable();
        attachBuilder(builder, "ShapelessRecipeBuilder");

        MLuaClassMethods.setInstanceMethod(builder, "AddIngredient", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String itemId = args.checkjstring(2);
                MLuaResourceLocations.parse(itemId);
                if (ingredients.size() >= 9) throw new LuaError("[MLua] Shapeless recipes allow at most 9 ingredients");
                ingredients.add(MLuaRecipeDefinitionJson.ingredient("item", itemId));
                return builder;
            }
        });

        MLuaClassMethods.setInstanceMethod(builder, "AddTagIngredient", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String tagId = args.checkjstring(2);
                MLuaResourceLocations.parse(tagId);
                if (ingredients.size() >= 9) throw new LuaError("[MLua] Shapeless recipes allow at most 9 ingredients");
                ingredients.add(MLuaRecipeDefinitionJson.ingredient("tag", tagId));
                return builder;
            }
        });

        MLuaClassMethods.setInstanceMethod(builder, "SetGroup", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String value = args.checkjstring(2);
                group[0] = value.isBlank() ? null : value;
                return builder;
            }
        });

        MLuaClassMethods.setInstanceMethod(builder, "Build", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                if (ingredients.isEmpty()) {
                    throw new LuaError("[MLua] Shapeless recipe must have at least one ingredient");
                }
                JsonArray entries = new JsonArray();
                for (JsonObject ingredient : ingredients) entries.add(ingredient);
                JsonObject recipe = new JsonObject();
                recipe.addProperty("type", "minecraft:crafting_shapeless");
                recipe.add("ingredients", entries);
                recipe.add("result", MLuaRecipeDefinitionJson.itemStackResult(output, count));
                if (group[0] != null) recipe.addProperty("group", group[0]);
                return LuaValue.valueOf(recipe.toString());
            }
        });

        return builder;
    }

    private void attachBuilder(LuaTable builder, String className) {
        if (this.registration == null || this.registration.classTable(className) == null) {
            throw new IllegalStateException("Lua class '" + className
                + "' must be registered before creating recipe builders");
        }
        this.registration.attachClassInstance(builder, className, null);
    }
}
