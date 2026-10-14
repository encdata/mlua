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
