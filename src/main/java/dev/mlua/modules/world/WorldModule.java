package dev.mlua.modules.world;

import dev.mlua.modules.world.service.MLuaWorldService;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.MLuaContextView;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.minecraft.binding.MLuaFluidStateWrapper;
import dev.mlua.minecraft.binding.MLuaBiomeWrapper;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule.ServiceEntry;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class WorldModule implements MLuaModule {
    private static final Map<String, String> WORLD_METHODS = createWorldMethods();
    private static final Map<String, String> WORLD_SERVICE_METHODS = createWorldServiceMethods();
    private static final Map<String, String> CHUNK_METHODS = Map.ofEntries(
        Map.entry("GetX", "Returns this chunk's X coordinate."),
        Map.entry("GetZ", "Returns this chunk's Z coordinate."),
        Map.entry("GetWorld", "Returns the live World instance for the dimension containing this chunk."),
        Map.entry("GetLevel", "Returns the same live World instance as GetWorld()."),
        Map.entry("GetDimension", "Returns the dimension identifier containing this chunk."),
        Map.entry("GetPosition", "Returns the Vector2 chunk coordinates (chunk X, chunk Z)."),
        Map.entry("GetBlockOrigin", "Returns this chunk's minimum block-space X/Z position as a Vector3."),
        Map.entry("IsLoaded", "Checks whether this chunk is currently loaded without loading it."),
        Map.entry("IsForced", "Checks whether this dimension has a force-load ticket for this chunk."),
        Map.entry("Load", "Loads this chunk on the server and returns the same Chunk instance."),
        Map.entry("Unload", "On the server, removes this chunk's force-load ticket; Minecraft decides when it unloads."),
        Map.entry("SetForced", "On the server, sets or removes this chunk's force-load ticket."),
        Map.entry("ForceLoad", "On the server, adds a persistent force-load ticket for this chunk."),
        Map.entry("GetBlockAt", "Loads this chunk if needed and returns the live Block instance at a world position inside it, including its registered addon subclass."),
        Map.entry("GetBlockStateAt", "Loads this chunk if needed and returns the typed BlockState at a world position."),
        Map.entry("GetFluidStateAt", "Loads this chunk if needed and returns the typed FluidState at a world position."),
        Map.entry("GetBlockEntityAt", "Loads this chunk if needed and returns its block entity at a world position."),
        Map.entry("GetBiomeAt", "Loads this chunk if needed and returns a typed Biome instance at a world position inside it."),
        Map.entry("GetFluidAt", "Loads this chunk if needed and returns the fluid identifier at a world position inside it, or nil."),
        Map.entry("GetLightAt", "Loads this chunk if needed and returns the raw local light level at a world position inside it."),
        Map.entry("GetHeightAt", "Loads this chunk if needed and returns the first free Y coordinate above the selected heightmap at a world X/Z position."),
        Map.entry("GetTopBlockAt", "Loads this chunk if needed and returns the live Block immediately below the selected heightmap's first free Y position."),
        Map.entry("SetBlockAt", "Loads this chunk if needed and sets a block id with optional properties or an existing BlockState at a world position. Optional Minecraft update flags default to 3. Server-side only."),
        Map.entry("GetEntities", "Loads this chunk if needed and returns live entities whose block position belongs to it."),
        Map.entry("GetBlockEntities", "Loads this chunk if needed and returns its live registered BlockEntity instances.")
    );

    @Override
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaLevelWrapper.classTable(registration);
        registration.registerClass("World", "Level", "A live world instance with dimension, time, block, entity, and weather APIs.");
        worldMethods().forEach((name, description) ->
            MLuaClassMethods.registerInstanceMethod(registration, "World", name, description));
        MLuaWorldClassRegistration.registerWorldProperties(registration);
        MLuaBlockStateWrapper.classTable(registration);
        MLuaFluidStateWrapper.classTable(registration);
        MLuaBiomeWrapper.classTable(registration);
        registration.registerClass("Chunk", "Instance", "A dimension-scoped chunk handle whose data methods load the chunk when needed.");
        chunkMethods().forEach((name, description) ->
            MLuaClassMethods.registerInstanceMethod(registration, "Chunk", name, description));
        MLuaWorldClassRegistration.registerRaycastResultClasses(registration);
        MLuaWorldClassRegistration.registerGameRuleEntry(registration);
        MLuaWorldClassRegistration.registerChunkProperties(registration);
    }

    public static Map<String, String> worldMethods() {
        return WORLD_METHODS;
    }

    public static Map<String, String> chunkMethods() {
        return CHUNK_METHODS;
    }

    private static Map<String, String> createWorldMethods() {
        return Map.ofEntries(
            Map.entry("GetId", "Returns this world's dimension identifier."),
            Map.entry("GetDimension", "Returns this world's dimension identifier."),
            Map.entry("GetSide", "Returns whether this world belongs to the client or server."),
            Map.entry("IsClient", "Returns whether this is a client world."),
            Map.entry("IsServer", "Returns whether this is a server world."),
            Map.entry("IsChunkLoaded", "Returns whether a chunk is loaded. Vector2 input uses whole-number chunk X/Z coordinates; Vector3 and CFrame input use block positions."),
            Map.entry("IsBlockLoaded", "Returns whether the chunk containing a position is loaded."),
            Map.entry("LoadChunk", "Loads or generates a chunk. Vector2 input uses whole-number chunk X/Z coordinates; Vector3 and CFrame input use block positions."),
            Map.entry("GetChunk", "Returns a live Chunk instance without loading it. Vector2 input uses whole-number chunk X/Z coordinates; Vector3 and CFrame input use block positions. Repeated lookups reuse the same instance while it is referenced."),
            Map.entry("GetChunkAt", "Returns a live Chunk instance without loading it. Vector2 input uses whole-number chunk X/Z coordinates; Vector3 and CFrame input use block positions. Repeated lookups reuse the same instance while it is referenced."),
            Map.entry("UnloadChunk", "Removes this dimension's force-load ticket; Minecraft unloads it when no other tickets need it."),
            Map.entry("SetChunkForced", "Sets whether this dimension keeps a chunk force-loaded."),
            Map.entry("ForceLoadChunk", "Keeps a chunk force-loaded in this dimension until its force-load ticket is removed."),
            Map.entry("IsChunkForced", "Returns whether this dimension has a force-load ticket for the chunk."),
            Map.entry("GetEntitiesInChunk", "Returns live entities whose block position belongs to the chunk, optionally loading it first."),
            Map.entry("GetTime", "Returns this world's day time in ticks."),
            Map.entry("GetDayTime", "Returns the current day cycle time in ticks."),
            Map.entry("GetGameTime", "Returns the total number of ticks this world has been running."),
            Map.entry("GetMinBuildHeight", "Returns this world's minimum build height."),
            Map.entry("GetMaxBuildHeight", "Returns this world's maximum build height."),
            Map.entry("GetSpawnPosition", "Returns this world's shared spawn position."),
            Map.entry("SetSpawnPosition", "Sets this world's shared respawn position and yaw. This method is server-side only."),
            Map.entry("IsDay", "Returns whether it is daytime in this world."),
            Map.entry("IsNight", "Returns whether it is nighttime in this world."),
            Map.entry("SetTime", "Sets this world's day time using a whole-number tick value. This method is server-side only."),
            Map.entry("AddTime", "Adds a whole-number tick delta and errors if the result overflows. This method is server-side only."),
            Map.entry("GetWeather", "Returns this world's current weather."),
            Map.entry("SetWeather", "Sets this world's weather for an optional whole-number duration in ticks (default 6000). This method is server-side only."),
            Map.entry("GetDifficulty", "Returns this world's difficulty."),
            Map.entry("SetDifficulty", "Sets the server difficulty."),
            Map.entry("GetBlock", "Loads the position's chunk on the server if needed and returns the live Block instance. Returns nil on a client when that chunk is not loaded."),
            Map.entry("GetBlockEntity", "Loads the position's chunk on the server if needed and returns its live block entity, or nil when absent. Returns nil on a client when that chunk is not loaded."),
            Map.entry("GetBlockState", "Loads the position's chunk on the server if needed and returns its BlockState with current properties. Returns nil on a client when that chunk is not loaded."),
            Map.entry("SetBlockState", "Places an immutable BlockState at a position, loading its chunk by default. Optional Minecraft update flags default to 3. This method is server-side only."),
            Map.entry("GetFluid", "Loads the position's chunk on the server if needed and returns the fluid identifier, or nil when empty or unavailable."),
            Map.entry("GetFluidState", "Loads the position's chunk on the server if needed and returns its live position-aware FluidState. Returns nil on a client when that chunk is not loaded."),
            Map.entry("GetBiome", "Loads the position's chunk on the server if needed and returns its typed Biome instance. Returns nil on a client when that chunk is not loaded."),
            Map.entry("GetLightLevel", "Loads the position's chunk on the server if needed and returns the maximum local light level. Returns nil on a client when that chunk is not loaded."),
            Map.entry("GetSkyLight", "Loads the position's chunk on the server if needed and returns the sky light level. Returns nil on a client when that chunk is not loaded."),
            Map.entry("GetBlockLight", "Loads the position's chunk on the server if needed and returns the block light level. Returns nil on a client when that chunk is not loaded."),
            Map.entry("CanSeeSky", "Loads the position's chunk on the server if needed and returns whether the sky is visible. Returns nil on a client when that chunk is not loaded."),
            Map.entry("IsRaining", "Returns whether it is raining in this world."),
            Map.entry("IsThundering", "Returns whether a thunderstorm is active in this world."),
            Map.entry("IsRainingAt", "Loads the position's chunk on the server if needed and returns whether rain reaches it. Returns nil on a client when that chunk is not loaded."),
            Map.entry("GetSurfaceY", "Loads the X/Z column's chunk on the server if needed and returns its motion-blocking surface height. Returns nil on a client when that chunk is not loaded."),
            Map.entry("Raycast", "Returns the first block hit by a ray from the given origin."),
            Map.entry("RaycastEntity", "Returns the nearest matching entity hit before a colliding block."),
            Map.entry("RaycastAABB", "Returns the nearest custom EntityBounds hit along a ray."),
            Map.entry("GetPlayers", "Returns players currently in this dimension. On the client, this includes only players currently loaded by that client."),
            Map.entry("GetEntities", "Returns the entities currently in this world."),
            Map.entry("FindEntitiesInRadius", "Returns entities within a radius of a position."),
            Map.entry("GetEntitiesInRadius", "Returns entities within a radius of a position."),
            Map.entry("GetEntitiesNearby", "Returns entities within a radius of a position."),
            Map.entry("FindEntitiesInBox", "Returns entities within an axis-aligned box."),
            Map.entry("FindEntitiesInBounds", "Returns entities within an EntityBounds instance."),
            Map.entry("FindPlayersInBounds", "Returns loaded players whose positions lie within an EntityBounds instance."),
            Map.entry("FindPlayersInRadius", "Returns players within a radius of a position. On the client, only currently loaded players are returned."),
            Map.entry("GetPlayersNearby", "Returns players within a radius of a position. On the client, only currently loaded players are returned."),
            Map.entry("FindNearestPlayer", "Returns the nearest player to a position, or nil. On the client, searches currently loaded players."),
            Map.entry("GetNearestPlayer", "Returns the nearest player to a position, or nil. On the client, searches currently loaded players."),
            Map.entry("FindNearestEntity", "Returns the nearest matching entity to a position, or nil."),
            Map.entry("SetBlock", "Sets a block from its registry identifier or a typed BlockState, with optional state properties and Minecraft update flags (default 3). Loads its chunk by default and returns whether Minecraft changed the block. This method is server-side only."),
            Map.entry("BreakBlock", "Breaks a block, optionally dropping its items. This method is server-side only."),
            Map.entry("DestroyBlock", "Breaks a block, optionally dropping its items. This method is server-side only."),
            Map.entry("PlaySound", "Plays a sound at a position. Volume and pitch default to one and must be finite and non-negative; this method is server-side only."),
            Map.entry("SpawnParticle", "Spawns a positive whole-number count of particles at a position. This method is server-side only."),
            Map.entry("SpawnEntity", "Spawns an entity and returns its live instance. CFrame positions supply yaw and pitch unless explicit rotation values are given. This method is server-side only."),
            Map.entry("DropItem", "Drops a positive whole-number item count up to that item's stack limit. This method is server-side only."),
            Map.entry("Explode", "Creates an explosion. This method is server-side only."),
            Map.entry("SpawnLightning", "Spawns lightning at a position. This method is server-side only."),
            Map.entry("GetBlockInventory", "Loads the container position's chunk and returns its inventory, or nil when no inventory exists. This method is server-side only."),
            Map.entry("GetPersistentData", "Returns this world's persistent MLua data."),
            Map.entry("GetSyncedData", "Returns this world's synced MLua data."),
            Map.entry("GetData", "Returns persistent world data by key."),
            Map.entry("SetData", "Sets persistent world data by key. This method is server-side only."),
            Map.entry("RemoveData", "Removes a persistent world data key. This method is server-side only."),
            Map.entry("ListGameRules", "Returns the names and current values of this world's gamerules. Available wherever this dimension is loaded; client reads use the client's local dimension state."),
            Map.entry("GetGameRule", "Returns a world gamerule value by name. Available wherever this dimension is loaded; client reads use the client's local dimension state."),
            Map.entry("GetGameRuleEntry", "Returns one typed gamerule entry by name. Available wherever this dimension is loaded; client reads use the client's local dimension state."),
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
            if (implementation.isfunction()) {
                MLuaClassMethods.bindInstanceMethod(registration, "World", instance, name, implementation, description);
                instance.rawset(name, LuaValue.NIL);
                return;
            }

            // Some focused binders (for example chunk operations) install directly into the
            // instance method store. Do not require them to temporarily expose an instance
            // function on the table just so this final consistency pass can bind it again.
            LuaValue metatable = instance.getmetatable();
            LuaValue methods = metatable.istable()
                ? metatable.checktable().rawget("__mlua_instance_methods")
                : LuaValue.NIL;
            if (!methods.istable() || !methods.checktable().rawget(name).isfunction()) {
                throw new IllegalStateException("World instance is missing registered method '" + name + "'");
            }
        });
    }

    private static boolean isExplicitServiceMethod(String name) {
        return name.equals("GetSide") || name.equals("GetWeather") || name.equals("SetWeather");
    }

    public static Map<String, String> worldServiceMethods() {
        return WORLD_SERVICE_METHODS;
    }

    private static Map<String, String> createWorldServiceMethods() {
        Map<String, String> methods = new LinkedHashMap<>();
        methods.put("Current", "Returns the client world on the client or the overworld on the server.");
        methods.put("Get", "Returns the live World instance for a loaded dimension identifier.");
        methods.put("GetAll", "Returns all currently loaded live world instances.");
        methods.put("GetSide", "Returns whether this service belongs to a client or server context.");
        methods.put("GetWeather", "Returns the current world's weather.");
        methods.put("SetWeather", "Sets the current world's weather for an optional whole-number duration in ticks (default 6000). This method is server-side only.");
        worldMethods().forEach((name, description) -> {
            if (!isExplicitServiceMethod(name)) methods.put(name, description);
        });
        return java.util.Collections.unmodifiableMap(methods);
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        globals.set("Level", context.registration().classTable("Level"));
        globals.set("World", context.registration().classTable("World"));
        globals.set("BlockState", context.registration().classTable("BlockState"));
        globals.set("FluidState", context.registration().classTable("FluidState"));
        globals.set("Biome", context.registration().classTable("Biome"));
        globals.set("Chunk", context.registration().classTable("Chunk"));
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        MLuaWorldDocumentation.contribute(docs);
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
