package dev.mlua.modules.world;

import dev.mlua.modules.world.Service.MLuaWorldService;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.MLuaContextView;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.module.MLuaDocBuilder;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;

public class WorldModule implements MLuaModule {
    @Override
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaLevelWrapper.classTable(registration);
        registration.registerClass("World", "Level", "A live world instance with dimension, time, block, entity, and weather APIs.");
        worldMethods().forEach((name, description) ->
            MLuaClassMethods.registerInstanceMethod(registration, "World", name, description));
    }

    private static Map<String, String> worldMethods() {
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

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        globals.set("Level", context.registration().classTable("Level"));
        globals.set("World", context.registration().classTable("World"));
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("WorldService", "World:GetBlockInventory(x: number, y: number, z: number)", "Container");
        docs.signature("WorldService", "World:SetBlock(x: number, y: number, z: number, blockId: string)", null);
        docs.signature("WorldService", "World:BreakBlock(x: number, y: number, z: number, dropItems: boolean)", null);
        docs.signature("WorldService", "World:DestroyBlock(x: number, y: number, z: number, dropItems: boolean)", null);
        docs.signature("WorldService", "World:PlaySound(x: number, y: number, z: number, sound: string, volume: number, pitch: number)", null);
        docs.signature("WorldService", "World:SpawnParticle(x: number, y: number, z: number, particle: string, count: number)", null);
        docs.signature("WorldService", "World:SpawnEntity(entityId: string, x: number, y: number, z: number, yaw: number, pitch: number)", null);
        docs.signature("WorldService", "World:GetBlock(x: number, y: number, z: number)", null);
        docs.signature("WorldService", "World:Raycast(ox: number, oy: number, oz: number, dx: number, dy: number, dz: number, distance: number)", null);
        docs.clazz("Level", "MLuaLevelWrapper", "A live Minecraft world instance, with dimension, time, and block access.", "classes/level.html", "Instance");
        // World is the live level class; WorldService is a separate Minecraft:GetService class.
        // Keep the class definition tied to its registered Lua contract instead of scanning the
        // service implementation as if it were the live world instance type.
        docs.clazz("World", null, "A live world instance with dimension, time, block, entity, and weather APIs.", "classes/world.html", "Level");
        docs.service("World", "MLuaWorldService", "Provides live world instances for loaded dimensions.", "services/world.html");
    }

    @Override
    public String id() { return "world"; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder world = ServiceEntry.builder("World", MLuaWorldService::new)
            .className("WorldService")
            .description("Provides live world instances for loaded dimensions.");
        world.function("Current", "Returns the current live world instance.")
            .function("Get", "Returns the live world instance for a dimension identifier.")
            .function("GetAll", "Returns all currently loaded live world instances.")
            .function("GetSide", "Returns whether this service belongs to a client or server context.")
            .function("GetWeather", "Returns the current world's weather.")
            .function("SetWeather", "Sets the current world's weather. This method is server-side only.");
        return List.of(world.build());
    }
}
