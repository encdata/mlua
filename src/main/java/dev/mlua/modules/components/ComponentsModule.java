package dev.mlua.modules.components;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.modules.components.Service.MLuaComponentsService;
import dev.mlua.common.enums.Side;
import dev.mlua.runtime.entity.MLuaEntityComponentManager;
import java.util.List;

public final class ComponentsModule implements MLuaModule {
    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        if (runtimeSession instanceof dev.mlua.core.MLuaRuntimeSession session) {
            MLuaEntityComponentManager.clearRuntime(session, side);
        }
    }

    @Override
    public void onRuntimeTick(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, double deltaTime) {
        MLuaEntityComponentManager.tick(side);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        MLuaEntityComponentManager.clearOwned((dev.mlua.core.MLuaRuntimeSession) runtimeSession, side, ownerModId);
    }

    @Override
    public String id() {
        return "components";
    }

    @Override
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("EntityComponent", registration.requireInstanceBaseClassName(),
            "A live entity component with state, enable control, and lifecycle methods.");
        String[][] methods = {
            {"GetName", "Returns this component's registered name."},
            {"GetEntity", "Returns the entity that owns this component."},
            {"GetState", "Returns this component's state table or one state value."},
            {"SetState", "Sets one value in this component's state table."},
            {"GetEnabled", "Returns whether this component is enabled."},
            {"SetEnabled", "Enables or disables this component."},
            {"Destroy", "Removes this component from its entity."}
        };
        for (String[] method : methods) {
            MLuaClassMethods.registerInstanceMethod(registration, "EntityComponent", method[0], method[1]);
        }
        MLuaClassMethods.registerInstanceProperty(registration, "EntityComponent", "Name", "GetName", null,
            "string", "shared:Shared", "This component's registered name.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityComponent", "Entity", "GetEntity", null,
            "Entity", "shared:Shared", "The entity that owns this component.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityComponent", "State", "GetState", null,
            "table", "shared:Shared", "The mutable state table owned by this component.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityComponent", "Enabled", "GetEnabled", "SetEnabled",
            "boolean", "shared:Shared", "Whether this component is enabled.");
    }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Components", MLuaComponentsService::new)
            .reflective(MLuaComponentsService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Components", "MLuaComponentsService",
            "Attach owner-scoped Lua behavior components to entities with mutable state, lifecycle callbacks, and deterministic lookup.",
            "services/components.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("EntityComponent", null,
            "Mutable entity behavior component handle returned by Components:Attach.",
            "classes/entity-component.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:Find(entity: Entity, name: string): EntityComponent?", "EntityComponent or nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:Has(entity: Entity, name: string): boolean", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:GetAll(entity: Entity): EntityComponent[]", "name-sorted component handles"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:Remove(entity: Entity, name: string): boolean", "true if a component was removed"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:Clear(entity: Entity): number", "number of removed components"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Components", "Attach state and callbacks to an entity",
            "A component is scoped to the calling mod and runtime. Its State table persists while the component is attached. Reattaching the same name updates callbacks and keeps the existing state; OnAdded runs only for a new attachment. Callback payloads expose Entity, Component, State, and Event.",
            "local Components = Minecraft:GetService(\"Components\")\nlocal component = Components:Attach(entity, \"patrol_state\", {\n    State = { ticks = 0 },\n    OnAdded = function(event)\n        print(\"component attached to\", event.Entity.Name)\n    end,\n    OnTick = function(event)\n        event.State.ticks = event.State.ticks + 1\n    end,\n    OnHurt = function(event)\n        print(\"entity took damage\", event.Amount)\n    end,\n    OnRemoved = function(event)\n        print(\"component removed\", event.Component.Name)\n    end,\n})\n\nif Components:Has(entity, \"patrol_state\") then\n    print(component.State.ticks)\nend\n\nfor _, attached in ipairs(Components:GetAll(entity)) do\n    print(attached.Name)\nend"));
    }
}
