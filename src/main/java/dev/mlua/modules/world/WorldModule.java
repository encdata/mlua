package dev.mlua.modules.world;

import dev.mlua.modules.world.Service.MLuaWorldService;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.MLuaContextView;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.minecraft.binding.MLuaFluidStateWrapper;
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
        MLuaBlockStateWrapper.classTable(registration);
        MLuaFluidStateWrapper.classTable(registration);
        registration.registerClass("Chunk", "Instance", "A dimension-scoped chunk handle whose data methods load the chunk when needed.");
        chunkMethods().forEach((name, description) ->
            MLuaClassMethods.registerInstanceMethod(registration, "Chunk", name, description));
        registration.registerClass("RaycastResult", registration.requireInstanceBaseClassName(),
            "A world ray hit with a typed hit position, surface normal, and distance.");
        String[][] raycastProperties = {
            {"HitType", "string", "string", "The kind of ray hit: block or entity."},
            {"Position", "Vector3", "Vector3", "The point where the ray intersects the entity bounds."},
            {"Normal", "Vector3", "Vector3", "The outward surface direction at the hit point."},
            {"Distance", "number", "number", "Distance from the ray origin to the hit point."}
        };
        for (String[] property : raycastProperties) {
            registration.registerClassFunction("RaycastResult", "Get" + property[0], new org.luaj.vm2.lib.OneArgFunction() {
                @Override public LuaValue call(LuaValue self) { return self.checktable().get(property[0]); }
            }, property[3]);
            MLuaClassMethods.registerInstanceProperty(registration, "RaycastResult", property[0],
                "Get" + property[0], null, property[1], "shared:Shared", property[3]);
        }
        registration.registerClass("EntityRaycastResult", "RaycastResult",
            "The nearest entity and hit point returned by a world's entity raycast.");
        registration.registerClassFunction("EntityRaycastResult", "GetEntity", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().get("Entity"); }
        }, "Returns the live entity intersected by the ray.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityRaycastResult", "Entity", "GetEntity", null,
            "Entity", "shared:Shared", "The live entity intersected by the ray.");
        registration.registerClass("BlockRaycastResult", "RaycastResult",
            "The first colliding block and hit point returned by a world's block raycast.");
        String[][] blockRaycastProperties = {
            {"Block", "string", "The registered block identifier hit by the ray."},
            {"Face", "string", "The face of the block hit by the ray."}
        };
        for (String[] property : blockRaycastProperties) {
            registration.registerClassFunction("BlockRaycastResult", "Get" + property[0], new org.luaj.vm2.lib.OneArgFunction() {
                @Override public LuaValue call(LuaValue self) { return self.checktable().get(property[0]); }
            }, property[2]);
            MLuaClassMethods.registerInstanceProperty(registration, "BlockRaycastResult", property[0],
                "Get" + property[0], null, property[1], "shared:Shared", property[2]);
        }
        registration.registerClassFunction("BlockRaycastResult", "GetBlockInstance", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().get("BlockInstance"); }
        }, "Returns the live Block instance at the hit position, including its registered subclass.");
        MLuaClassMethods.registerInstanceProperty(registration, "BlockRaycastResult", "BlockInstance", "GetBlockInstance", null,
            "Block", "shared:Shared", "The live Block instance at the hit position.");
        registration.registerClass("GameRuleEntry", registration.requireInstanceBaseClassName(),
            "One registered Minecraft gamerule and its current value.");
        for (String property : List.of("Name", "Value")) {
            String valueType = property.equals("Name") ? "string" : "boolean|number|string";
            String description = property.equals("Name")
                ? "The gamerule name accepted by GetGameRule and SetGameRule."
                : "The current gamerule value with its Minecraft type.";
            registration.registerClassFunction("GameRuleEntry", "Get" + property, new org.luaj.vm2.lib.OneArgFunction() {
                @Override public LuaValue call(LuaValue self) { return self.checktable().get(property); }
            }, description);
            MLuaClassMethods.registerInstanceProperty(registration, "GameRuleEntry", property,
                "Get" + property, null, valueType, "server:Server", description);
        }
        registration.registerClassFunction("GameRuleEntry", "GetValueType", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                LuaValue value = self.checktable().get("Value");
                return LuaValue.valueOf(value.isboolean() ? "boolean" : value.isnumber() ? "number" : "string");
            }
        }, "Returns whether this gamerule value is boolean, number, or string.");
        MLuaClassMethods.registerInstanceProperty(registration, "GameRuleEntry", "ValueType", "GetValueType", null,
            "string", "server:Server", "The Lua value type of this gamerule's value.");
        registration.registerClassFunction("GameRuleEntry", "IsBoolean", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(self.checktable().get("Value").isboolean()); }
        }, "Returns whether this gamerule stores a boolean value.");
        registration.registerClassFunction("GameRuleEntry", "IsNumber", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(self.checktable().get("Value").isnumber()); }
        }, "Returns whether this gamerule stores a numeric value.");
        registration.registerClassFunction("GameRuleEntry", "IsString", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return LuaValue.valueOf(self.checktable().get("Value").isstring()); }
        }, "Returns whether this gamerule stores a string value.");
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
            Map.entry("GetBlockState", "Returns a BlockState instance with the block type, position, and current state properties."),
            Map.entry("SetBlockState", "Places an immutable BlockState at a position, loading its chunk by default. This method is server-side only."),
            Map.entry("GetFluid", "Returns the fluid identifier at a position, or nil when empty."),
            Map.entry("GetFluidState", "Returns the live position-aware FluidState at a position."),
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
            Map.entry("RaycastEntity", "Returns the nearest matching entity hit before a colliding block."),
            Map.entry("GetPlayers", "Returns the players currently in this world."),
            Map.entry("GetEntities", "Returns the entities currently in this world."),
            Map.entry("FindEntitiesInRadius", "Returns entities within a radius of a position."),
            Map.entry("GetEntitiesInRadius", "Returns entities within a radius of a position."),
            Map.entry("GetEntitiesNearby", "Returns entities within a radius of a position."),
            Map.entry("FindEntitiesInBox", "Returns entities within an axis-aligned box."),
            Map.entry("FindEntitiesInBounds", "Returns entities within an EntityBounds instance."),
            Map.entry("FindPlayersInBounds", "Returns players whose positions lie within an EntityBounds instance."),
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
            Map.entry("GetGameRuleEntry", "Returns one typed gamerule entry by name. This method is server-side only."),
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
        globals.set("BlockState", context.registration().classTable("BlockState"));
        globals.set("FluidState", context.registration().classTable("FluidState"));
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
        docs.signature("World", "World:GetBlockState(position: Vector3|CFrame)", "BlockState");
        docs.signature("World", "World:SetBlockState(position: Vector3|CFrame, state: BlockState, loadChunk: boolean?)", "boolean");
        docs.signature("World", "World:GetFluidState(position: Vector3|CFrame)", "FluidState");
        docs.signature("BlockState", "BlockState:GetId()", "string");
        docs.signature("BlockState", "BlockState:GetBlock()", "Block");
        docs.signature("BlockState", "BlockState:GetPosition()", "Vector3");
        docs.signature("BlockState", "BlockState:GetProperties()", "BlockStateProperties");
        docs.signature("BlockState", "BlockState:GetProperty(name: string)", "string|number|boolean|nil");
        docs.signature("BlockState", "BlockState:WithProperty(name: string, value: string|number|boolean)", "BlockState");
        docs.signature("BlockState", "BlockState:WithProperties(properties: BlockStateProperties)", "BlockState");
        docs.signature("BlockState", "BlockState:HasProperty(name: string)", "boolean");
        docs.signature("BlockState", "BlockState:IsAir()", "boolean");
        docs.signature("BlockState", "BlockState:IsSolid()", "boolean");
        docs.signature("BlockState", "BlockState:IsReplaceable()", "boolean");
        docs.signature("BlockState", "BlockState:GetFluid()", "string|nil");
        docs.signature("BlockState", "BlockState:GetFluidState()", "FluidState");
        docs.signature("BlockState", "BlockState:HasBlockEntity()", "boolean");
        docs.signature("BlockState", "BlockState:IsRandomlyTicking()", "boolean");
        docs.signature("BlockState", "BlockState:CanSurvive()", "boolean");
        docs.signature("BlockState", "BlockState:GetLightEmission()", "number");
        docs.signature("BlockState", "BlockState:IsSignalSource()", "boolean");
        docs.signature("BlockState", "BlockState:GetAnalogOutputSignal()", "number");
        docs.signature("BlockState", "BlockState:CanOcclude()", "boolean");
        docs.signature("BlockState", "BlockState:IsViewBlocking()", "boolean");
        docs.signature("BlockState", "BlockState:GetLightBlock()", "number");
        docs.signature("BlockState", "BlockState:GetDestroySpeed()", "number");
        docs.signature("FluidState", "FluidState:GetId()", "string|nil");
        docs.signature("FluidState", "FluidState:GetPosition()", "Vector3");
        docs.signature("FluidState", "FluidState:IsEmpty()", "boolean");
        docs.signature("FluidState", "FluidState:IsSource()", "boolean");
        docs.signature("FluidState", "FluidState:GetAmount()", "number");
        docs.signature("FluidState", "FluidState:GetHeight()", "number");
        docs.signature("FluidState", "FluidState:GetBlockState()", "BlockState");
        docs.signature("World", "World:SetSpawnPosition(position: Vector3|CFrame, yaw: number?)", "boolean");
        docs.signature("World", "World:ListGameRules()", "GameRuleList");
        docs.signature("World", "World:GetGameRule(name: string)", "boolean|number|string");
        docs.signature("World", "World:GetGameRuleEntry(name: string): GameRuleEntry?", null);
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
        docs.signature("WorldService", "WorldService:GetGameRule(name: string)", "boolean|number|string");
        docs.signature("WorldService", "WorldService:GetGameRuleEntry(name: string): GameRuleEntry?", null);
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
        docs.field("GameRuleList", "*", "GameRuleEntry|boolean|number|string", "An ordered GameRuleEntry at a numeric index, or the current value when indexed by gamerule name.");
        docs.clazz("GameRuleEntry", "WorldModule", "One registered Minecraft gamerule and its current value.", "classes/gamerule-entry.html", "Instance");
        docs.property("GameRuleEntry", "GameRuleEntry.Name", "string", "The gamerule name accepted by GetGameRule and SetGameRule.", "read:Read");
        docs.property("GameRuleEntry", "GameRuleEntry.Value", "boolean|number|string", "The current value, represented as a boolean, number, or string according to that rule's type.", "read:Read");
        docs.property("GameRuleEntry", "GameRuleEntry.ValueType", "string", "The Lua value type of this gamerule's value.", "read:Read");
        docs.method("GameRuleEntry", "GameRuleEntry:GetValueType(): string", "Returns the Lua value type of this gamerule's value.", null, "string", "server:Server", null);
        docs.method("GameRuleEntry", "GameRuleEntry:IsBoolean(): boolean", "Checks whether this gamerule stores a boolean value.", null, "boolean", "server:Server", null);
        docs.method("GameRuleEntry", "GameRuleEntry:IsNumber(): boolean", "Checks whether this gamerule stores a numeric value.", null, "boolean", "server:Server", null);
        docs.method("GameRuleEntry", "GameRuleEntry:IsString(): boolean", "Checks whether this gamerule stores a string value.", null, "boolean", "server:Server", null);
        docs.signature("WorldService", "WorldService:BreakBlock(position: Vector3|CFrame, dropItems: boolean?)", "boolean");
        docs.signature("WorldService", "WorldService:PlaySound(position: Vector3|CFrame, sound: string, volume: number?, pitch: number?)", "void");
        docs.signature("WorldService", "WorldService:SpawnParticle(position: Vector3|CFrame, particle: string, count: number?)", "void");
        docs.signature("WorldService", "WorldService:SpawnEntity(entityId: string, position: Vector3|CFrame, yaw: number?, pitch: number?)", "Entity?");
        docs.signature("WorldService", "WorldService:DropItem(position: Vector3|CFrame, itemId: string, count: number?)", "Entity?");
        docs.signature("WorldService", "WorldService:Explode(position: Vector3|CFrame, power: number?, fire: boolean?)", "void");
        docs.signature("WorldService", "WorldService:SpawnLightning(position: Vector3|CFrame)", "Entity?");
        docs.signature("WorldService", "WorldService:FindEntitiesInRadius(center: Vector3|CFrame, radius: number, filters: EntityQuery?)", "Entity[]");
        docs.signature("WorldService", "WorldService:FindEntitiesInBox(minimum: Vector3|CFrame, maximum: Vector3|CFrame, filters: EntityQuery?)", "Entity[]");
        docs.signature("WorldService", "WorldService:FindEntitiesInBounds(bounds: EntityBounds, filters: EntityQuery?)", "Entity[]");
        docs.signature("WorldService", "WorldService:FindPlayersInBounds(bounds: EntityBounds)", "Player[]");
        docs.signature("WorldService", "WorldService:FindPlayersInRadius(center: Vector3|CFrame, radius: number)", "Player[]");
        docs.signature("WorldService", "WorldService:FindNearestPlayer(position: Vector3|CFrame, maxDistance: number?)", "Player?");
        docs.signature("WorldService", "WorldService:FindNearestEntity(position: Vector3|CFrame, maxDistance: number?, filters: EntityQuery?)", "Entity?");
        docs.signature("WorldService", "WorldService:Raycast(origin: Vector3|CFrame, direction: Vector3, distance: number?, includeFluids: boolean?): BlockRaycastResult?", null);
        docs.signature("WorldService", "WorldService:RaycastEntity(origin: Vector3|CFrame, direction: Vector3, distance: number?, filters: EntityQuery?): EntityRaycastResult?", null);
        docs.clazz("RaycastResult", "WorldModule", "A world ray hit with a typed hit position, surface normal, and distance.", "classes/raycast-result.html", "Instance");
        docs.property("RaycastResult", "RaycastResult.HitType", "string", "The kind of ray hit.", "read:Read");
        docs.property("RaycastResult", "RaycastResult.Position", "Vector3", "The point where the ray intersects the hit bounds.", "read:Read");
        docs.property("RaycastResult", "RaycastResult.Normal", "Vector3", "The outward surface direction at the hit point.", "read:Read");
        docs.property("RaycastResult", "RaycastResult.Distance", "number", "Distance from the ray origin to the hit point.", "read:Read");
        docs.clazz("EntityRaycastResult", "WorldModule", "The nearest entity and hit point returned by a world's entity raycast.", "classes/entity-raycast-result.html", "RaycastResult");
        docs.property("EntityRaycastResult", "EntityRaycastResult.Entity", "Entity", "The live entity intersected by the ray.", "read:Read");
        docs.field("EntityRaycastResult", "Type", "string", "Compatibility field for HitType.");
        docs.clazz("BlockRaycastResult", "WorldModule", "The first colliding block and hit point returned by a world's block raycast.", "classes/block-raycast-result.html", "RaycastResult");
        docs.field("BlockRaycastResult", "Type", "string", "Compatibility field for HitType.");
        docs.property("BlockRaycastResult", "BlockRaycastResult.Block", "string", "The registered block identifier hit by the ray.", "read:Read");
        docs.property("BlockRaycastResult", "BlockRaycastResult.Face", "string", "The face of the block hit by the ray.", "read:Read");
        docs.property("BlockRaycastResult", "BlockRaycastResult.BlockInstance", "Block", "The live Block instance at the hit position.", "read:Read");
        docs.guideStep("World", "Raycast from the player's view",
            "RaycastEntity finds the closest matching entity along the direction and stops at colliding blocks. Both block and entity results inherit RaycastResult; block hits also return the live block instance.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal entityHit = world:RaycastEntity(player.EyePosition, player.LookVector, 32, { exclude = player })\nif entityHit then\n    print(entityHit:IsA(\"RaycastResult\"), entityHit.Entity.Name, entityHit.Distance, entityHit.Normal)\nend\n\nlocal blockHit = world:Raycast(player.EyePosition, player.LookVector, 32)\nif blockHit then\n    print(blockHit:IsA(\"BlockRaycastResult\"), blockHit.Block, blockHit.BlockInstance.Id, blockHit.Face, blockHit.Normal)\nend");
        docs.type("EntityQuery", null, "Filters accepted by world entity search methods.");
        docs.field("EntityQuery", "type", "string", "Match this registered entity type identifier.");
        docs.field("EntityQuery", "types", "string[]", "Allow only these registered entity type identifiers; combines with type when both are set.");
        docs.field("EntityQuery", "exclude_types", "string[]", "Exclude these registered entity type identifiers.");
        docs.field("EntityQuery", "players_only", "boolean", "Include only player entities when true.");
        docs.field("EntityQuery", "exclude_players", "boolean", "Exclude player entities when true.");
        docs.field("EntityQuery", "exclude", "Entity", "Exclude this live entity from the query results.");
        docs.field("EntityQuery", "exclude_entities", "Entity[]", "Exclude these live entity instances from the query results.");
        docs.field("EntityQuery", "alive", "boolean", "Require the entity's alive state to match.");
        docs.field("EntityQuery", "name", "string", "Require an exact entity name.");
        docs.field("EntityQuery", "name_contains", "string", "Require the entity's display name to contain this case-sensitive text.");
        docs.field("EntityQuery", "tag", "string", "Require this saved MLua entity tag.");
        docs.field("EntityQuery", "tags", "string[]", "Require every listed saved MLua entity tag.");
        docs.field("EntityQuery", "exclude_tags", "string[]", "Exclude entities with any listed saved MLua tag.");
        docs.guideStep("World", "Query several entity types",
            "Use types to include a set of registered entity identifiers and exclude_types to remove specific ones. Radius searches use a spherical distance from the center.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal hostiles = world:FindEntitiesInRadius(player.Position, 24, {\n    types = { \"minecraft:zombie\", \"minecraft:skeleton\", \"minecraft:creeper\" },\n    exclude_entities = { player, projectile }\n})\n\nfor _, entity in ipairs(hostiles) do\n    print(entity.Name, entity.Position)\nend");
        docs.clazz("Level", "MLuaLevelWrapper", "A live Minecraft world instance, with dimension, time, and block access.", "classes/level.html", "Instance");
        docs.clazz("World", null, "A live world instance for one dimension, with time, block, entity, and chunk APIs.", "classes/world.html", "Level");
        docs.clazz("BlockState", "MLuaBlockStateWrapper", "An immutable Minecraft block state at a world position, with typed state queries.", "classes/block-state.html", "Instance");
        docs.clazz("FluidState", "MLuaFluidStateWrapper", "An immutable Minecraft fluid state at a world position.", "classes/fluid-state.html", "Instance");
        docs.property("BlockState", "state.Id", "string", "The block identifier for this state.", "shared:Shared");
        docs.property("BlockState", "state.Block", "Block", "The live block instance at this state's position.", "shared:Shared");
        docs.property("BlockState", "state.Position", "Vector3", "This state's world position.", "shared:Shared");
        docs.property("BlockState", "state.Properties", "BlockStateProperties", "The current values of this state's properties.", "shared:Shared");
        docs.property("BlockState", "state.Air", "boolean", "Whether this state is air.", "shared:Shared");
        docs.property("BlockState", "state.Solid", "boolean", "Whether this state is solid.", "shared:Shared");
        docs.property("BlockState", "state.Replaceable", "boolean", "Whether another block can replace this state.", "shared:Shared");
        docs.property("BlockState", "state.Fluid", "string|nil", "The fluid identifier in this state, or nil when empty.", "shared:Shared");
        docs.property("BlockState", "state.RandomlyTicking", "boolean", "Whether this state receives random game ticks.", "shared:Shared");
        docs.property("BlockState", "state.Survivable", "boolean", "Whether this state can remain at its world position.", "shared:Shared");
        docs.property("BlockState", "state.LightEmission", "number", "The light level emitted by this state.", "shared:Shared");
        docs.property("BlockState", "state.SignalSource", "boolean", "Whether this state can provide a redstone signal.", "shared:Shared");
        docs.property("BlockState", "state.Occluding", "boolean", "Whether this state occludes neighboring faces.", "shared:Shared");
        docs.property("BlockState", "state.ViewBlocking", "boolean", "Whether this state blocks visibility at its world position.", "shared:Shared");
        docs.property("BlockState", "state.LightBlock", "number", "The light level blocked by this state at its world position.", "shared:Shared");
        docs.property("BlockState", "state.DestroySpeed", "number", "The state destroy speed in its world context.", "shared:Shared");
        docs.property("FluidState", "fluidState.Id", "string|nil", "This fluid state's registered fluid identifier.", "shared:Shared");
        docs.property("FluidState", "fluidState.Position", "Vector3", "This fluid state's world position.", "shared:Shared");
        docs.property("FluidState", "fluidState.Empty", "boolean", "Whether this position contains no fluid.", "shared:Shared");
        docs.property("FluidState", "fluidState.Source", "boolean", "Whether this is a source fluid state.", "shared:Shared");
        docs.property("FluidState", "fluidState.Amount", "number", "This fluid state's level amount.", "shared:Shared");
        docs.property("FluidState", "fluidState.Height", "number", "This fluid state's height at its world position.", "shared:Shared");
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
            "Gamerules belong to a dimension. These methods are server-side only. GetGameRule returns the rule's current value; GetGameRuleEntry returns one typed entry with its name, value, and value type. SetGameRule accepts a boolean for boolean rules and a whole 32-bit number for integer rules. Unknown rule names and mismatched values raise an error. ListGameRules can be traversed with ipairs or indexed by the rule name.",
            "local world = Minecraft:GetService(\"World\"):Get(\"minecraft:overworld\")\nlocal oldValue = world:GetGameRule(\"doDaylightCycle\")\nlocal rule = world:GetGameRuleEntry(\"doDaylightCycle\")\nprint(\"Daylight cycle:\", oldValue, rule:IsA(\"GameRuleEntry\"), rule.ValueType)\n\nworld:SetGameRule(\"doDaylightCycle\", false)\nfor _, entry in ipairs(world:ListGameRules()) do\n    print(entry.Name, entry.Value, entry:IsBoolean())\nend\n\nlocal rules = world:ListGameRules()\nprint(\"Mob griefing:\", rules.mobGriefing)");
        docs.guideStep("WorldService", "Select the dimension for world operations",
            "Each World value represents one dimension. Get selects a loaded dimension by identifier. Current returns the active client dimension on the client and the overworld on the server; when acting on a player in another dimension, use the player's dimension identifier with Get. World changes are server-side operations.",
            "local WorldService = Minecraft:GetService(\"World\")\nlocal world = WorldService:Current()\n\nif world then\n    print(world.Id, world.Time)\n    world:SetTime(world.Time + 100)\nend\n\nWorldService:SetWeather(Enum.Weather.Rain)\nlocal nether = WorldService:Get(\"minecraft:the_nether\")\nif nether then\n    print(nether:GetTime())\nend");
        docs.guideStep("World", "Query surface, sky, and weather around a position",
            "GetSurfaceY reads the motion-blocking height for the X/Z column and returns nil when that chunk is not loaded. CanSeeSky and IsRainingAt use the exact block position.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal position = Vector3.new(0, 64, 0)\nlocal surfaceY = world:GetSurfaceY(position)\nif surfaceY then\n    print(\"Surface Y:\", surfaceY)\n    print(\"Sky visible:\", world:CanSeeSky(position))\n    print(\"Rain reaches position:\", world:IsRainingAt(position))\nend");
        docs.guideStep("World", "Reach a block entity and its container",
            "GetBlockEntity returns the live registered BlockEntity subclass at a position. Scripted block entities can expose their own methods and a shared Container instance.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal blockEntity = world:GetBlockEntity(Vector3.new(0, 64, 0))\nif blockEntity and blockEntity:HasContainer() then\n    local container = blockEntity:GetContainer()\n    print(\"Slots:\", container:GetSize())\n    for _, slot in ipairs(container:GetAll()) do\n        if not slot:IsEmpty() then\n            print(slot:GetItem():GetId(), slot:GetCount())\n        end\n    end\nend");
        docs.guideStep("World", "Inspect a block state",
            "GetBlockState returns an immutable BlockState instance, while GetBlock returns the live position-aware Block instance. Read typed state properties from BlockState.Properties and check for a property before querying optional values. WithProperty creates a new state value; SetBlockState applies that state to the world on the server.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal position = Vector3.new(4, 65, 4)\nlocal state = world:GetBlockState(position)\nprint(state.Id, state.Solid, state.DestroySpeed, state.LightEmission, state.Survivable)\nif state:IsSignalSource() then\n    print(\"Analog output:\", state:GetAnalogOutputSignal())\nend\n\nif state:HasProperty(\"facing\") then\n    local rotated = state:WithProperty(\"facing\", \"east\")\n    world:SetBlockState(position, rotated)\nend\n\nlocal block = world:GetBlock(position)\nprint(block:GetClassName(), block:GetStateProperties().facing)");
        docs.guideStep("World", "Inspect fluid at a position",
            "GetFluid returns the fluid identifier for simple comparisons. GetFluidState returns the native-style FluidState instance, including whether it is empty or a source, its amount and height, and the legacy BlockState representation.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal position = Vector3.new(4, 63, 4)\nlocal fluid = world:GetFluidState(position)\nif not fluid.Empty then\n    print(fluid.Id, fluid.Source, fluid.Amount, fluid.Height)\n    local legacyState = fluid:GetBlockState()\n    print(legacyState.Id)\nend");
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
