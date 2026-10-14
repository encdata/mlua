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
        docs.signature("Entity", "Entity:Kill()", "void");
        docs.signature("Entity", "Entity:Remove()", "void");
        docs.signature("LivingEntity", "LivingEntity:GetHealth()", "number");
        docs.signature("LivingEntity", "LivingEntity:SetHealth(value: number)", "void");
        docs.signature("LivingEntity", "LivingEntity:GetMaxHealth()", "number");
        docs.signature("LivingEntity", "LivingEntity:GetArmor()", "number");
        docs.signature("LivingEntity", "LivingEntity:Damage(amount: number, sourceName: string?, attacker: Entity?)", "void");
        docs.signature("LivingEntity", "LivingEntity:GetAttribute(name: string)", "number?");
        docs.signature("LivingEntity", "LivingEntity:SetAttribute(name: string, value: number)", "boolean");
        docs.signature("LivingEntity", "LivingEntity:GetAttributeInstance(name: string)", "AttributeInstance?");
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
        docs.signature("EntityService", "EntityService:Spawn(typeId: string, position: Vector3|CFrame, world: World?)", "Entity");
        docs.signature("EntityService", "EntityService:Teleport(entity: Entity, position: Vector3|CFrame)", "void");
        docs.signature("EntityService", "EntityService:SetVelocity(entity: Entity, velocity: Vector3)", "void");
        docs.signature("EntityService", "EntityService:Damage(entity: Entity, amount: number)", "boolean");
        docs.signature("EntityService", "EntityService:Kill(entity: Entity)", "void");
        docs.signature("EntityService", "EntityService:Remove(entity: Entity)", "void");
        docs.service("EntityService", "MLuaEntityService",
            "Query, spawn, move, damage, and remove Minecraft entities from Lua.",
            "services/entity-service.html");
    }
}
