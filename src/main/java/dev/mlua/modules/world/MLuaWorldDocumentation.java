package dev.mlua.modules.world;

import dev.mlua.module.MLuaDocBuilder;

/** Owns the published World, Chunk, BlockState, and dimension guide entries. */
final class MLuaWorldDocumentation {
    private MLuaWorldDocumentation() { }

    static void contribute(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:IsChunkLoaded(coordinates: Vector2|Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:IsBlockLoaded(position: Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:GetChunkAt(coordinates: Vector2|Vector3|CFrame)", "Chunk"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:LoadChunk(coordinates: Vector2|Vector3|CFrame)", "Chunk"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:GetChunk(coordinates: Vector2|Vector3|CFrame)", "Chunk"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:SetTime(ticks: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:AddTime(ticks: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:UnloadChunk(coordinates: Vector2|Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:SetChunkForced(coordinates: Vector2|Vector3|CFrame, forced: boolean)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:ForceLoadChunk(coordinates: Vector2|Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:IsChunkForced(coordinates: Vector2|Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:GetEntitiesInChunk(coordinates: Vector2|Vector3|CFrame, loadChunk: boolean?)", "Entity[]"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:SetBlock(position: Vector3|CFrame, block: string|BlockState, properties: BlockStateProperties?, loadChunk: boolean?, updateFlags: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:SetBlock(position: Vector3|CFrame, block: string|BlockState, properties: BlockStateProperties, updateFlags: number)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:SetBlock(position: Vector3|CFrame, block: string|BlockState, loadChunk: boolean, updateFlags: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:SetBlock(position: Vector3|CFrame, block: string|BlockState, updateFlags: number)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:Raycast(origin: Vector3|CFrame, direction: Vector3, distance: number?, includeFluids: boolean?)", "BlockRaycastResult?"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:GetBlockState(position: Vector3|CFrame)", "BlockState"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:SetBlockState(position: Vector3|CFrame, state: BlockState, loadChunk: boolean?, updateFlags: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:SetBlockState(position: Vector3|CFrame, state: BlockState, updateFlags: number)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:GetFluidState(position: Vector3|CFrame)", "FluidState"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetId()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetBlock()", "Block"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetPosition()", "Vector3"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetProperties()", "BlockStateProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:SetStateProperties(properties: BlockStateProperties, updateFlags: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:Set(blockId: string, properties: BlockStateProperties?, updateFlags: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:Set(blockId: string, updateFlags: number)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:Break(dropItems: boolean?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:ScheduleTick(delay: whole number >= 1)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetProperty(name: string)", "string|number|boolean|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:WithProperty(name: string, value: string|number|boolean)", "BlockState"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:WithProperties(properties: BlockStateProperties)", "BlockState"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:HasProperty(name: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:IsAir()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:IsSolid()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:IsReplaceable()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetFluid()", "string|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetFluidState()", "FluidState"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:HasBlockEntity()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:IsRandomlyTicking()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:CanSurvive()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetLightEmission()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:IsSignalSource()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:IsRedstoneConductor()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:HasAnalogOutputSignal()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:IsFaceSturdy(direction: Enum.Direction|string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:IsCollisionShapeFullBlock()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetAnalogOutputSignal()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetSignal(direction: Enum.Direction|string)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetDirectSignal(direction: Enum.Direction|string)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:CanOcclude()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:IsViewBlocking()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetLightBlock()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockState", "BlockState:GetDestroySpeed()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("FluidState", "FluidState:GetId()", "string|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("FluidState", "FluidState:GetPosition()", "Vector3"));
        docs.register(MLuaDocBuilder.Registration.signature("FluidState", "FluidState:IsEmpty()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("FluidState", "FluidState:IsSource()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("FluidState", "FluidState:GetAmount()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("FluidState", "FluidState:GetHeight()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("FluidState", "FluidState:GetOwnHeight()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("FluidState", "FluidState:GetFlow()", "Vector3"));
        docs.register(MLuaDocBuilder.Registration.signature("FluidState", "FluidState:IsRandomlyTicking()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("FluidState", "FluidState:GetBlockState()", "BlockState"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:SetSpawnPosition(position: Vector3|CFrame, yaw: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:ListGameRules()", "GameRuleList"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:GetGameRule(name: string)", "boolean|number|string"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:GetGameRuleEntry(name: string): GameRuleEntry?", null));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:SetGameRule(name: string, value: boolean|number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetX()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetZ()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetWorld()", "World"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetLevel()", "World"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetDimension()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetPosition()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetBlockOrigin()", "Vector3"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:IsLoaded()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:IsForced()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:Load()", "Chunk"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:Unload()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:SetForced(forced: boolean)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:ForceLoad()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetBlockAt(position: Vector3|CFrame)", "Block?"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetBlockStateAt(position: Vector3|CFrame)", "BlockState?"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetFluidStateAt(position: Vector3|CFrame)", "FluidState?"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetBlockEntityAt(position: Vector3|CFrame)", "BlockEntity?"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetBiomeAt(position: Vector3|CFrame)", "Biome?"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetFluidAt(position: Vector3|CFrame)", "string?"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetLightAt(position: Vector3|CFrame)", "number?"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetHeightAt(position: Vector3|CFrame, heightmap: string?)", "number?"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetTopBlockAt(position: Vector3|CFrame, heightmap: string?)", "Block?"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:SetBlockAt(position: Vector3|CFrame, blockId: string, properties: BlockStateProperties?, updateFlags: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:SetBlockAt(position: Vector3|CFrame, blockId: string, updateFlags: number)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:SetBlockAt(position: Vector3|CFrame, state: BlockState, updateFlags: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetEntities()", "Entity[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Chunk", "Chunk:GetBlockEntities()", "BlockEntity[]"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:Get(dimensionId: string)", "World?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetAll()", "World[]"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:ListGameRules()", "GameRuleList"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetGameRule(name: string)", "boolean|number|string"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetGameRuleEntry(name: string): GameRuleEntry?", null));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SetGameRule(name: string, value: boolean|number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:IsChunkLoaded(coordinates: Vector2|Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:IsBlockLoaded(position: Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:LoadChunk(coordinates: Vector2|Vector3|CFrame)", "Chunk"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetChunk(coordinates: Vector2|Vector3|CFrame)", "Chunk"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetChunkAt(coordinates: Vector2|Vector3|CFrame)", "Chunk"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SetTime(ticks: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:AddTime(ticks: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:UnloadChunk(coordinates: Vector2|Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SetChunkForced(coordinates: Vector2|Vector3|CFrame, forced: boolean)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:ForceLoadChunk(coordinates: Vector2|Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:IsChunkForced(coordinates: Vector2|Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetEntitiesInChunk(coordinates: Vector2|Vector3|CFrame, loadChunk: boolean?)", "Entity[]"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SetBlock(position: Vector3|CFrame, block: string|BlockState, properties: BlockStateProperties?, loadChunk: boolean?, updateFlags: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SetBlock(position: Vector3|CFrame, block: string|BlockState, properties: BlockStateProperties, updateFlags: number)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SetBlock(position: Vector3|CFrame, block: string|BlockState, loadChunk: boolean, updateFlags: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SetBlock(position: Vector3|CFrame, block: string|BlockState, updateFlags: number)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SetBlockState(position: Vector3|CFrame, state: BlockState, loadChunk: boolean?, updateFlags: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SetBlockState(position: Vector3|CFrame, state: BlockState, updateFlags: number)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:GetBlockEntity(position: Vector3|CFrame)", "BlockEntity?"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:CanSeeSky(position: Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:IsRaining()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:IsThundering()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:IsRainingAt(position: Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("World", "World:GetSurfaceY(position: Vector3|CFrame)", "number?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetBlockEntity(position: Vector3|CFrame)", "BlockEntity?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:CanSeeSky(position: Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:IsRaining()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:IsThundering()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:IsRainingAt(position: Vector3|CFrame)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetSurfaceY(position: Vector3|CFrame)", "number?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetBlock(position: Vector3|CFrame)", "Block"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SetSpawnPosition(position: Vector3|CFrame, yaw: number?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetFluid(position: Vector3|CFrame)", "string?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetBiome(position: Vector3|CFrame)", "Biome?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetLightLevel(position: Vector3|CFrame)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetSkyLight(position: Vector3|CFrame)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetBlockLight(position: Vector3|CFrame)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:GetBlockInventory(position: Vector3|CFrame)", "Container?"));
        docs.register(MLuaDocBuilder.Registration.type("BlockStateProperties", null, "Property names and values supported by the selected Minecraft block. Values are strings, numbers, or booleans and are validated against that block's state definition."));
        docs.register(MLuaDocBuilder.Registration.field("BlockStateProperties", "*", "string|number|boolean", "A block state property, such as facing = \"north\" or waterlogged = false."));
        docs.register(MLuaDocBuilder.Registration.type("GameRuleList", null, "ListGameRules returns numeric entries containing Name and Value, plus direct lookup by gamerule name. Reads work on client and server worlds; client reads use the client's local dimension state. Changes require a server World."));
        docs.register(MLuaDocBuilder.Registration.field("GameRuleList", "*", "GameRuleEntry|boolean|number|string", "An ordered GameRuleEntry at a numeric index, or the current value when indexed by gamerule name."));
        docs.register(MLuaDocBuilder.Registration.clazz("GameRuleEntry", "WorldModule", "A live typed view of one registered Minecraft gamerule.", "classes/gamerule-entry.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("GameRuleEntry", "GameRuleEntry.Name", "string", "The gamerule name accepted by GetGameRule and SetGameRule.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("GameRuleEntry", "GameRuleEntry.Value", "boolean|number|string", "The live value is readable on clients and servers; assignment is server-side and applies the same validation as World:SetGameRule.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("GameRuleEntry", "GameRuleEntry.ValueType", "string", "The Lua value type of this gamerule's value.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("GameRuleEntry", "GameRuleEntry:GetValueType(): string", "Returns the Lua value type of this gamerule's value.", null, "string", null, null));
        docs.register(MLuaDocBuilder.Registration.method("GameRuleEntry", "GameRuleEntry:SetValue(value: boolean|number)", "Sets the live value using the same type and range checks as World:SetGameRule.", null, "void", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("GameRuleEntry", "GameRuleEntry:IsBoolean(): boolean", "Checks whether this gamerule stores a boolean value.", null, "boolean", null, null));
        docs.register(MLuaDocBuilder.Registration.method("GameRuleEntry", "GameRuleEntry:IsNumber(): boolean", "Checks whether this gamerule stores a numeric value.", null, "boolean", null, null));
        docs.register(MLuaDocBuilder.Registration.method("GameRuleEntry", "GameRuleEntry:IsString(): boolean", "Checks whether this gamerule stores a string value.", null, "boolean", null, null));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:BreakBlock(position: Vector3|CFrame, dropItems: boolean?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:PlaySound(position: Vector3|CFrame, sound: string, volume: number?, pitch: number?)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SpawnParticle(position: Vector3|CFrame, particle: string, count: number?)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SpawnEntity(entityId: string, position: Vector3|CFrame, yaw: number?, pitch: number?)", "Entity?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:DropItem(position: Vector3|CFrame, itemId: string, count: number?)", "Entity?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:Explode(position: Vector3|CFrame, power: number?, fire: boolean?)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:SpawnLightning(position: Vector3|CFrame)", "Entity?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:FindEntitiesInRadius(center: Vector3|CFrame, radius: number, filters: string|EntityQuery?)", "Entity[]"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:FindEntitiesInBox(minimum: Vector3|CFrame, maximum: Vector3|CFrame, filters: string|EntityQuery?)", "Entity[]"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:FindEntitiesInBounds(bounds: EntityBounds, filters: string|EntityQuery?)", "Entity[]"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:FindPlayersInBounds(bounds: EntityBounds)", "Player[]"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:FindPlayersInRadius(center: Vector3|CFrame, radius: number)", "Player[]"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:FindNearestPlayer(position: Vector3|CFrame, maxDistance: number?)", "Player?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:FindNearestEntity(position: Vector3|CFrame, maxDistance: number?, filters: string|EntityQuery?)", "Entity?"));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:Raycast(origin: Vector3|CFrame, direction: Vector3, distance: number?, includeFluids: boolean?): BlockRaycastResult?", null));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:RaycastEntity(origin: Vector3|CFrame, direction: Vector3, distance: number?, filters: string|EntityQuery?): EntityRaycastResult?", null));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:RaycastAABB(origin: Vector3|CFrame, direction: Vector3, distance: number?, bounds: EntityBounds[]): BoundsRaycastResult?", null));
        docs.register(MLuaDocBuilder.Registration.signature("WorldService", "WorldService:RaycastAABB(origin: Vector3|CFrame, direction: Vector3, bounds: EntityBounds[]): BoundsRaycastResult?", null));
        docs.register(MLuaDocBuilder.Registration.clazz("Biome", "WorldModule", "A read-only biome view sampled in a dimension at a block position.", "classes/biome.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.Id", "string", "The registered biome identifier.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.Position", "Vector3", "The position used to sample this biome.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.Temperature", "number", "The biome's base temperature.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.Precipitation", "string", "Precipitation at the sample position: rain, snow, or none.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.Tags", "string[]", "Registered tags for this biome.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.SkyColor", "Color3", "Biome sky color.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.FogColor", "Color3", "Biome fog color.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.WaterColor", "Color3", "Biome water color.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.WaterFogColor", "Color3", "Biome water fog color.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.FoliageColor", "Color3", "Biome foliage color.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("Biome", "Biome.GrassColor", "Color3", "Biome grass color at the sample X/Z.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("Biome", "Biome:IsCold(): boolean", "Checks whether snow can form at the sample position.", null, "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Biome", "Biome:IsHot(): boolean", "Checks whether the biome's base temperature is above one.", null, "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Biome", "Biome:HasTag(tag: string): boolean", "Checks for a biome tag identifier.", null, "boolean", null, null));
        docs.register(MLuaDocBuilder.Registration.clazz("BoundsRaycastResult", "WorldModule", "The nearest custom EntityBounds hit returned by a ray.", "classes/bounds-raycast-result.html", "RaycastResult"));
        docs.register(MLuaDocBuilder.Registration.property("BoundsRaycastResult", "BoundsRaycastResult.Bounds", "EntityBounds", "The custom bounds instance hit by the ray.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("BoundsRaycastResult", "BoundsRaycastResult.BoundsIndex", "number", "One-based index of the hit bounds in the input list.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.clazz("RaycastResult", "WorldModule", "A world ray hit with a typed hit position, surface normal, and distance.", "classes/raycast-result.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("RaycastResult", "RaycastResult.HitType", "string", "The kind of ray hit.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("RaycastResult", "RaycastResult.Position", "Vector3", "The point where the ray intersects the hit bounds.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("RaycastResult", "RaycastResult.Normal", "Vector3", "The outward surface direction at the hit point.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("RaycastResult", "RaycastResult.Distance", "number", "Distance from the ray origin to the hit point.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.clazz("EntityRaycastResult", "WorldModule", "The nearest entity and hit point returned by a world's entity raycast.", "classes/entity-raycast-result.html", "RaycastResult"));
        docs.register(MLuaDocBuilder.Registration.property("EntityRaycastResult", "EntityRaycastResult.Entity", "Entity", "The live entity intersected by the ray.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.field("EntityRaycastResult", "Type", "string", "Compatibility field for HitType."));
        docs.register(MLuaDocBuilder.Registration.clazz("BlockRaycastResult", "WorldModule", "The first colliding block and hit point returned by a world's block raycast.", "classes/block-raycast-result.html", "RaycastResult"));
        docs.register(MLuaDocBuilder.Registration.field("BlockRaycastResult", "Type", "string", "Compatibility field for HitType."));
        docs.register(MLuaDocBuilder.Registration.property("BlockRaycastResult", "BlockRaycastResult.Block", "string", "The registered block identifier hit by the ray.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("BlockRaycastResult", "BlockRaycastResult.Face", "string", "The face of the block hit by the ray.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("BlockRaycastResult", "BlockRaycastResult.BlockInstance", "Block", "The live Block instance at the hit position.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Raycast from the player's view",
            "RaycastEntity finds the closest matching entity along the direction and stops at colliding blocks. Both block and entity results inherit RaycastResult; block hits also return the live block instance.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal entityHit = world:RaycastEntity(player.EyePosition, player.LookVector, 32, { exclude = player })\nif entityHit then\n    print(entityHit:IsA(\"RaycastResult\"), entityHit.Entity.Name, entityHit.Distance, entityHit.Normal)\nend\n\nlocal blockHit = world:Raycast(player.EyePosition, player.LookVector, 32)\nif blockHit then\n    print(blockHit:IsA(\"BlockRaycastResult\"), blockHit.Block, blockHit.BlockInstance.Id, blockHit.Face, blockHit.Normal)\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Raycast addon-defined hitboxes",
            "Pass world-space EntityBounds instances to test custom shapes. This tests the supplied boxes directly, without consulting block collisions. The result identifies the closest intersected box and its one-based input index.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal boxes = {\n    EntityBounds.new(Vector3.new(4, 64, 8), Vector3.new(5, 66, 9)),\n    EntityBounds.new(Vector3.new(7, 64, 8), Vector3.new(8, 66, 9)),\n}\nlocal hit = world:RaycastAABB(player.EyePosition, player.LookVector, 32, boxes)\nif hit then\n    print(hit.BoundsIndex, hit.Bounds.Minimum, hit.Position, hit.Normal)\nend"));
        docs.register(MLuaDocBuilder.Registration.type("EntityQuery", null, "A table of filters accepted by world, EntityService, and entity instance searches. Pass a string when matching one registered entity type."));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "type", "string", "Match this registered entity type identifier."));
        String filterArrayRule = " Values must use a dense Lua array with only positive integer keys and at most "
            + MLuaEntityQuery.MAX_FILTER_ARRAY_ENTRIES + " entries.";
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "types", "string[]", "Allow only these registered entity type identifiers; combines with type when both are set." + filterArrayRule));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "exclude_types", "string[]", "Exclude these registered entity type identifiers." + filterArrayRule));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "players_only", "boolean", "Include only player entities when true."));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "exclude_players", "boolean", "Exclude player entities when true."));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "exclude", "Entity", "Exclude this live entity from the query results."));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "exclude_entities", "Entity[]", "Exclude these live entity instances from the query results." + filterArrayRule));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "alive", "boolean", "Require the entity's alive state to match."));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "name", "string", "Require an exact entity name."));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "name_contains", "string", "Require the entity's display name to contain this case-sensitive text."));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "tag", "string", "Require this saved MLua entity tag."));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "tags", "string[]", "Require every listed saved MLua entity tag." + filterArrayRule));
        docs.register(MLuaDocBuilder.Registration.field("EntityQuery", "exclude_tags", "string[]", "Exclude entities with any listed saved MLua tag." + filterArrayRule));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Query several entity types",
            "Use types to include a set of registered entity identifiers and exclude_types to remove specific ones. Radius searches use a spherical distance from the center.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal hostiles = world:FindEntitiesInRadius(player.Position, 24, {\n    types = { \"minecraft:zombie\", \"minecraft:skeleton\", \"minecraft:creeper\" },\n    exclude_entities = { player, projectile }\n})\n\nfor _, entity in ipairs(hostiles) do\n    print(entity.Name, entity.Position)\nend"));
        docs.register(MLuaDocBuilder.Registration.clazz("Level", "MLuaLevelWrapper", "A live Minecraft world instance, with dimension, time, and block access.", "classes/level.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.method("Level", "Level:GetDimension()", "Returns this world's namespaced dimension identifier.", null, "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Level", "Level:IsClientSide()", "Returns whether this world instance belongs to the client.", null, "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Level", "Level:GetGameTime()", "Returns the total number of ticks this world has been running.", null, "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Level", "Level:GetDayTime()", "Returns this world's current day time in ticks.", null, "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("Level", "Level:SetDayTime(ticks: number)", "Sets the day time on a server world.", "Whole-number game ticks", "void", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("Level", "Level:GetBlock(position: Vector3|CFrame): Block", "Returns the live block instance at the supplied native position.", "Vector3 or CFrame position; coordinate tables are rejected", "Block", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.clazz("World", null, "A live world instance for one dimension, with time, block, entity, and chunk APIs.", "classes/world.html", "Level"));
        docs.register(MLuaDocBuilder.Registration.clazz("BlockState", "MLuaBlockStateWrapper", "An immutable Minecraft block state at a world position, with typed state queries.", "classes/block-state.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("FluidState", "MLuaFluidStateWrapper", "An immutable Minecraft fluid state at a world position.", "classes/fluid-state.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.Id", "string", "The block identifier for this state.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.Block", "Block", "The live block instance at this state's position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.Position", "Vector3", "This state's world position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.Properties", "BlockStateProperties", "The current values of this state's properties.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.Air", "boolean", "Whether this state is air.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.Solid", "boolean", "Whether this state is solid.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.Replaceable", "boolean", "Whether another block can replace this state.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.Fluid", "string|nil", "The fluid identifier in this state, or nil when empty.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.RandomlyTicking", "boolean", "Whether this state receives random game ticks.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.Survivable", "boolean", "Whether this state can remain at its world position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.LightEmission", "number", "The light level emitted by this state.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.SignalSource", "boolean", "Whether this state can provide a redstone signal.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.RedstoneConductor", "boolean", "Whether this state conducts redstone power at its world position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.AnalogOutput", "boolean", "Whether this state provides comparator output.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.CollisionShapeFullBlock", "boolean", "Whether this state's collision shape fills its block space.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.Occluding", "boolean", "Whether this state occludes neighboring faces.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.ViewBlocking", "boolean", "Whether this state blocks visibility at its world position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.LightBlock", "number", "The light level blocked by this state at its world position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockState", "state.DestroySpeed", "number", "The state destroy speed in its world context.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("FluidState", "fluidState.Id", "string|nil", "This fluid state's registered fluid identifier.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("FluidState", "fluidState.Position", "Vector3", "This fluid state's world position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("FluidState", "fluidState.Empty", "boolean", "Whether this position contains no fluid.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("FluidState", "fluidState.Source", "boolean", "Whether this is a source fluid state.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("FluidState", "fluidState.Amount", "number", "This fluid state's level amount.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("FluidState", "fluidState.Height", "number", "This fluid state's height at its world position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("FluidState", "fluidState.OwnHeight", "number", "This fluid state's intrinsic height.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("FluidState", "fluidState.Flow", "Vector3", "This fluid state's flow direction.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("FluidState", "fluidState.RandomlyTicking", "boolean", "Whether this fluid state receives random game ticks.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("FluidState", "fluidState.BlockState", "BlockState", "The legacy block state representing this fluid at its world position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.clazz("Chunk", "MLuaChunkWrapper", "A live handle to one chunk in one dimension. Data methods load the chunk when needed.", "classes/chunk.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("Chunk", "chunk.X", "number", "This chunk's X coordinate.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Chunk", "chunk.Z", "number", "This chunk's Z coordinate.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Chunk", "chunk.World", "World", "The live dimension containing this chunk.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Chunk", "chunk.Level", "World", "The live world containing this chunk.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Chunk", "chunk.Dimension", "string", "The identifier of the dimension containing this chunk.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Chunk", "chunk.Position", "Vector2", "Chunk coordinates: X is chunk X and Y is chunk Z.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Chunk", "chunk.BlockOrigin", "Vector3", "The minimum block-space X/Z position of this chunk.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Chunk", "chunk.Loaded", "boolean", "Whether this chunk is currently loaded, without loading it.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("Chunk", "chunk.Forced", "boolean", "Whether this chunk has a force-load ticket.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("World", "world.Raining", "boolean", "Whether it is raining in this world.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("World", "world.Thundering", "boolean", "Whether a thunderstorm is active in this world.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("World", "world.SpawnPosition", "Vector3", "This world's shared spawn position; assign a native Vector3 or CFrame to change it on the server.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.service("World", "MLuaWorldService", "Provides live world instances for loaded dimensions.", "services/world.html"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Load and edit chunks in a dimension",
            "Each World instance is one dimension. Vector2 means chunk coordinates (X, Z); Vector3 and CFrame select the chunk containing a block position. GetChunk returns a live Chunk handle without loading it. Data methods load that chunk when called. Chunk:GetHeightAt returns the first free Y value, and Chunk:GetTopBlockAt returns the live registered Block instance below it. Both accept a Minecraft heightmap name, defaulting to MOTION_BLOCKING. SetBlock and SetBlockAt accept an optional property table for states such as stair facing and waterlogging, or a typed BlockState. Both also accept Minecraft block update flags, defaulting to 3. Property names and values follow the block's Minecraft state definition. LoadChunk loads immediately; ForceLoad adds a persistent ticket, while Unload removes that ticket and lets Minecraft decide when to unload.",
            "local worlds = Minecraft:GetService(\"World\")\nlocal nether = worlds:Get(\"minecraft:the_nether\")\nlocal coordinates = Vector2.new(2, -1)\nlocal blockPosition = Vector3.new(32, 70, -16)\nlocal chunk = nether:GetChunk(coordinates)\n\nchunk:SetBlockAt(blockPosition, \"minecraft:oak_stairs\", { facing = \"north\", half = \"bottom\", waterlogged = false }, 3)\nlocal state = chunk:GetBlockStateAt(blockPosition)\nlocal fluidState = chunk:GetFluidStateAt(blockPosition)\nif state then\n    chunk:SetBlockAt(blockPosition, state, 3)\nend\nlocal biome = chunk:GetBiomeAt(blockPosition)\nlocal fluid = chunk:GetFluidAt(blockPosition)\nlocal loaded = nether:LoadChunk(coordinates)\nprint(loaded.Loaded, loaded.Position, biome, fluid, fluidState.Id)\n\nchunk:ForceLoad()\nprint(chunk.Forced)\nchunk:Unload()"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Find and remove entities in a chunk",
            "GetEntitiesInChunk can load an unloaded chunk first. It returns live Entity instances whose block position is inside that chunk, so adjacent chunks do not duplicate entities that merely overlap the boundary. Use their Entity methods to remove or kill them. This operates only in the World instance's dimension.",
            "local worlds = Minecraft:GetService(\"World\")\nlocal world = worlds:Get(\"minecraft:overworld\")\nlocal position = Vector3.new(32, 70, -16)\n\nfor _, entity in ipairs(world:GetEntitiesInChunk(position, true)) do\n    if entity:IsA(\"Mob\") then\n        entity:Remove()\n    end\nend\n\nlocal spawned = Minecraft:GetService(\"EntityService\"):Spawn(\"minecraft:zombie\", position, world)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Set a dimension's shared spawn point",
            "SetSpawnPosition takes a native Vector3 or CFrame and an optional yaw in degrees. It is server-side only; SpawnPosition can also be assigned directly.",
            "local world = Minecraft:GetService(\"World\"):Get(\"minecraft:overworld\")\nlocal spawn = Vector3.new(0, 65, 0)\nworld:SetSpawnPosition(spawn, 90)\nworld.SpawnPosition = spawn"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Read and change dimension gamerules",
            "Gamerules belong to a dimension. Reads work on client and server worlds; client reads use the client's local dimension state. GetGameRule returns the rule's current value; GetGameRuleEntry returns a live typed entry with its name, value, and value type. SetGameRule and GameRuleEntry:SetValue are server-side and accept a boolean for boolean rules and a whole 32-bit number for integer rules. Unknown rule names and mismatched values raise an error. ListGameRules can be traversed with ipairs or indexed by the rule name.",
            "local world = Minecraft:GetService(\"World\"):Get(\"minecraft:overworld\")\nlocal oldValue = world:GetGameRule(\"doDaylightCycle\")\nlocal rule = world:GetGameRuleEntry(\"doDaylightCycle\")\nprint(\"Daylight cycle:\", oldValue, rule:IsA(\"GameRuleEntry\"), rule.ValueType)\n\nworld:SetGameRule(\"doDaylightCycle\", false)\nfor _, entry in ipairs(world:ListGameRules()) do\n    print(entry.Name, entry.Value, entry:IsBoolean())\nend\n\nlocal rules = world:ListGameRules()\nprint(\"Mob griefing:\", rules.mobGriefing)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("WorldService", "Select the dimension for world operations",
            "Each World value represents one dimension. Get selects a loaded dimension by identifier. Current returns the active client dimension on the client and the overworld on the server; when acting on a player in another dimension, use the player's dimension identifier with Get. World changes are server-side operations.",
            "local WorldService = Minecraft:GetService(\"World\")\nlocal world = WorldService:Current()\n\nif world then\n    print(world.Id, world.Time)\n    world:SetTime(world.Time + 100)\nend\n\nWorldService:SetWeather(Enum.Weather.Rain)\nlocal nether = WorldService:Get(\"minecraft:the_nether\")\nif nether then\n    print(nether:GetTime())\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Query surface, sky, and weather around a position",
            "GetSurfaceY reads the motion-blocking height for the X/Z column and returns nil when that chunk is not loaded. CanSeeSky and IsRainingAt use the exact block position.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal position = Vector3.new(0, 64, 0)\nlocal surfaceY = world:GetSurfaceY(position)\nif surfaceY then\n    print(\"Surface Y:\", surfaceY)\n    print(\"Sky visible:\", world:CanSeeSky(position))\n    print(\"Rain reaches position:\", world:IsRainingAt(position))\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Reach a block entity and its container",
            "GetBlockEntity returns the live registered BlockEntity subclass at a position. Scripted block entities can expose their own methods and a shared Container instance.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal blockEntity = world:GetBlockEntity(Vector3.new(0, 64, 0))\nif blockEntity and blockEntity:HasContainer() then\n    local container = blockEntity:GetContainer()\n    print(\"Slots:\", container:GetSize())\n    for _, slot in ipairs(container:GetAll()) do\n        if not slot:IsEmpty() then\n            print(slot:GetItem():GetId(), slot:GetCount())\n        end\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Inspect a block state",
            "GetBlockState returns an immutable BlockState instance, while GetBlock returns the live position-aware Block instance. Read typed state properties from BlockState.Properties and check for a property before querying optional values. WithProperty creates a new state value; SetBlockState applies that state to the world on the server.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal position = Vector3.new(4, 65, 4)\nlocal state = world:GetBlockState(position)\nprint(state.Id, state.Solid, state.DestroySpeed, state.LightEmission, state.Survivable)\nif state:IsSignalSource() then\n    print(\"Analog output:\", state:GetAnalogOutputSignal())\n    print(\"North signal:\", state:GetSignal(Enum.Direction.NORTH))\nend\n\nif state:HasProperty(\"facing\") then\n    local rotated = state:WithProperty(\"facing\", \"east\")\n    world:SetBlockState(position, rotated)\nend\n\nlocal block = world:GetBlock(position)\nprint(block:GetClassName(), block:GetStateProperties().facing)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Inspect fluid at a position",
            "GetFluid returns the fluid identifier for simple comparisons. GetFluidState returns the native-style FluidState instance, including whether it is empty or a source, its amount and height, and the legacy BlockState representation.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal position = Vector3.new(4, 63, 4)\nlocal fluid = world:GetFluidState(position)\nif not fluid.Empty then\n    print(fluid.Id, fluid.Source, fluid.Amount, fluid.Height)\n    local legacyState = fluid:GetBlockState()\n    print(legacyState.Id)\nend"));
    }
}
