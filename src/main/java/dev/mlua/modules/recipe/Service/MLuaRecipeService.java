package dev.mlua.modules.recipe.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class MLuaRecipeService extends MLuaServiceBase {
    private final MLuaRegistration registration;

    public MLuaRecipeService(MLuaService.Context context) {
        super(context);
        this.registration = context.mlua().registration();
    }

    @Override
    public String name() {
        return "Recipe";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("__side", "Both");

        table.set("Shaped", fn(args -> {
            int rows = args.checkint(2);
            String output = args.checkjstring(3);
            int count = args.optint(4, 1);
            MLuaResourceLocations.parse(output);
            if (rows < 1 || rows > 3) throw new LuaError("[MLua] Shaped recipe row count must be 1-3");
            if (count < 1) throw new LuaError("[MLua] Shaped recipe count must be at least 1");

            final String[] pattern = new String[rows];
            final int[] nextRow = {0};
            final Map<String, String> keyMap = new LinkedHashMap<>();

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
                    keyMap.put(ch, itemId);
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
                    for (Map.Entry<String, String> entry : keyMap.entrySet()) {
                        JsonObject ingredient = new JsonObject();
                        ingredient.addProperty("item", entry.getValue());
                        keys.add(entry.getKey(), ingredient);
                    }
                    JsonObject recipe = new JsonObject();
                    recipe.addProperty("type", "minecraft:crafting_shaped");
                    recipe.add("pattern", rows);
                    recipe.add("key", keys);
                    recipe.add("result", itemStackResult(output, count));
                    return LuaValue.valueOf(recipe.toString());
                }
            });

            return builder;
        }));

        table.set("Shapeless", fn(args -> {
            String output = args.checkjstring(2);
            int count = args.optint(3, 1);
            MLuaResourceLocations.parse(output);
            if (count < 1) throw new LuaError("[MLua] Shapeless recipe count must be at least 1");

            final List<String> ingredients = new ArrayList<>();

            LuaTable builder = new LuaTable();
            attachBuilder(builder, "ShapelessRecipeBuilder");

            MLuaClassMethods.setInstanceMethod(builder, "AddIngredient", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    String itemId = args.checkjstring(2);
                    MLuaResourceLocations.parse(itemId);
                    if (ingredients.size() >= 9) throw new LuaError("[MLua] Shapeless recipes allow at most 9 ingredients");
                    ingredients.add(itemId);
                    return builder;
                }
            });

            MLuaClassMethods.setInstanceMethod(builder, "Build", new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    if (ingredients.isEmpty()) {
                        throw new LuaError("[MLua] Shapeless recipe must have at least one ingredient");
                    }
                    JsonArray entries = new JsonArray();
                    for (String itemId : ingredients) {
                        JsonObject ingredient = new JsonObject();
                        ingredient.addProperty("item", itemId);
                        entries.add(ingredient);
                    }
                    JsonObject recipe = new JsonObject();
                    recipe.addProperty("type", "minecraft:crafting_shapeless");
                    recipe.add("ingredients", entries);
                    recipe.add("result", itemStackResult(output, count));
                    return LuaValue.valueOf(recipe.toString());
                }
            });

            return builder;
        }));

        table.set("Smelting", fn(args -> {
            String input = args.checkjstring(2);
            String output = args.checkjstring(3);
            double xp = args.optdouble(4, 0.35);
            int cookTime = args.optint(5, 200);
            MLuaResourceLocations.parse(input);
            MLuaResourceLocations.parse(output);
            if (!Double.isFinite(xp) || xp < 0) throw new LuaError("[MLua] Smelting experience must be finite and non-negative");
            if (cookTime < 1) throw new LuaError("[MLua] Smelting cookTime must be at least 1");

            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", input);
            JsonObject recipe = new JsonObject();
            recipe.addProperty("type", "minecraft:smelting");
            recipe.add("ingredient", ingredient);
            //--version 1.20.1
            recipe.addProperty("result", output);
            //--endversion
            //--version 1.21.1
            JsonObject smeltingResult = new JsonObject();
            smeltingResult.addProperty("id", output);
            recipe.add("result", smeltingResult);
            //--endversion
            //--version 26.1
            recipe.add("result", itemStackResult(output, 1));
            //--endversion
            recipe.addProperty("experience", xp);
            recipe.addProperty("cookingtime", cookTime);
            return LuaValue.valueOf(recipe.toString());
        }));

        table.set("Stonecutting", fn(args -> {
            String input = args.checkjstring(2);
            String output = args.checkjstring(3);
            int count = args.optint(4, 1);
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
            recipe.add("result", itemStackResult(output, count));
            //--endversion
            //--version 26.1
            recipe.add("result", itemStackResult(output, count));
            //--endversion
            return LuaValue.valueOf(recipe.toString());
        }));

        return table;
    }

    private void attachBuilder(LuaTable builder, String className) {
        if (this.registration == null || this.registration.classTable(className) == null) {
            throw new IllegalStateException("Lua class '" + className
                + "' must be registered before creating recipe builders");
        }
        this.registration.attachClassInstance(builder, className, null);
    }

    private static JsonObject itemStackResult(String itemId, int count) {
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

    private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError error) {
                    throw error;
                } catch (Exception error) {
                    throw new LuaError("[MLua] " + error.getMessage());
                }
            }
        };
    }

}
