package dev.mlua.modules.entity;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.entity.Service.MLuaEntityService;
import java.util.List;

public final class EntityModule implements MLuaModule {
    @Override
    public String id() {
        return "entity";
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder entity = ServiceEntry.builder("EntityService", MLuaEntityService::new)
            .reflective(MLuaEntityService.class);
        return List.of(entity.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetPosition()", "Vector3"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetPosition(position: Vector3|CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:Teleport(position: Vector3|CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetVelocity()", "Vector3"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetVelocity(velocity: Vector3)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetCFrame()", "CFrame"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetCFrame(value: CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:LookAt(target: Vector3|CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:DistanceTo(target: Vector3|CFrame|Entity)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetLevel()", "World"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetDimension()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetLookVector()", "Vector3"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetRotation()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetRotation(rotation: Vector2)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetIsAlive()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetIsOnGround()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetIsInWater()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetIsOnFire()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetIsMoving()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetIsFalling()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetCustomName()", "string?"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetCustomName(name: string?)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetCustomNameVisible()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetCustomNameVisible(visible: boolean)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetGlowing()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetGlowing(enabled: boolean)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetNoGravity()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetNoGravity(enabled: boolean)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetInvulnerable()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetInvulnerable(enabled: boolean)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetSilent()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetSilent(enabled: boolean)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetFireTicks()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetFireTicks(ticks: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetEyePosition()", "Vector3"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetBoundingBox()", "EntityBounds"));
        docs.register(MLuaDocBuilder.Registration.property("Entity", "Entity.BoundingBox", "EntityBounds", "This entity's axis-aligned bounds.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetWidth()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetHeight()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetEyeHeight()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetVehicle()", "Entity?"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetRootVehicle()", "Entity"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetPassengers()", "Entity[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:StartRiding(vehicle: Entity, force: boolean?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:StopRiding()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetIsPassenger()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetIsVehicle()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetIsInLava()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetIsUnderWater()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetFallDistance()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetFallDistance(distance: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Entity", "Ride an entity and inspect its passengers",
            "Entity ride relationships use Minecraft's passenger rules. StartRiding returns false if the mount is rejected; GetPassengers returns live Entity instances.",
            "local entityService = Minecraft:GetService(\"EntityService\")\nlocal world = Minecraft:GetService(\"World\"):Current()\nlocal spawnPosition = Vector3.new(0, 64, 0)\nlocal horse = entityService:Spawn(\"minecraft:horse\", spawnPosition, world)\nlocal rider = entityService:Spawn(\"minecraft:zombie\", spawnPosition, world)\nif rider:StartRiding(horse) then\n    for _, passenger in ipairs(horse.Passengers) do\n        print(passenger.Name, passenger.Vehicle.Name)\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetTags()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:HasTag(tag: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:AddTag(tag: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:RemoveTag(tag: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Entity", "Mark and query entities with saved tags",
            "Entity tags created here are MLua persistent tags saved with MLua world data. They can be used in World entity-query filters.",
            "local target = world:FindNearestEntity(Vector3.new(0, 64, 0), 16, { type = \"minecraft:zombie\" })\nif target then\n    target:AddTag(\"encounter_target\")\nend\n\nfor _, entity in ipairs(world:FindEntitiesInRadius(Vector3.new(0, 64, 0), 24, { tags = { \"encounter_target\" } })) do\n    entity:SetGlowing(true)\nend"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:Kill()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:Remove()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GetHealth()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:SetHealth(value: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GetMaxHealth()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GetArmor()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GetAbsorption()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:SetAbsorption(value: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:Damage(amount: number, sourceName: string?, attacker: Entity?)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GetAttribute(name: string)", "number?"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:SetAttribute(name: string, value: number)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GetAttributeInstance(name: string)", "AttributeInstance?"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GiveEffect(effect: string|table, duration: number?, amplifier: number?, ambient: boolean?, showParticles: boolean?, showIcon: boolean?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:RemoveEffect(effect: string|table)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:HasEffect(effect: string|table)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GetEffects()", "table[]"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:ClearEffects()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Mob", "Mob:GetTarget()", "LivingEntity?"));
        docs.register(MLuaDocBuilder.Registration.signature("Mob", "Mob:SetTarget(target: LivingEntity?)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Mob", "Mob:CanSee(target: Entity)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Mob", "Mob:CanAttack(target: LivingEntity)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Mob", "Mob:GetPersistenceRequired()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Mob", "Mob:SetPersistenceRequired()", "void"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Mob", "Drive a mob's target from a script",
            "Mob is the class for Minecraft mobs, between LivingEntity and concrete mob types. Use CanSee and CanAttack before assigning the mob's target.",
            "local function updateTarget(mob, player)\n    if mob:CanSee(player) and mob:CanAttack(player) then\n        mob.Target = player\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetNbt(key: string?)", "any"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetNbt(key: string, value: any)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetPersistentData()", "DataStore"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetSyncedData()", "DataStore"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GetEquipment(slot: string)", "ItemStack"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:SetEquipment(slot: string, item: ItemStack)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:LoadAnimation(animation: Animation|BBModel, priority: string?, fadeTime: number?)", "AnimationTrack|BBModel"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:SetBonePose(bone: string, pose: CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetBonePose(bone: string)", "CFrame?"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:ClearBonePose(bone: string)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetBonePoses()", "table"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:PlayAnimation(name: string, options: table?)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:StopAnimation()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:StopAllAnimations()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetPlayingAnimationTracks()", "AnimationTrack[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:ResetAnimation()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:GetById(id: number)", "Entity?"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:GetByUUID(uuid: string)", "Entity?"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:FindByUUID(uuid: string)", "Entity?"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:GetAll()", "Entity[]"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:GetNearby(center: Entity|Vector3|CFrame, radius: number)", "Entity[]"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:Spawn(typeId: string, position: Entity|Vector3|CFrame, world: World|string?)", "Entity?"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:Teleport(entity: Entity, position: Vector3|CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:SetVelocity(entity: Entity, velocity: Vector3)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:Damage(entity: Entity, amount: number, damageType: string?, attacker: Entity?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:Kill(entity: Entity)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityService", "EntityService:Remove(entity: Entity)", "void"));
        docs.register(MLuaDocBuilder.Registration.clazz("EntityBounds", "PlayersModule", "An axis-aligned entity bounding box with Vector3 corners and spatial queries.", "classes/entity-bounds.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.signature("EntityBounds", "EntityBounds.new(minimum: Vector3, maximum: Vector3): EntityBounds", null));
        docs.register(MLuaDocBuilder.Registration.property("EntityBounds", "EntityBounds.Minimum", "Vector3", "Minimum corner of the entity bounds.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("EntityBounds", "EntityBounds.Maximum", "Vector3", "Maximum corner of the entity bounds.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("EntityBounds", "EntityBounds.Center", "Vector3", "Center point of the entity bounds.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("EntityBounds", "EntityBounds.Size", "Vector3", "Dimensions of the entity bounds.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.property("EntityBounds", "EntityBounds.Volume", "number", "Volume of the entity bounds in cubic blocks.", "read:Read"));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:GetCenter(): Vector3", "Returns the center of the box.", null, "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:GetSize(): Vector3", "Returns the dimensions of the box.", null, "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:GetVolume(): number", "Returns the volume of the box in cubic blocks.", null, "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:Contains(point: Vector3): boolean", "Checks whether a point lies inside or on the box.", "point", "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:Intersects(other: EntityBounds): boolean", "Checks whether this box overlaps another box.", "other", "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:Inflate(amount: number): EntityBounds", "Returns a new box expanded by the given amount.", "amount", "EntityBounds", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:Deflate(amount: number): EntityBounds", "Returns a new box contracted by the given amount.", "amount", "EntityBounds", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:Move(offset: Vector3): EntityBounds", "Returns a new box translated by the given offset.", "offset", "EntityBounds", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:Union(other: EntityBounds): EntityBounds", "Returns a new box that encloses both boxes.", "other", "EntityBounds", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:DistanceTo(point: Vector3): number", "Returns the shortest distance from a point to the box.", "point", "number", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("EntityBounds", "EntityBounds:ClosestPoint(point: Vector3): Vector3", "Returns the nearest point on or inside the box.", "point", "Vector3", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("EntityBounds", "Create and query a bounds instance",
            "Create bounds from two ordered corners, then use the same class methods as the bounds returned by entities.",
            "local bounds = EntityBounds.new(Vector3.new(-2, 60, -2), Vector3.new(2, 68, 2))\nlocal expanded = bounds:Inflate(1)\n\nprint(bounds:IsA(\"EntityBounds\"), bounds.Center, bounds:Contains(Vector3.new(0, 64, 0)))\nprint(expanded:Intersects(bounds), bounds:DistanceTo(Vector3.new(8, 64, 0)))"));
        docs.register(MLuaDocBuilder.Registration.guideStep("World", "Query entities with EntityBounds",
            "Pass the same typed bounds object to a dimension's entity or player search. Entity searches can take the usual query filters.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal bounds = EntityBounds.new(Vector3.new(-8, 60, -8), Vector3.new(8, 72, 8))\n\nfor _, entity in ipairs(world:FindEntitiesInBounds(bounds, { alive = true })) do\n    print(entity.Name, entity.Position)\nend\n\nlocal players = world:FindPlayersInBounds(bounds)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Entity", "Tag entities and query them in the world",
            "Use saved MLua entity tags to mark targets, then pass an entity query to a world's spatial search method.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal target = world:FindNearestEntity(Vector3.new(0, 64, 0), 16, { type = \"minecraft:zombie\" })\n\nif target then\n    target:AddTag(\"encounter_target\")\n    local bounds = target:GetBoundingBox()\n    print(bounds:IsA(\"EntityBounds\"), bounds:GetCenter(), bounds:Contains(target.Position))\nend\n\nlocal targets = world:FindEntitiesInRadius(Vector3.new(0, 64, 0), 24, { tags = { \"encounter_target\" }, alive = true })\nfor _, entity in ipairs(targets) do\n    entity:SetGlowing(true)\nend"));
        docs.register(MLuaDocBuilder.Registration.service("EntityService", "MLuaEntityService",
            "Query, spawn, move, damage, and remove Minecraft entities from Lua.",
            "services/entity-service.html"));
    }
}
