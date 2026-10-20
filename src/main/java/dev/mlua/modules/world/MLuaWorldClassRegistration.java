package dev.mlua.modules.world;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

/** Registers the public World, Chunk, gamerule, and raycast result class contracts. */
final class MLuaWorldClassRegistration {
    private MLuaWorldClassRegistration() { }

    static void registerWorldProperties(MLuaRegistration registration) {
        String[][] properties = {
            {"Id", "GetId", null, "string", "This world's dimension identifier."},
            {"Dimension", "GetDimension", null, "string", "This world's dimension identifier."},
            {"Side", "GetSide", null, "string", "Whether this world belongs to the client or server."},
            {"ClientSide", "IsClient", null, "boolean", "Whether this is a client world."},
            {"ServerSide", "IsServer", null, "boolean", "Whether this is a server world."},
            {"Time", "GetTime", "SetTime", "number", "This world's day time in ticks."},
            {"DayTime", "GetDayTime", null, "number", "The current day cycle time in ticks."},
            {"GameTime", "GetGameTime", null, "number", "The total number of ticks this world has been running."},
            {"SpawnPosition", "GetSpawnPosition", "SetSpawnPosition", "Vector3", "This world's shared spawn position."},
            {"Weather", "GetWeather", "SetWeather", "Weather", "This world's current weather."},
            {"Difficulty", "GetDifficulty", "SetDifficulty", "Difficulty", "This world's difficulty."},
            {"Raining", "IsRaining", null, "boolean", "Whether it is raining in this world."},
            {"Thundering", "IsThundering", null, "boolean", "Whether a thunderstorm is active in this world."}
        };
        for (String[] property : properties) {
            MLuaClassMethods.registerInstanceProperty(registration, "World", property[0], property[1], property[2],
                property[3], "shared:Shared", property[4]);
        }
    }

