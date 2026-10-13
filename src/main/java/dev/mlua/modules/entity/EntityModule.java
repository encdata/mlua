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
            .description("Query, spawn, move, damage, and remove Minecraft entities from Lua.");
        entity.function("GetById", "Returns a live entity instance by runtime ID.")
            .function("FindByUUID", "Returns a live entity instance by UUID, or nil when absent.")
            .function("GetAll", "Returns entities matching the supplied query options.")
            .function("GetNearby", "Returns entities near a position within the requested radius.")
            .function("Spawn", "Creates an entity at a position in a world.")
            .function("Remove", "Removes an entity from its world.")
            .function("Kill", "Kills a living entity.")
            .function("Teleport", "Moves an entity to a position and rotation.")
            .function("SetVelocity", "Sets an entity's motion vector.")
            .function("Damage", "Applies damage to a living entity.");
        return List.of(entity.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("Entity", "Entity:GetInventory()", "Container");
        docs.signature("Entity", "Entity:Damage(amount: number, sourceName: string, attacker: Entity)", null);
        docs.signature("Entity", "Entity:SetPosition(x: number, y: number, z: number)", null);
        docs.signature("Entity", "Entity:Teleport(x: number, y: number, z: number)", null);
        docs.signature("Entity", "Entity:LookAt(x: number, y: number, z: number)", null);
        docs.signature("Entity", "Entity:DistanceTo(entityOrPosition: any)", null);
        docs.signature("Entity", "Entity:GetEquipment(slot: string)", null);
        docs.signature("Entity", "Entity:SetEquipment(slot: string, item: Item)", null);
        docs.signature("Entity", "Entity:SetAttribute(name: string, value: number)", null);
        docs.signature("Entity", "Entity:GetAttribute(name: string)", null);
        docs.signature("Entity", "Entity:LoadAnimation(animation: Animation, priority: string, fadeTime: number)", null);
        docs.signature("Entity", "Entity:SetBonePose(bone: string, cframe: CFrame)", null);
        docs.signature("Entity", "Entity:GetBonePose(bone: string)", null);
        docs.signature("Entity", "Entity:ClearBonePose(bone: string)", null);
        docs.service("EntityService", "MLuaEntityService",
            "Query, spawn, move, damage, and remove Minecraft entities from Lua.",
            "services/entity-service.html");
    }
}
