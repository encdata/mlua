package dev.mlua.modules.loot.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.module.MLuaRegistration;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

@MLuaDescription("Builds loader-independent Minecraft loot table definitions for generated loot resources.")
public final class MLuaLootTableService extends MLuaReflectiveService {
    private static final String BUILDER_STATE = "__loot_builder_state";
    private final MLuaRegistration registration;

    public MLuaLootTableService(MLuaService.Context context) {
        super(context);
        this.registration = context.mlua().registration();
    }

    public static void registerBuilderClass(MLuaRegistration registration) {
        registration.registerClass("LootTableBuilder", registration.requireInstanceBaseClassName(),
            "A live builder for composing a loot table from pools, item entries, and roll settings.");
        registerBuilderFunction(registration, "SelectPool", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                int index = MLuaLuaArguments.wholeInt(args, 2, "LootTableBuilder:SelectPool", "index");
                if (index < 1 || index > state.poolCount) {
                    throw new LuaError("[MLua] LootTable pool index must be between 1 and " + state.poolCount);
                }
                state.currentPool = index - 1;
                state.currentEntry = null;
                return args.arg1();
            }
        }, "Selects the one-based pool that receives subsequent entry and roll changes.");
        registerBuilderFunction(registration, "AddEntry", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                String itemId = args.checkjstring(2);
                int weight = MLuaLuaArguments.wholeInt(args, 3, "LootTableBuilder:AddEntry", "weight");
                int quality = MLuaLuaArguments.optionalWholeInt(args, 4, 0, "LootTableBuilder:AddEntry", "quality");
                MLuaResourceLocations.parse(itemId);
                if (weight < 1) throw new LuaError("[MLua] LootTable entry weight must be at least 1");
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "minecraft:item");
                entry.addProperty("name", itemId);
                entry.addProperty("weight", weight);
                if (quality != 0) entry.addProperty("quality", quality);
                state.currentEntries().add(entry);
                state.currentEntry = entry;
                return args.arg1();
            }
        }, "Adds an item entry to the selected pool with its weight and optional quality.");
        registerBuilderFunction(registration, "AddTagEntry", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                String tagId = args.checkjstring(2);
                int weight = MLuaLuaArguments.optionalWholeInt(args, 3, 1, "LootTableBuilder:AddTagEntry", "weight");
                int quality = MLuaLuaArguments.optionalWholeInt(args, 4, 0, "LootTableBuilder:AddTagEntry", "quality");
                boolean expand = args.arg(5).optboolean(true);
                MLuaResourceLocations.parse(tagId);
                if (weight < 1) throw new LuaError("[MLua] LootTable entry weight must be at least 1");
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "minecraft:tag");
                entry.addProperty("name", tagId);
                entry.addProperty("expand", expand);
                entry.addProperty("weight", weight);
                if (quality != 0) entry.addProperty("quality", quality);
                state.currentEntries().add(entry);
                state.currentEntry = entry;
                return args.arg1();
            }
        }, "Adds a weighted entry that expands an item tag into its matching items.");
        registerBuilderFunction(registration, "AddEmptyEntry", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                int weight = MLuaLuaArguments.optionalWholeInt(args, 2, 1, "LootTableBuilder:AddEmptyEntry", "weight");
                int quality = MLuaLuaArguments.optionalWholeInt(args, 3, 0, "LootTableBuilder:AddEmptyEntry", "quality");
                if (weight < 1) throw new LuaError("[MLua] LootTable entry weight must be at least 1");
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "minecraft:empty");
                entry.addProperty("weight", weight);
                if (quality != 0) entry.addProperty("quality", quality);
                state.currentEntries().add(entry);
                state.currentEntry = entry;
                return args.arg1();
            }
        }, "Adds a weighted empty outcome to the selected pool.");
        registerBuilderFunction(registration, "SetEntryCount", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                JsonObject entry = state.currentEntry;
                if (entry == null) throw new LuaError("[MLua] Add an entry before setting its count");
                if ("minecraft:empty".equals(entry.get("type").getAsString())) {
                    throw new LuaError("[MLua] Empty loot entries do not produce item counts");
                }
                int minimum = MLuaLuaArguments.wholeInt(args, 2, "LootTableBuilder:SetEntryCount", "min");
                int maximum = MLuaLuaArguments.optionalWholeInt(args, 3, minimum, "LootTableBuilder:SetEntryCount", "max");
                if (minimum < 0 || maximum < minimum) {
                    throw new LuaError("[MLua] LootTable entry count requires 0 <= min <= max");
                }
                JsonObject count = new JsonObject();
                if (minimum == maximum) {
                    count.addProperty("count", minimum);
                } else {
                    JsonObject range = new JsonObject();
                    range.addProperty("type", "minecraft:uniform");
                    range.addProperty("min", minimum);
                    range.addProperty("max", maximum);
                    count.add("count", range);
                }
                count.addProperty("function", "minecraft:set_count");
                JsonArray functions = entry.has("functions") ? entry.getAsJsonArray("functions") : new JsonArray();
                for (int index = functions.size() - 1; index >= 0; index--) {
                    JsonObject function = functions.get(index).getAsJsonObject();
                    if ("minecraft:set_count".equals(function.get("function").getAsString())) {
                        functions.remove(index);
                    }
                }
                functions.add(count);
                entry.add("functions", functions);
                return args.arg1();
            }
        }, "Sets the most recently added entry's item count or random count range.");
        registerBuilderFunction(registration, "SetRolls", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                setRollRange(state.currentPool(), "rolls", args, "SetRolls", "rolls");
                return args.arg1();
            }
        }, "Sets the selected pool's minimum rolls and optional maximum rolls.");
        registerBuilderFunction(registration, "SetBonusRolls", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                setRollRange(state.currentPool(), "bonus_rolls", args, "SetBonusRolls", "bonus rolls");
                return args.arg1();
            }
        }, "Sets the selected pool's minimum bonus rolls and optional maximum bonus rolls.");
        registerBuilderFunction(registration, "Build", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                JsonObject root = new JsonObject();
                root.add("pools", state(args.arg1()).pools);
                return LuaValue.valueOf(root.toString());
            }
        }, "Serializes the current loot table definition as JSON.");
    }

    private static void registerBuilderFunction(MLuaRegistration registration, String name,
                                                LuaValue implementation, String description) {
        LuaTable classTable = registration.classTable("LootTableBuilder");
        LuaValue existing = classTable == null ? LuaValue.NIL : classTable.rawget(name);
        registration.registerClassFunction("LootTableBuilder", name,
            existing.isfunction() ? existing : implementation, description);
    }

    @Override
    public String name() { return "LootTable"; }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Both");
        return table;
    }

    @MLuaDescription("Creates a live LootTableBuilder with the requested number of pools.")
    public LuaTable Create(Optional<Integer> requestedPoolCount) {
        int poolCount = requestedPoolCount.orElse(1);
        if (poolCount < 1) throw new LuaError("[MLua] LootTable:Create poolCount must be at least 1");
        return createBuilder(poolCount);
    }

    @MLuaDescription("Returns the serialized JSON for an empty loot table.")
    public String Empty() {
        JsonObject root = new JsonObject();
        root.add("pools", new JsonArray());
        return root.toString();
    }

    private LuaTable createBuilder(int poolCount) {
        JsonArray pools = new JsonArray();
        for (int i = 0; i < poolCount; i++) {
            JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);
            pool.add("entries", new JsonArray());
            pools.add(pool);
        }
        LuaTable instance = this.registration.createClassInstance("LootTableBuilder");
        instance.rawset(BUILDER_STATE, LuaValue.userdataOf(new BuilderState(poolCount, pools)));
        return instance;
    }

    private static BuilderState state(LuaValue value) {
        if (!value.istable()) throw new LuaError("[MLua] LootTableBuilder method requires a live LootTableBuilder instance.");
        LuaValue backing = value.checktable().rawget(BUILDER_STATE);
        Object raw = backing.isuserdata() ? backing.touserdata() : null;
        if (!(raw instanceof BuilderState state)) throw new LuaError("[MLua] LootTableBuilder is no longer attached to its builder state.");
        return state;
    }

    private static void setRollRange(JsonObject pool, String key, Varargs args, String method, String label) {
        int minimum = MLuaLuaArguments.wholeInt(args, 2, "LootTableBuilder:" + method, label + " minimum");
        if (minimum < 0) throw new LuaError("[MLua] LootTable " + label + " cannot be negative");
        if (args.narg() >= 3 && !args.arg(3).isnil()) {
            int maximum = MLuaLuaArguments.wholeInt(args, 3, "LootTableBuilder:" + method, label + " maximum");
            if (maximum < minimum) throw new LuaError("[MLua] LootTable maximum " + label + " must be at least the minimum");
            JsonObject range = new JsonObject();
            range.addProperty("min", minimum);
            range.addProperty("max", maximum);
            pool.add(key, range);
        } else {
            pool.addProperty(key, minimum);
        }
    }

    private static final class BuilderState {
        private final int poolCount;
        private final JsonArray pools;
        private int currentPool;
        private JsonObject currentEntry;

        private BuilderState(int poolCount, JsonArray pools) {
            this.poolCount = poolCount;
            this.pools = pools;
        }

        private JsonObject currentPool() { return this.pools.get(this.currentPool).getAsJsonObject(); }

        private JsonArray currentEntries() { return this.currentPool().getAsJsonArray("entries"); }
    }
}