    static void registerRaycastResultClasses(MLuaRegistration registration) {
        registration.registerClass("RaycastResult", registration.requireInstanceBaseClassName(),
            "A world ray hit with a typed hit position, surface normal, and distance.");
        String[][] raycastProperties = {
            {"HitType", "string", "string", "The kind of ray hit: block or entity."},
            {"Position", "Vector3", "Vector3", "The point where the ray intersects the entity bounds."},
            {"Normal", "Vector3", "Vector3", "The outward surface direction at the hit point."},
            {"Distance", "number", "number", "Distance from the ray origin to the hit point."}
        };
        for (String[] property : raycastProperties) {
            registerTableProperty(registration, "RaycastResult", property[0], property[1], property[3]);
        }

        registration.registerClass("EntityRaycastResult", "RaycastResult",
            "The nearest entity and hit point returned by a world's entity raycast.");
        registerTableProperty(registration, "EntityRaycastResult", "Entity", "Entity",
            "Returns the live entity intersected by the ray.");

        registration.registerClass("BlockRaycastResult", "RaycastResult",
            "The first colliding block and hit point returned by a world's block raycast.");
        String[][] blockProperties = {
            {"Block", "string", "The registered block identifier hit by the ray."},
            {"Face", "string", "The face of the block hit by the ray."}
        };
        for (String[] property : blockProperties) {
            registerTableProperty(registration, "BlockRaycastResult", property[0], property[1], property[2]);
        }
        registerTableProperty(registration, "BlockRaycastResult", "BlockInstance", "Block",
            "Returns the live Block instance at the hit position, including its registered subclass.");

        registration.registerClass("BoundsRaycastResult", "RaycastResult",
            "The nearest custom EntityBounds hit returned by a world's bounds raycast.");
        registerTableProperty(registration, "BoundsRaycastResult", "Bounds", "EntityBounds",
            "Returns the EntityBounds instance intersected by the ray.");
        registration.registerClassFunction("BoundsRaycastResult", "GetBoundsIndex", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().get("BoundsIndex"); }
        }, "Returns the one-based position of the hit bounds in the input array.");
        MLuaClassMethods.registerInstanceProperty(registration, "BoundsRaycastResult", "BoundsIndex", "GetBoundsIndex", null,
            "number", "shared:Shared", "One-based index of the hit bounds in the input array.");
    }

    static void registerGameRuleEntry(MLuaRegistration registration) {
        registration.registerClass("GameRuleEntry", registration.requireInstanceBaseClassName(),
            "A live typed view of one registered Minecraft gamerule.");
        registration.registerClassFunction("GameRuleEntry", "GetName", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().rawget("__game_rule_name"); }
        }, "Returns the gamerule name accepted by GetGameRule and SetGameRule.");
        MLuaClassMethods.registerInstanceProperty(registration, "GameRuleEntry", "Name", "GetName", null,
            "string", "shared:Shared", "The gamerule name accepted by GetGameRule and SetGameRule.");
        registration.registerClassFunction("GameRuleEntry", "GetValue", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                LuaValue getter = self.checktable().rawget("__game_rule_get_value");
                if (!getter.isfunction()) throw new LuaError("[MLua] Gamerule entry is missing its live value source.");
                return getter.call();
            }
        }, "Returns this gamerule's current value.");
        registration.registerClassFunction("GameRuleEntry", "SetValue", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                LuaValue setter = self.checktable().rawget("__game_rule_set_value");
                if (!setter.isfunction()) throw new LuaError("[MLua] Gamerule entry is missing its value setter.");
                setter.call(value);
                return LuaValue.NONE;
            }
        }, "Sets this gamerule's value using the same type and range checks as World:SetGameRule.");
        MLuaClassMethods.registerInstanceProperty(registration, "GameRuleEntry", "Value",
            "GetValue", "SetValue", "boolean|number|string", "shared:Shared",
            "The current value, readable on clients and servers. Assigning it is server-side only.");
        registration.registerClassFunction("GameRuleEntry", "GetValueType", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                LuaValue value = self.checktable().get("Value");
                return LuaValue.valueOf(value.isboolean() ? "boolean" : value.isnumber() ? "number" : "string");
            }
        }, "Returns whether this gamerule value is boolean, number, or string.");
        MLuaClassMethods.registerInstanceProperty(registration, "GameRuleEntry", "ValueType",
            "GetValueType", null, "string", "shared:Shared", "The Lua value type of this gamerule's value.");
        registration.registerClassFunction("GameRuleEntry", "IsBoolean", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(self.checktable().get("Value").isboolean()); }
        }, "Returns whether this gamerule stores a boolean value.");
        registration.registerClassFunction("GameRuleEntry", "IsNumber", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(self.checktable().get("Value").isnumber()); }
        }, "Returns whether this gamerule stores a numeric value.");
        registration.registerClassFunction("GameRuleEntry", "IsString", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(self.checktable().get("Value").isstring()); }
        }, "Returns whether this gamerule stores a string value.");
    }

    static void registerChunkProperties(MLuaRegistration registration) {
        String[][] properties = {
            {"X", "GetX", "number", "This chunk's X coordinate."},
            {"Z", "GetZ", "number", "This chunk's Z coordinate."},
            {"World", "GetWorld", "World", "The live dimension containing this chunk."},
            {"Level", "GetLevel", "World", "The live world containing this chunk."},
            {"Dimension", "GetDimension", "string", "The identifier of the dimension containing this chunk."},
            {"Position", "GetPosition", "Vector2", "Chunk coordinates: X is chunk X and Y is chunk Z."},
            {"BlockOrigin", "GetBlockOrigin", "Vector3", "The minimum block-space X/Z position of this chunk."},
            {"Loaded", "IsLoaded", "boolean", "Whether this chunk is loaded now, without loading it."},
            {"Forced", "IsForced", "boolean", "Whether this chunk has a force-load ticket."}
        };
        for (String[] property : properties) {
            MLuaClassMethods.registerInstanceProperty(registration, "Chunk", property[0], property[1], null,
                property[2], "shared:Shared", property[3]);
        }
    }

    private static void registerTableProperty(MLuaRegistration registration, String className,
                                             String property, String type, String description) {
        String getter = "Get" + property;
        registration.registerClassFunction(className, getter, new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().get(property); }
        }, description);
        MLuaClassMethods.registerInstanceProperty(registration, className, property, getter, null,
            type, "shared:Shared", description);
    }
}
