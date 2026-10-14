package dev.mlua.modules.world;

import dev.mlua.modules.world.Service.MLuaWorldService;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.MLuaContextView;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule.ServiceEntry;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class WorldModule implements MLuaModule {
    @Override
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaLevelWrapper.classTable(registration);
        registration.registerClass("World", "Level", "A live world instance with dimension, time, block, entity, and weather APIs.");
        worldMethods().forEach((name, description) ->
            MLuaClassMethods.registerInstanceMethod(registration, "World", name, description));
        registerWorldProperties(registration);
    }

    private static void registerWorldProperties(MLuaRegistration registration) {
        String[][] properties = {
            {"Id", "GetId", null, "string", "This world's dimension identifier."},
            {"Dimension", "GetDimension", null, "string", "This world's dimension identifier."},
            {"Side", "GetSide", null, "string", "Whether this world belongs to the client or server."},
            {"ClientSide", "IsClient", null, "boolean", "Whether this is a client world."},
            {"ServerSide", "IsServer", null, "boolean", "Whether this is a server world."},
            {"Time", "GetTime", "SetTime", "number", "This world's day time in ticks."},
            {"DayTime", "GetDayTime", null, "number", "The current day cycle time in ticks."},
            {"GameTime", "GetGameTime", null, "number", "The total number of ticks this world has been running."},
            {"SpawnPosition", "GetSpawnPosition", null, "Vector3", "This world's shared spawn position."},
            {"Weather", "GetWeather", "SetWeather", "Weather", "This world's current weather."},
            {"Difficulty", "GetDifficulty", "SetDifficulty", "Difficulty", "This world's difficulty."}
        };
        for (String[] property : properties) {
            MLuaClassMethods.registerInstanceProperty(registration, "World", property[0], property[1], property[2],
                property[3], "shared:Shared", property[4]);
        }
    }

    public static Map<String, String> worldMethods() {
        return Map.ofEntries(
            Map.entry("GetId", "Returns this world's dimension identifier."),
            Map.entry("GetDimension", "Returns this world's dimension identifier."),
            Map.entry("GetSide", "Returns whether this world belongs to the client or server."),
            Map.entry("IsClient", "Returns whether this is a client world."),
            Map.entry("IsServer", "Returns whether this is a server world."),
            Map.entry("IsChunkLoaded", "Returns whether the chunk at a position is loaded."),
            Map.entry("IsBlockLoaded", "Returns whether the chunk containing a position is loaded."),
            Map.entry("GetTime", "Returns this world's day time in ticks."),
            Map.entry("GetDayTime", "Returns the current day cycle time in ticks."),
            Map.entry("GetGameTime", "Returns the total number of ticks this world has been running."),
            Map.entry("GetMinBuildHeight", "Returns this world's minimum build height."),
            Map.entry("GetMaxBuildHeight", "Returns this world's maximum build height."),
            Map.entry("GetSpawnPosition", "Returns this world's shared spawn position."),
            Map.entry("IsDay", "Returns whether it is daytime in this world."),
            Map.entry("IsNight", "Returns whether it is nighttime in this world."),
            Map.entry("SetTime", "Sets this world's day time. This method is server-side only."),
            Map.entry("AddTime", "Adds ticks to this world's day time. This method is server-side only."),
            Map.entry("GetWeather", "Returns this world's current weather."),
            Map.entry("SetWeather", "Sets this world's weather. This method is server-side only."),
            Map.entry("GetDifficulty", "Returns this world's difficulty."),
            Map.entry("SetDifficulty", "Sets the server difficulty."),
            Map.entry("GetBlock", "Returns the live block instance at a position."),
            Map.entry("GetBlockState", "Returns the live block instance at a position."),
            Map.entry("GetFluid", "Returns the fluid identifier at a position, or nil when empty."),
            Map.entry("GetBiome", "Returns the biome identifier at a position."),
            Map.entry("GetLightLevel", "Returns the maximum local light level at a position."),
            Map.entry("GetSkyLight", "Returns the sky light level at a position."),
            Map.entry("GetBlockLight", "Returns the block light level at a position."),
            Map.entry("Raycast", "Returns the first block hit by a ray from the given origin."),
            Map.entry("GetPlayers", "Returns the players currently in this world."),
            Map.entry("GetEntities", "Returns the entities currently in this world."),
            Map.entry("FindEntitiesInRadius", "Returns entities within a radius of a position."),
            Map.entry("GetEntitiesInRadius", "Returns entities within a radius of a position."),
            Map.entry("GetEntitiesNearby", "Returns entities within a radius of a position."),
            Map.entry("FindEntitiesInBox", "Returns entities within an axis-aligned box."),
            Map.entry("FindPlayersInRadius", "Returns players within a radius of a position."),
            Map.entry("GetPlayersNearby", "Returns players within a radius of a position."),
            Map.entry("FindNearestPlayer", "Returns the nearest player to a position, or nil."),
            Map.entry("GetNearestPlayer", "Returns the nearest player to a position, or nil."),
            Map.entry("FindNearestEntity", "Returns the nearest matching entity to a position, or nil."),
            Map.entry("SetBlock", "Sets the block at a position. This method is server-side only."),
            Map.entry("BreakBlock", "Breaks a block, optionally dropping its items. This method is server-side only."),
            Map.entry("DestroyBlock", "Breaks a block, optionally dropping its items. This method is server-side only."),
            Map.entry("PlaySound", "Plays a sound at a position. This method is server-side only."),
            Map.entry("SpawnParticle", "Spawns particles at a position. This method is server-side only."),
            Map.entry("SpawnEntity", "Spawns an entity and returns its live instance. This method is server-side only."),
            Map.entry("DropItem", "Drops an item stack at a position. This method is server-side only."),
            Map.entry("Explode", "Creates an explosion. This method is server-side only."),
            Map.entry("SpawnLightning", "Spawns lightning at a position. This method is server-side only."),
            Map.entry("GetBlockInventory", "Returns the inventory at a container position, or nil."),
            Map.entry("GetPersistentData", "Returns this world's persistent MLua data."),
            Map.entry("GetSyncedData", "Returns this world's synced MLua data."),
            Map.entry("GetData", "Returns persistent world data by key."),
            Map.entry("SetData", "Sets persistent world data by key. This method is server-side only."),
            Map.entry("RemoveData", "Removes a persistent world data key. This method is server-side only."),
            Map.entry("ListGameRules", "Returns the names and current values of this world's gamerules."),
            Map.entry("GetGameRule", "Returns a world gamerule value by name."),
            Map.entry("SetGameRule", "Sets a world gamerule. This method is server-side only.")
        );
    }

    public static void bindWorldServiceMethods(LuaTable service, java.util.function.Function<String, LuaValue> methodFactory) {
        worldMethods().forEach((name, description) -> {
            if (!isExplicitServiceMethod(name)) service.set(name, methodFactory.apply(name));
        });
    }

    public static void bindWorldInstanceMethods(MLuaRegistration registration, LuaTable instance) {
        worldMethods().forEach((name, description) -> {
            LuaValue implementation = instance.rawget(name);
            if (!implementation.isfunction()) {
                throw new IllegalStateException("World instance is missing registered method '" + name + "'");
            }
            MLuaClassMethods.bindInstanceMethod(registration, "World", instance, name, implementation, description);
            instance.rawset(name, LuaValue.NIL);
        });
    }

    private static boolean isExplicitServiceMethod(String name) {
        return name.equals("GetSide") || name.equals("GetWeather") || name.equals("SetWeather");
    }

    public static Map<String, String> worldServiceMethods() {
        Map<String, String> methods = new LinkedHashMap<>();
        methods.put("Current", "Returns the current live world instance.");
        methods.put("Get", "Returns the live world instance for a dimension identifier.");
        methods.put("GetAll", "Returns all currently loaded live world instances.");
        methods.put("GetSide", "Returns whether this service belongs to a client or server context.");
        methods.put("GetWeather", "Returns the current world's weather.");
        methods.put("SetWeather", "Sets the current world's weather. This method is server-side only.");
        worldMethods().forEach((name, description) -> {
            if (!isExplicitServiceMethod(name)) methods.put(name, description);
        });
        return java.util.Collections.unmodifiableMap(methods);
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        globals.set("Level", context.registration().classTable("Level"));
        globals.set("World", context.registration().classTable("World"));
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("WorldService", "WorldService:GetBlockInventory(x: number, y: number, z: number)", "Container");
        docs.signature("WorldService", "WorldService:SetBlock(x: number, y: number, z: number, blockId: string)", null);
        docs.signature("WorldService", "WorldService:BreakBlock(x: number, y: number, z: number, dropItems: boolean)", null);
        docs.signature("WorldService", "WorldService:DestroyBlock(x: number, y: number, z: number, dropItems: boolean)", null);
        docs.signature("WorldService", "WorldService:PlaySound(x: number, y: number, z: number, sound: string, volume: number, pitch: number)", null);
        docs.signature("WorldService", "WorldService:SpawnParticle(x: number, y: number, z: number, particle: string, count: number)", null);
        docs.signature("WorldService", "WorldService:SpawnEntity(entityId: string, x: number, y: number, z: number, yaw: number, pitch: number)", null);
        docs.signature("WorldService", "WorldService:GetBlock(x: number, y: number, z: number)", null);
        docs.signature("WorldService", "WorldService:Raycast(ox: number, oy: number, oz: number, dx: number, dy: number, dz: number, distance: number)", null);
        docs.clazz("Level", "MLuaLevelWrapper", "A live Minecraft world instance, with dimension, time, and block access.", "classes/level.html", "Instance");
        docs.clazz("World", null, "A live world instance with dimension, time, block, entity, and weather APIs.", "classes/world.html", "Level");
        docs.service("World", "MLuaWorldService", "Provides live world instances for loaded dimensions.", "services/world.html");
        docs.guideStep("WorldService", "Use the current world service and live world instance",
            "The World service forwards the World class methods to the current dimension. Use Current or Get when you need to keep a specific live world instance, such as when working across dimensions.",
            "local WorldService = Minecraft:GetService(\"World\")\nlocal world = WorldService:Current()\n\nif world then\n    print(world.Id, world.Time)\n    world:SetTime(world.Time + 100)\nend\n\nWorldService:SetWeather(Enum.Weather.Rain)\nlocal nether = WorldService:Get(\"minecraft:the_nether\")\nif nether then\n    print(nether:GetTime())\nend");
    }

    @Override
    public String id() { return "world"; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder world = ServiceEntry.builder("World", MLuaWorldService::new)
            .className("WorldService")
            .description("Provides live world instances for loaded dimensions.");
        worldServiceMethods().forEach(world::function);
        return List.of(world.build());
    }
}
