package dev.mlua.modules.loot.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.module.MLuaRegistration;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaLootTableService extends MLuaServiceBase {
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
                int index = args.checkint(2);
                if (index < 1 || index > state.poolCount) {
                    throw new LuaError("[MLua] LootTable pool index must be between 1 and " + state.poolCount);
                }
                state.currentPool = index - 1;
                return args.arg1();
            }
        }, "Selects the one-based pool that receives subsequent entry and roll changes.");
        registerBuilderFunction(registration, "AddEntry", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                String itemId = args.checkjstring(2);
                int weight = args.checkint(3);
                int quality = args.optint(4, 0);
                MLuaResourceLocations.parse(itemId);
                if (weight < 1) throw new LuaError("[MLua] LootTable entry weight must be at least 1");
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "minecraft:item");
                entry.addProperty("name", itemId);
                entry.addProperty("weight", weight);
                if (quality != 0) entry.addProperty("quality", quality);
                state.currentEntries().add(entry);
                return args.arg1();
            }
        }, "Adds an item entry to the selected pool with its weight and optional quality.");
        registerBuilderFunction(registration, "SetRolls", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                setRollRange(state.currentPool(), "rolls", args, "rolls");
                return args.arg1();
            }
        }, "Sets the selected pool's minimum rolls and optional maximum rolls.");
        registerBuilderFunction(registration, "SetBonusRolls", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                setRollRange(state.currentPool(), "bonus_rolls", args, "bonus rolls");
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
        LuaTable table = new LuaTable();
        table.set("__side", "Both");
        table.set("Create", fn(args -> {
            int poolCount = args.optint(2, 1);
            if (poolCount < 1) throw new LuaError("[MLua] LootTable:Create poolCount must be at least 1");
            return createBuilder(poolCount);
        }));
        table.set("Empty", fn(args -> {
            JsonObject root = new JsonObject();
            root.add("pools", new JsonArray());
            return LuaValue.valueOf(root.toString());
        }));
        return table;
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

    private static void setRollRange(JsonObject pool, String key, Varargs args, String label) {
        int minimum = args.checkint(2);
        if (minimum < 0) throw new LuaError("[MLua] LootTable " + label + " cannot be negative");
        if (args.narg() >= 3 && !args.arg(3).isnil()) {
            int maximum = args.checkint(3);
            if (maximum < minimum) throw new LuaError("[MLua] LootTable maximum " + label + " must be at least the minimum");
            JsonObject range = new JsonObject();
            range.addProperty("min", minimum);
            range.addProperty("max", maximum);
            pool.add(key, range);
        } else {
            pool.addProperty(key, minimum);
        }
    }

    private static VarArgFunction fn(Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
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

    private static final class BuilderState {
        private final int poolCount;
        private final JsonArray pools;
        private int currentPool;

        private BuilderState(int poolCount, JsonArray pools) {
            this.poolCount = poolCount;
            this.pools = pools;
        }

        private JsonObject currentPool() { return this.pools.get(this.currentPool).getAsJsonObject(); }

        private JsonArray currentEntries() { return this.currentPool().getAsJsonArray("entries"); }
    }
}
