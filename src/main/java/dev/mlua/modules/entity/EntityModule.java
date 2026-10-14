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
        docs.signature("Entity", "Entity:GetPosition()", "Vector3");
        docs.signature("Entity", "Entity:SetPosition(position: Vector3|CFrame)", "void");
        docs.signature("Entity", "Entity:Teleport(position: Vector3|CFrame)", "void");
        docs.signature("Entity", "Entity:GetVelocity()", "Vector3");
        docs.signature("Entity", "Entity:SetVelocity(velocity: Vector3)", "void");
        docs.signature("Entity", "Entity:GetCFrame()", "CFrame");
        docs.signature("Entity", "Entity:SetCFrame(value: CFrame)", "void");
        docs.signature("Entity", "Entity:LookAt(target: Vector3|CFrame)", "void");
        docs.signature("Entity", "Entity:DistanceTo(position: Vector3|CFrame)", "number");
        docs.signature("Entity", "Entity:GetLevel()", "World");
        docs.signature("Entity", "Entity:GetDimension()", "string");
        docs.signature("Entity", "Entity:GetLookVector()", "Vector3");
        docs.signature("Entity", "Entity:GetRotation()", "Vector2");
        docs.signature("Entity", "Entity:SetRotation(rotation: Vector2)", "void");
        docs.signature("Entity", "Entity:GetIsAlive()", "boolean");
        docs.signature("Entity", "Entity:GetIsOnGround()", "boolean");
        docs.signature("Entity", "Entity:GetIsInWater()", "boolean");
        docs.signature("Entity", "Entity:GetIsOnFire()", "boolean");
        docs.signature("Entity", "Entity:GetIsMoving()", "boolean");
        docs.signature("Entity", "Entity:GetIsFalling()", "boolean");
        docs.signature("Entity", "Entity:GetCustomName()", "string?");
        docs.signature("Entity", "Entity:SetCustomName(name: string?)", "void");
        docs.signature("Entity", "Entity:GetCustomNameVisible()", "boolean");
        docs.signature("Entity", "Entity:SetCustomNameVisible(visible: boolean)", "void");
        docs.signature("Entity", "Entity:GetGlowing()", "boolean");
        docs.signature("Entity", "Entity:SetGlowing(enabled: boolean)", "void");
        docs.signature("Entity", "Entity:GetNoGravity()", "boolean");
        docs.signature("Entity", "Entity:SetNoGravity(enabled: boolean)", "void");
        docs.signature("Entity", "Entity:GetInvulnerable()", "boolean");
        docs.signature("Entity", "Entity:SetInvulnerable(enabled: boolean)", "void");
        docs.signature("Entity", "Entity:GetSilent()", "boolean");
        docs.signature("Entity", "Entity:SetSilent(enabled: boolean)", "void");
        docs.signature("Entity", "Entity:GetFireTicks()", "number");
        docs.signature("Entity", "Entity:SetFireTicks(ticks: number)", "void");
        docs.signature("Entity", "Entity:GetEyePosition()", "Vector3");
        docs.signature("Entity", "Entity:GetBoundingBox()", "EntityBounds");
        docs.signature("Entity", "Entity:GetWidth()", "number");
        docs.signature("Entity", "Entity:GetHeight()", "number");
        docs.signature("Entity", "Entity:GetEyeHeight()", "number");
        docs.signature("Entity", "Entity:GetVehicle()", "Entity?");
        docs.signature("Entity", "Entity:GetRootVehicle()", "Entity");
        docs.signature("Entity", "Entity:GetPassengers()", "Entity[]");
        docs.signature("Entity", "Entity:StartRiding(vehicle: Entity, force: boolean?)", "boolean");
        docs.signature("Entity", "Entity:StopRiding()", "void");
        docs.signature("Entity", "Entity:GetIsPassenger()", "boolean");
        docs.signature("Entity", "Entity:GetIsVehicle()", "boolean");
        docs.signature("Entity", "Entity:GetIsInLava()", "boolean");
        docs.signature("Entity", "Entity:GetIsUnderWater()", "boolean");
        docs.signature("Entity", "Entity:GetFallDistance()", "number");
        docs.signature("Entity", "Entity:SetFallDistance(distance: number)", "void");
        docs.guideStep("Entity", "Ride an entity and inspect its passengers",
            "Entity ride relationships use Minecraft's passenger rules. StartRiding returns false if the mount is rejected; GetPassengers returns live Entity instances.",
            "local entityService = Minecraft:GetService(\"EntityService\")\nlocal world = Minecraft:GetService(\"World\"):Current()\nlocal spawnPosition = Vector3.new(0, 64, 0)\nlocal horse = entityService:Spawn(\"minecraft:horse\", spawnPosition, world)\nlocal rider = entityService:Spawn(\"minecraft:zombie\", spawnPosition, world)\nif rider:StartRiding(horse) then\n    for _, passenger in ipairs(horse.Passengers) do\n        print(passenger.Name, passenger.Vehicle.Name)\n    end\nend");
        docs.signature("Entity", "Entity:GetTags()", "string[]");
        docs.signature("Entity", "Entity:HasTag(tag: string)", "boolean");
        docs.signature("Entity", "Entity:AddTag(tag: string)", "boolean");
        docs.signature("Entity", "Entity:RemoveTag(tag: string)", "boolean");
        docs.guideStep("Entity", "Mark and query entities with saved tags",
            "Entity tags created here are MLua persistent tags saved with MLua world data. They can be used in World entity-query filters.",
            "local target = world:FindNearestEntity(Vector3.new(0, 64, 0), 16, { type = \"minecraft:zombie\" })\nif target then\n    target:AddTag(\"encounter_target\")\nend\n\nfor _, entity in ipairs(world:FindEntitiesInRadius(Vector3.new(0, 64, 0), 24, { tags = { \"encounter_target\" } })) do\n    entity:SetGlowing(true)\nend");
        docs.signature("Entity", "Entity:Kill()", "void");
        docs.signature("Entity", "Entity:Remove()", "void");
        docs.signature("LivingEntity", "LivingEntity:GetHealth()", "number");
        docs.signature("LivingEntity", "LivingEntity:SetHealth(value: number)", "void");
        docs.signature("LivingEntity", "LivingEntity:GetMaxHealth()", "number");
        docs.signature("LivingEntity", "LivingEntity:GetArmor()", "number");
        docs.signature("LivingEntity", "LivingEntity:GetAbsorption()", "number");
        docs.signature("LivingEntity", "LivingEntity:SetAbsorption(value: number)", "void");
        docs.signature("LivingEntity", "LivingEntity:Damage(amount: number, sourceName: string?, attacker: Entity?)", "void");
        docs.signature("LivingEntity", "LivingEntity:GetAttribute(name: string)", "number?");
        docs.signature("LivingEntity", "LivingEntity:SetAttribute(name: string, value: number)", "boolean");
        docs.signature("LivingEntity", "LivingEntity:GetAttributeInstance(name: string)", "AttributeInstance?");
        docs.signature("LivingEntity", "LivingEntity:GiveEffect(effect: string|table, duration: number?, amplifier: number?, ambient: boolean?, showParticles: boolean?, showIcon: boolean?)", "boolean");
        docs.signature("LivingEntity", "LivingEntity:RemoveEffect(effect: string|table)", "boolean");
        docs.signature("LivingEntity", "LivingEntity:HasEffect(effect: string|table)", "boolean");
        docs.signature("LivingEntity", "LivingEntity:GetEffects()", "table[]");
        docs.signature("LivingEntity", "LivingEntity:ClearEffects()", "void");
        docs.signature("Mob", "Mob:GetTarget()", "LivingEntity?");
        docs.signature("Mob", "Mob:SetTarget(target: LivingEntity?)", "void");
        docs.signature("Mob", "Mob:CanSee(target: Entity)", "boolean");
        docs.signature("Mob", "Mob:CanAttack(target: LivingEntity)", "boolean");
        docs.signature("Mob", "Mob:GetPersistenceRequired()", "boolean");
        docs.signature("Mob", "Mob:SetPersistenceRequired()", "void");
        docs.guideStep("Mob", "Drive a mob's target from a script",
            "Mob is the class for Minecraft mobs, between LivingEntity and concrete mob types. Use CanSee and CanAttack before assigning the mob's target.",
            "local function updateTarget(mob, player)\n    if mob:CanSee(player) and mob:CanAttack(player) then\n        mob.Target = player\n    end\nend");
        docs.signature("Entity", "Entity:GetNbt(key: string?)", "any");
        docs.signature("Entity", "Entity:SetNbt(key: string, value: any)", "void");
        docs.signature("Entity", "Entity:GetPersistentData()", "DataStore");
        docs.signature("Entity", "Entity:GetSyncedData()", "DataStore");
        docs.signature("LivingEntity", "LivingEntity:GetEquipment(slot: string)", "ItemStack");
        docs.signature("LivingEntity", "LivingEntity:SetEquipment(slot: string, item: ItemStack)", "void");
        docs.signature("Entity", "Entity:LoadAnimation(animation: Animation|BBModel, priority: string?, fadeTime: number?)", "AnimationTrack|BBModel");
        docs.signature("Entity", "Entity:SetBonePose(bone: string, pose: CFrame)", "void");
        docs.signature("Entity", "Entity:GetBonePose(bone: string)", "CFrame?");
        docs.signature("Entity", "Entity:ClearBonePose(bone: string)", "void");
        docs.signature("Entity", "Entity:GetBonePoses()", "table");
        docs.signature("Entity", "Entity:PlayAnimation(name: string, options: table?)", "void");
        docs.signature("Entity", "Entity:StopAnimation()", "void");
        docs.signature("Entity", "Entity:StopAllAnimations()", "void");
        docs.signature("Entity", "Entity:GetPlayingAnimationTracks()", "AnimationTrack[]");
        docs.signature("Entity", "Entity:ResetAnimation()", "void");
        docs.signature("EntityService", "EntityService:GetById(id: number)", "Entity?");
        docs.signature("EntityService", "EntityService:GetByUUID(uuid: string)", "Entity?");
        docs.signature("EntityService", "EntityService:FindByUUID(uuid: string)", "Entity?");
        docs.signature("EntityService", "EntityService:GetAll()", "Entity[]");
        docs.signature("EntityService", "EntityService:GetNearby(center: Entity|Vector3|CFrame, radius: number)", "Entity[]");
        docs.signature("EntityService", "EntityService:Spawn(typeId: string, position: Entity|Vector3|CFrame, world: World|string?)", "Entity?");
        docs.signature("EntityService", "EntityService:Teleport(entity: Entity, position: Vector3|CFrame)", "void");
        docs.signature("EntityService", "EntityService:SetVelocity(entity: Entity, velocity: Vector3)", "void");
        docs.signature("EntityService", "EntityService:Damage(entity: Entity, amount: number)", "boolean");
        docs.signature("EntityService", "EntityService:Kill(entity: Entity)", "void");
        docs.signature("EntityService", "EntityService:Remove(entity: Entity)", "void");
        docs.clazz("EntityBounds", "PlayersModule", "An axis-aligned entity bounding box with Vector3 corners and spatial queries.", "classes/entity-bounds.html", "Instance");
        docs.property("EntityBounds", "EntityBounds.Minimum", "Vector3", "Minimum corner of the entity bounds.", "read:Read");
        docs.property("EntityBounds", "EntityBounds.Maximum", "Vector3", "Maximum corner of the entity bounds.", "read:Read");
        docs.property("EntityBounds", "EntityBounds.Center", "Vector3", "Center point of the entity bounds.", "read:Read");
        docs.property("EntityBounds", "EntityBounds.Size", "Vector3", "Dimensions of the entity bounds.", "read:Read");
        docs.method("EntityBounds", "EntityBounds:GetCenter(): Vector3", "Returns the center of the box.", null, "Vector3", "shared:Shared", null);
        docs.method("EntityBounds", "EntityBounds:GetSize(): Vector3", "Returns the dimensions of the box.", null, "Vector3", "shared:Shared", null);
        docs.method("EntityBounds", "EntityBounds:Contains(point: Vector3): boolean", "Checks whether a point lies inside or on the box.", "point", "boolean", "shared:Shared", null);
        docs.method("EntityBounds", "EntityBounds:Intersects(other: EntityBounds): boolean", "Checks whether this box overlaps another box.", "other", "boolean", "shared:Shared", null);
        docs.guideStep("Entity", "Tag entities and query them in the world",
            "Use saved MLua entity tags to mark targets, then pass an entity query to a world's spatial search method.",
            "local world = Minecraft:GetService(\"World\"):Current()\nlocal target = world:FindNearestEntity(Vector3.new(0, 64, 0), 16, { type = \"minecraft:zombie\" })\n\nif target then\n    target:AddTag(\"encounter_target\")\n    local bounds = target:GetBoundingBox()\n    print(bounds:IsA(\"EntityBounds\"), bounds:GetCenter(), bounds:Contains(target.Position))\nend\n\nlocal targets = world:FindEntitiesInRadius(Vector3.new(0, 64, 0), 24, { tags = { \"encounter_target\" }, alive = true })\nfor _, entity in ipairs(targets) do\n    entity:SetGlowing(true)\nend");
        docs.service("EntityService", "MLuaEntityService",
            "Query, spawn, move, damage, and remove Minecraft entities from Lua.",
            "services/entity-service.html");
    }
}
