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
    private static final Map<String, String> WORLD_METHODS = createWorldMethods();
    private static final Map<String, String> WORLD_SERVICE_METHODS = createWorldServiceMethods();
    private static final Map<String, String> CHUNK_METHODS = Map.ofEntries(
        Map.entry("GetX", "Returns this chunk's X coordinate."),
        Map.entry("GetZ", "Returns this chunk's Z coordinate."),
        Map.entry("GetDimension", "Returns the dimension identifier containing this chunk."),
        Map.entry("GetPosition", "Returns the Vector2 chunk coordinates (chunk X, chunk Z)."),
        Map.entry("GetBlockOrigin", "Returns this chunk's minimum block-space X/Z position as a Vector3."),
        Map.entry("IsLoaded", "Checks whether this chunk is currently loaded without loading it."),
        Map.entry("IsForced", "Checks whether this dimension has a force-load ticket for this chunk."),
        Map.entry("Load", "Loads this chunk on the server and returns the same Chunk instance."),
        Map.entry("Unload", "Removes this chunk's server force-load ticket; Minecraft decides when it unloads."),
        Map.entry("SetForced", "Sets this chunk's server force-load ticket."),
        Map.entry("ForceLoad", "Adds a persistent server force-load ticket for this chunk."),
        Map.entry("GetBlockAt", "Loads this chunk if needed and returns the live Block instance at a world position inside it, including its registered addon subclass."),
        Map.entry("GetBlockEntityAt", "Loads this chunk if needed and returns its block entity at a world position."),
        Map.entry("GetBiomeAt", "Loads this chunk if needed and returns the biome identifier at a world position inside it."),
        Map.entry("GetFluidAt", "Loads this chunk if needed and returns the fluid identifier at a world position inside it, or nil."),
        Map.entry("GetLightAt", "Loads this chunk if needed and returns the raw local light level at a world position inside it."),
        Map.entry("GetHeightAt", "Loads this chunk if needed and returns the first free Y coordinate above the selected heightmap at a world X/Z position."),
        Map.entry("GetTopBlockAt", "Loads this chunk if needed and returns the live Block immediately below the selected heightmap's first free Y position."),
        Map.entry("SetBlockAt", "Loads this chunk if needed and sets a block and optional state properties at a world position inside it. Server-side only."),
        Map.entry("GetEntities", "Loads this chunk if needed and returns its live entities."),
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
        registerWorldProperties(registration);
        registration.registerClass("Chunk", "Instance", "A dimension-scoped chunk handle whose data methods load the chunk when needed.");
        chunkMethods().forEach((name, description) ->
            MLuaClassMethods.registerInstanceMethod(registration, "Chunk", name, description));
        String[][] chunkProperties = {
            {"X", "GetX", "number", "This chunk's X coordinate."},
            {"Z", "GetZ", "number", "This chunk's Z coordinate."},
            {"Dimension", "GetDimension", "string", "The identifier of the dimension containing this chunk."},
            {"Position", "GetPosition", "Vector2", "Chunk coordinates: X is chunk X and Y is chunk Z."},
            {"BlockOrigin", "GetBlockOrigin", "Vector3", "The minimum block-space X/Z position of this chunk."},
            {"Loaded", "IsLoaded", "boolean", "Whether this chunk is loaded now, without loading it."},
            {"Forced", "IsForced", "boolean", "Whether this chunk has a force-load ticket."}
        };
        for (String[] property : chunkProperties) {
            MLuaClassMethods.registerInstanceProperty(registration, "Chunk", property[0], property[1], null,
                property[2], "shared:Shared", property[3]);
        }
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
            Map.entry("IsChunkLoaded", "Returns whether the chunk at a position is loaded."),
            Map.entry("IsBlockLoaded", "Returns whether the chunk containing a position is loaded."),
            Map.entry("LoadChunk", "Loads or generates the chunk containing a position in this dimension."),
            Map.entry("GetChunk", "Returns a live Chunk instance for a position without loading it. Repeated lookups reuse the same instance while it is referenced."),
            Map.entry("GetChunkAt", "Returns a live Chunk instance for a position without loading it. Repeated lookups reuse the same instance while it is referenced."),
            Map.entry("UnloadChunk", "Removes this dimension's force-load ticket; Minecraft unloads it when no other tickets need it."),
            Map.entry("SetChunkForced", "Sets whether this dimension keeps a chunk force-loaded."),
            Map.entry("ForceLoadChunk", "Keeps a chunk force-loaded in this dimension until its force-load ticket is removed."),
            Map.entry("IsChunkForced", "Returns whether this dimension has a force-load ticket for the chunk."),
            Map.entry("GetEntitiesInChunk", "Returns entities in a chunk, optionally loading it first."),
            Map.entry("GetTime", "Returns this world's day time in ticks."),
            Map.entry("GetDayTime", "Returns the current day cycle time in ticks."),
            Map.entry("GetGameTime", "Returns the total number of ticks this world has been running."),
            Map.entry("GetMinBuildHeight", "Returns this world's minimum build height."),
            Map.entry("GetMaxBuildHeight", "Returns this world's maximum build height."),
            Map.entry("GetSpawnPosition", "Returns this world's shared spawn position."),
            Map.entry("SetSpawnPosition", "Sets this world's shared respawn position and yaw. This method is server-side only."),
            Map.entry("IsDay", "Returns whether it is daytime in this world."),
            Map.entry("IsNight", "Returns whether it is nighttime in this world."),
            Map.entry("SetTime", "Sets this world's day time. This method is server-side only."),
            Map.entry("AddTime", "Adds ticks to this world's day time. This method is server-side only."),
            Map.entry("GetWeather", "Returns this world's current weather."),
            Map.entry("SetWeather", "Sets this world's weather. This method is server-side only."),
            Map.entry("GetDifficulty", "Returns this world's difficulty."),
            Map.entry("SetDifficulty", "Sets the server difficulty."),
            Map.entry("GetBlock", "Returns the live block instance at a position."),
            Map.entry("GetBlockEntity", "Returns the live block entity at a position, or nil."),
            Map.entry("GetBlockState", "Returns the live block instance at a position."),
            Map.entry("GetFluid", "Returns the fluid identifier at a position, or nil when empty."),
            Map.entry("GetBiome", "Returns the biome identifier at a position."),
            Map.entry("GetLightLevel", "Returns the maximum local light level at a position."),
            Map.entry("GetSkyLight", "Returns the sky light level at a position."),
            Map.entry("GetBlockLight", "Returns the block light level at a position."),
            Map.entry("CanSeeSky", "Returns whether the sky is visible from a position."),
            Map.entry("IsRaining", "Returns whether it is raining in this world."),
            Map.entry("IsThundering", "Returns whether a thunderstorm is active in this world."),
            Map.entry("IsRainingAt", "Returns whether rain reaches a position."),
            Map.entry("GetSurfaceY", "Returns the motion-blocking surface height at an X/Z column, or nil when its chunk is not loaded."),
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
            Map.entry("SetBlock", "Sets a block and optional state properties at a position, loading its chunk by default. This method is server-side only."),
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
            Map.entry("ListGameRules", "Returns the names and current values of this world's gamerules. This method is server-side only."),
            Map.entry("GetGameRule", "Returns a world gamerule value by name. This method is server-side only."),
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
        return WORLD_SERVICE_METHODS;
    }

    private static Map<String, String> createWorldServiceMethods() {
        Map<String, String> methods = new LinkedHashMap<>();
        methods.put("Current", "Returns the client world on the client or the overworld on the server.");
        methods.put("Get", "Returns the live World instance for a loaded dimension identifier.");
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
        globals.set("Chunk", context.registration().classTable("Chunk"));
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("World", "World:IsChunkLoaded(coordinates: Vector2|Vector3|CFrame)", "boolean");
        docs.signature("World", "World:IsBlockLoaded(position: Vector3|CFrame)", "boolean");
        docs.signature("World", "World:GetChunkAt(coordinates: Vector2|Vector3|CFrame)", "Chunk");
        docs.signature("World", "World:LoadChunk(coordinates: Vector2|Vector3|CFrame)", "Chunk");
        docs.signature("World", "World:GetChunk(coordinates: Vector2|Vector3|CFrame)", "Chunk");
        docs.signature("World", "World:UnloadChunk(coordinates: Vector2|Vector3|CFrame)", "boolean");
        docs.signature("World", "World:SetChunkForced(coordinates: Vector2|Vector3|CFrame, forced: boolean)", "boolean");
        docs.signature("World", "World:ForceLoadChunk(coordinates: Vector2|Vector3|CFrame)", "boolean");
        docs.signature("World", "World:IsChunkForced(coordinates: Vector2|Vector3|CFrame)", "boolean");
        docs.signature("World", "World:GetEntitiesInChunk(coordinates: Vector2|Vector3|CFrame, loadChunk: boolean?)", "Entity[]");
        docs.signature("World", "World:SetBlock(position: Vector3|CFrame, blockId: string, properties: BlockStateProperties?, loadChunk: boolean?)", "boolean");
        docs.signature("World", "World:SetSpawnPosition(position: Vector3|CFrame, yaw: number?)", "boolean");
        docs.signature("World", "World:ListGameRules()", "GameRuleList");
        docs.signature("World", "World:GetGameRule(name: string)", "boolean|number");
        docs.signature("World", "World:SetGameRule(name: string, value: boolean|number)", "void");
        docs.signature("Chunk", "Chunk:GetX()", "number");
        docs.signature("Chunk", "Chunk:GetZ()", "number");
        docs.signature("Chunk", "Chunk:GetDimension()", "string");
        docs.signature("Chunk", "Chunk:GetPosition()", "Vector2");
        docs.signature("Chunk", "Chunk:GetBlockOrigin()", "Vector3");
        docs.signature("Chunk", "Chunk:IsLoaded()", "boolean");
        docs.signature("Chunk", "Chunk:IsForced()", "boolean");
        docs.signature("Chunk", "Chunk:Load()", "Chunk");
        docs.signature("Chunk", "Chunk:Unload()", "boolean");
        docs.signature("Chunk", "Chunk:SetForced(forced: boolean)", "boolean");
        docs.signature("Chunk", "Chunk:ForceLoad()", "boolean");
        docs.signature("Chunk", "Chunk:GetBlockAt(position: Vector3|CFrame)", "Block?");
        docs.signature("Chunk", "Chunk:GetBlockEntityAt(position: Vector3|CFrame)", "BlockEntity?");
        docs.signature("Chunk", "Chunk:GetBiomeAt(position: Vector3|CFrame)", "string?");
        docs.signature("Chunk", "Chunk:GetFluidAt(position: Vector3|CFrame)", "string?");
        docs.signature("Chunk", "Chunk:GetLightAt(position: Vector3|CFrame)", "number?");
        docs.signature("Chunk", "Chunk:GetHeightAt(position: Vector3|CFrame, heightmap: string?)", "number?");
        docs.signature("Chunk", "Chunk:GetTopBlockAt(position: Vector3|CFrame, heightmap: string?)", "Block?");
        docs.signature("Chunk", "Chunk:SetBlockAt(position: Vector3|CFrame, blockId: string, properties: BlockStateProperties?)", "boolean");
        docs.signature("Chunk", "Chunk:GetEntities()", "Entity[]");
        docs.signature("Chunk", "Chunk:GetBlockEntities()", "BlockEntity[]");
        docs.signature("WorldService", "WorldService:Get(dimensionId: string)", "World?");
        docs.signature("WorldService", "WorldService:GetAll()", "World[]");
        docs.signature("WorldService", "WorldService:ListGameRules()", "GameRuleList");
        docs.signature("WorldService", "WorldService:GetGameRule(name: string)", "boolean|number");
        docs.signature("WorldService", "WorldService:SetGameRule(name: string, value: boolean|number)", "void");
        docs.signature("WorldService", "WorldService:IsChunkLoaded(coordinates: Vector2|Vector3|CFrame)", "boolean");
        docs.signature("WorldService", "WorldService:IsBlockLoaded(position: Vector3|CFrame)", "boolean");
        docs.signature("WorldService", "WorldService:LoadChunk(coordinates: Vector2|Vector3|CFrame)", "Chunk");
        docs.signature("WorldService", "WorldService:GetChunk(coordinates: Vector2|Vector3|CFrame)", "Chunk");
        docs.signature("WorldService", "WorldService:GetChunkAt(coordinates: Vector2|Vector3|CFrame)", "Chunk");
        docs.signature("WorldService", "WorldService:UnloadChunk(coordinates: Vector2|Vector3|CFrame)", "boolean");
        docs.signature("WorldService", "WorldService:SetChunkForced(coordinates: Vector2|Vector3|CFrame, forced: boolean)", "boolean");
        docs.signature("WorldService", "WorldService:ForceLoadChunk(coordinates: Vector2|Vector3|CFrame)", "boolean");
        docs.signature("WorldService", "WorldService:IsChunkForced(coordinates: Vector2|Vector3|CFrame)", "boolean");
        docs.signature("WorldService", "WorldService:GetEntitiesInChunk(coordinates: Vector2|Vector3|CFrame, loadChunk: boolean?)", "Entity[]");
        docs.signature("World", "World:GetBlockEntity(position: Vector3|CFrame)", "BlockEntity?");
        docs.signature("World", "World:CanSeeSky(position: Vector3|CFrame)", "boolean");
        docs.signature("World", "World:IsRaining()", "boolean");
        docs.signature("World", "World:IsThundering()", "boolean");
        docs.signature("World", "World:IsRainingAt(position: Vector3|CFrame)", "boolean");
        docs.signature("World", "World:GetSurfaceY(position: Vector3|CFrame)", "number?");
        docs.signature("WorldService", "WorldService:GetBlockEntity(position: Vector3|CFrame)", "BlockEntity?");
        docs.signature("WorldService", "WorldService:CanSeeSky(position: Vector3|CFrame)", "boolean");
        docs.signature("WorldService", "WorldService:IsRaining()", "boolean");
        docs.signature("WorldService", "WorldService:IsThundering()", "boolean");
        docs.signature("WorldService", "WorldService:IsRainingAt(position: Vector3|CFrame)", "boolean");
        docs.signature("WorldService", "WorldService:GetSurfaceY(position: Vector3|CFrame)", "number?");
        docs.signature("WorldService", "WorldService:GetBlock(position: Vector3|CFrame)", "Block");
        docs.signature("WorldService", "WorldService:SetSpawnPosition(position: Vector3|CFrame, yaw: number?)", "boolean");
        docs.signature("WorldService", "WorldService:GetFluid(position: Vector3|CFrame)", "string?");
        docs.signature("WorldService", "WorldService:GetBiome(position: Vector3|CFrame)", "string");
        docs.signature("WorldService", "WorldService:GetLightLevel(position: Vector3|CFrame)", "number");
        docs.signature("WorldService", "WorldService:GetSkyLight(position: Vector3|CFrame)", "number");
        docs.signature("WorldService", "WorldService:GetBlockLight(position: Vector3|CFrame)", "number");
        docs.signature("WorldService", "WorldService:GetBlockInventory(position: Vector3|CFrame)", "Container?");
        docs.signature("WorldService", "WorldService:SetBlock(position: Vector3|CFrame, blockId: string, properties: BlockStateProperties?, loadChunk: boolean?)", "boolean");
        docs.type("BlockStateProperties", null, "Property names and values supported by the selected Minecraft block. Values are strings, numbers, or booleans and are validated against that block's state definition.");
        docs.field("BlockStateProperties", "*", "string|number|boolean", "A block state property, such as facing = \"north\" or waterlogged = false.");
        docs.type("GameRuleList", null, "ListGameRules returns numeric entries containing Name and Value, plus direct lookup by gamerule name. Gamerule changes and reads require a server World.");
        docs.field("GameRuleList", "*", "GameRuleEntry|boolean|number", "An ordered GameRuleEntry at a numeric index, or the current value when indexed by gamerule name.");
        docs.type("GameRuleEntry", null, "One registered Minecraft gamerule and its current value.");
        docs.field("GameRuleEntry", "Name", "string", "The gamerule name accepted by GetGameRule and SetGameRule.");
        docs.field("GameRuleEntry", "Value", "boolean|number", "The current boolean or integer gamerule value.");
        docs.signature("WorldService", "WorldService:BreakBlock(position: Vector3|CFrame, dropItems: boolean?)", "boolean");
        docs.signature("WorldService", "WorldService:PlaySound(position: Vector3|CFrame, sound: string, volume: number?, pitch: number?)", "void");
        docs.signature("WorldService", "WorldService:SpawnParticle(position: Vector3|CFrame, particle: string, count: number?)", "void");
        docs.signature("WorldService", "WorldService:SpawnEntity(entityId: string, position: Vector3|CFrame, yaw: number?, pitch: number?)", "Entity?");
        docs.signature("WorldService", "WorldService:DropItem(position: Vector3|CFrame, itemId: string, count: number?)", "Entity?");
        docs.signature("WorldService", "WorldService:Explode(position: Vector3|CFrame, power: number?, fire: boolean?)", "void");
        docs.signature("WorldService", "WorldService:SpawnLightning(position: Vector3|CFrame)", "Entity?");
        docs.signature("WorldService", "WorldService:FindEntitiesInRadius(center: Vector3|CFrame, radius: number, filters: EntityQuery?)", "Entity[]");
        docs.signature("WorldService", "WorldService:FindEntitiesInBox(minimum: Vector3|CFrame, maximum: Vector3|CFrame, filters: EntityQuery?)", "Entity[]");
        docs.signature("WorldService", "WorldService:FindPlayersInRadius(center: Vector3|CFrame, radius: number)", "Player[]");
        docs.signature("WorldService", "WorldService:FindNearestPlayer(position: Vector3|CFrame, maxDistance: number?)", "Player?");
        docs.signature("WorldService", "WorldService:FindNearestEntity(position: Vector3|CFrame, maxDistance: number?, filters: EntityQuery?)", "Entity?");
        docs.signature("WorldService", "WorldService:Raycast(origin: Vector3|CFrame, direction: Vector3, distance: number?, includeFluids: boolean?)", "table?");
        docs.type("EntityQuery", null, "Filters accepted by world entity search methods.");
        docs.field("EntityQuery", "type", "string", "Required registered entity type identifier.");
        docs.field("EntityQuery", "players_only", "boolean", "Include only player entities when true.");
        docs.field("EntityQuery", "exclude_players", "boolean", "Exclude player entities when true.");
        docs.field("EntityQuery", "alive", "boolean", "Require the entity's alive state to match.");
        docs.field("EntityQuery", "name", "string", "Require an exact entity name.");
        docs.field("EntityQuery", "tag", "string", "Require this saved MLua entity tag.");
        docs.field("EntityQuery", "tags", "string[]", "Require every listed saved MLua entity tag.");
        docs.field("EntityQuery", "exclude_tags", "string[]", "Exclude entities with any listed saved MLua tag.");
        docs.clazz("Level", "MLuaLevelWrapper", "A live Minecraft world instance, with dimension, time, and block access.", "classes/level.html", "Instance");
        docs.clazz("World", null, "A live world instance for one dimension, with time, block, entity, and chunk APIs.", "classes/world.html", "Level");
        docs.clazz("Chunk", "MLuaChunkWrapper", "A live handle to one chunk in one dimension. Data methods load the chunk when needed.", "classes/chunk.html", "Instance");
        docs.property("Chunk", "chunk.X", "number", "This chunk's X coordinate.", "shared:Shared");
        docs.property("Chunk", "chunk.Z", "number", "This chunk's Z coordinate.", "shared:Shared");
        docs.property("Chunk", "chunk.Dimension", "string", "The identifier of the dimension containing this chunk.", "shared:Shared");
        docs.property("Chunk", "chunk.Position", "Vector2", "Chunk coordinates: X is chunk X and Y is chunk Z.", "shared:Shared");
        docs.property("Chunk", "chunk.BlockOrigin", "Vector3", "The minimum block-space X/Z position of this chunk.", "shared:Shared");
        docs.property("Chunk", "chunk.Loaded", "boolean", "Whether this chunk is currently loaded, without loading it.", "shared:Shared");
        docs.property("Chunk", "chunk.Forced", "boolean", "Whether this chunk has a force-load ticket.", "shared:Shared");
        docs.property("World", "world.Raining", "boolean", "Whether it is raining in this world.", "shared:Shared");
        docs.property("World", "world.Thundering", "boolean", "Whether a thunderstorm is active in this world.", "shared:Shared");
        docs.property("World", "world.SpawnPosition", "Vector3", "This world's shared spawn position; assign a native Vector3 or CFrame to change it on the server.", "shared:Shared");
        docs.service("World", "MLuaWorldService", "Provides live world instances for loaded dimensions.", "services/world.html");
        docs.guideStep("World", "Load and edit chunks in a dimension",
            "Each World instance is one dimension. Vector2 means chunk coordinates (X, Z); Vector3 and CFrame select the chunk containing a block position. GetChunk returns a live Chunk handle without loading it. Data methods load that chunk when called. Chunk:GetHeightAt returns the first free Y value, and Chunk:GetTopBlockAt returns the live registered Block instance below it. Both accept a Minecraft heightmap name, defaulting to MOTION_BLOCKING. SetBlock and SetBlockAt accept an optional property table for states such as stair facing and waterlogging. Property names and values follow the block's Minecraft state definition. LoadChunk loads immediately; ForceLoad adds a persistent ticket, while Unload removes that ticket and lets Minecraft decide when to unload.",
            "local worlds = Minecraft:GetService(\"World\")\nlocal nether = worlds:Get(\"minecraft:the_nether\")\nlocal coordinates = Vector2.new(2, -1)\nlocal blockPosition = Vector3.new(32, 70, -16)\nlocal chunk = nether:GetChunk(coordinates)\n\nchunk:SetBlockAt(blockPosition, \"minecraft:oak_stairs\", { facing = \"north\", half = \"bottom\", waterlogged = false })\nlocal biome = chunk:GetBiomeAt(blockPosition)\nlocal fluid = chunk:GetFluidAt(blockPosition)\nlocal loaded = nether:LoadChunk(coordinates)\nprint(loaded.Loaded, loaded.Position, biome, fluid)\n\nchunk:ForceLoad()\nprint(chunk.Forced)\nchunk:Unload()");
        docs.guideStep("World", "Find and remove entities in a chunk",
            "GetEntitiesInChunk can load an unloaded chunk first. It returns live Entity instances, so use their Entity methods to remove or kill them. This operates only in the World instance's dimension.",
            "local worlds = Minecraft:GetService(\"World\")\nlocal world = worlds:Get(\"minecraft:overworld\")\nlocal position = Vector3.new(32, 70, -16)\n\nfor _, entity in ipairs(world:GetEntitiesInChunk(position, true)) do\n    if entity:IsA(\"Mob\") then\n        entity:Remove()\n    end\nend\n\nlocal spawned = Minecraft:GetService(\"EntityService\"):Spawn(\"minecraft:zombie\", position, world)");
        docs.guideStep("World", "Set a dimension's shared spawn point",
            "SetSpawnPosition takes a native Vector3 or CFrame and an optional yaw in degrees. It is server-side only; SpawnPosition can also be assigned directly.",
            "local world = Minecraft:GetService(\"World\"):Get(\"minecraft:overworld\")\nlocal spawn = Vector3.new(0, 65, 0)\nworld:SetSpawnPosition(spawn, 90)\nworld.SpawnPosition = spawn");
        docs.guideStep("World", "Read and change dimension gamerules",
            "Gamerules belong to a dimension. These methods are server-side only. GetGameRule returns a boolean or integer; SetGameRule accepts the matching Lua value and errors for an unknown name or incompatible value. ListGameRules can be traversed with ipairs or indexed by the rule name.",
            "local world = Minecraft:GetService(\"World\"):Get(\"minecraft:overworld\")\nlocal oldValue = world:GetGameRule(\"doDaylightCycle\")\nprint(\"Daylight cycle:\", oldValue)\n\nworld:SetGameRule(\"doDaylightCycle\", false)\nfor _, rule in ipairs(world:ListGameRules()) do\n    print(rule.Name, rule.Value)\nend\n\nlocal rules = world:ListGameRules()\nprint(\"Mob griefing:\", rules.mobGriefing)");
        docs.guideStep("WorldService", "Select the dimension for world operations",
            "Each World value represents one dimension. Get selects a loaded dimension by identifier. Current returns the active client dimension on the client and the overworld on the server; when acting on a player in another dimension, use the player's dimension identifier with Get. World changes are server-side operations.",
            "local WorldService = Minecraft:GetService(\"World\")\nlocal world = WorldService:Current()\n\nif world then\n    print(world.Id, world.Time)\n    world:SetTime(world.Time + 100)\nend\n\nWorldService:SetWeather(Enum.Weather.Rain)\nlocal nether = WorldService:Get(\"minecraft:the_nether\")\nif nether then\n    print(nether:GetTime())\nend");
        docs.guideStep("World", "Query surface, sky, and weather around a position",
            "GetSurfaceY reads the motion-blocking height for the X/Z column and returns nil when that chunk is not loaded. CanSeeSky and IsRainingAt use the exact block position.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal position = Vector3.new(0, 64, 0)\nlocal surfaceY = world:GetSurfaceY(position)\nif surfaceY then\n    print(\"Surface Y:\", surfaceY)\n    print(\"Sky visible:\", world:CanSeeSky(position))\n    print(\"Rain reaches position:\", world:IsRainingAt(position))\nend");
        docs.guideStep("World", "Reach a block entity and its container",
            "GetBlockEntity returns the live registered BlockEntity subclass at a position. Scripted block entities can expose their own methods and a shared Container instance.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal blockEntity = world:GetBlockEntity(Vector3.new(0, 64, 0))\nif blockEntity and blockEntity:HasContainer() then\n    local container = blockEntity:GetContainer()\n    print(\"Slots:\", container:GetSize())\n    for _, slot in ipairs(container:GetAll()) do\n        if not slot:IsEmpty() then\n            print(slot:GetItem():GetId(), slot:GetCount())\n        end\n    end\nend");
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
