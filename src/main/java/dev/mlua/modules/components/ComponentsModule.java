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
            "An entity behavior component with persistent state and lifecycle callbacks. A callback error disables the component, records its message in Error, and is logged without interrupting other entity behavior.");
        String[][] methods = {
            {"GetName", "Returns this component's registered name."},
            {"GetEntity", "Returns the entity that owns this component."},
            {"GetState", "Returns this component's state table or one state value."},
            {"SetState", "Sets one value in this component's state table."},
            {"GetEnabled", "Returns whether this component is enabled."},
            {"SetEnabled", "Enables or disables this component."},
            {"GetFailed", "Returns whether a component callback failed."},
            {"GetError", "Returns the latest callback error message, or nil when no callback has failed."},
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
        MLuaClassMethods.registerInstanceProperty(registration, "EntityComponent", "Failed", "GetFailed", null,
            "boolean", "shared:Shared", "Whether a component callback failed. Failed components cannot be re-enabled; reattach the same name with corrected callbacks to recover.");
        MLuaClassMethods.registerInstanceProperty(registration, "EntityComponent", "Error", "GetError", null,
            "string?", "shared:Shared", "The most recent component callback error message, or nil before a callback fails.");
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
            "Attach owner-scoped Lua behavior components to entities with mutable state, lifecycle callbacks, deterministic callback order, and bounded capacity.",
            "services/components.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("EntityComponent", null,
            "Mutable entity behavior component handle returned by Components:Attach. Failed callbacks disable the component, set Failed and Error, and do not interrupt other callbacks.",
            "classes/entity-component.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:Find(entity: Entity, name: string): EntityComponent?", "EntityComponent or nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:Has(entity: Entity, name: string): boolean", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:GetAll(entity: Entity): EntityComponent[]", "name-sorted component handles"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:Remove(entity: Entity, name: string): boolean", "true if a component was removed"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:Clear(entity: Entity): number", "number of removed components"));
        docs.register(MLuaDocBuilder.Registration.signature("Components", "Components:GetLimits(): ComponentLimits", "Active component usage and capacity."));
        docs.register(MLuaDocBuilder.Registration.type("ComponentLimits", null, "Active entity behavior component usage and configured capacity."));
        docs.register(MLuaDocBuilder.Registration.field("ComponentLimits", "Active", "number", "Number of attached components across all runtimes."));
        docs.register(MLuaDocBuilder.Registration.field("ComponentLimits", "MaxTotal", "number", "Maximum attached components across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ComponentLimits", "MaxPerEntity", "number", "Maximum components attached to one entity."));
        docs.register(MLuaDocBuilder.Registration.guideStep("Components", "Attach state and callbacks to an entity",
            "A component is scoped to the calling mod and runtime. Its State table persists while attached. Reattaching the same name updates callbacks and keeps existing state; OnAdded runs only for a new attachment. Components tick and receive events in mod id, component name, then registration order. Callback payloads expose Entity, Component, State, and Event. A failing callback disables that component and records its error without stopping other components. Reattach it to recover while preserving State. Use GetLimits to check current capacity.",
            "local Components = Minecraft:GetService(\"Components\")\nlocal component = Components:Attach(entity, \"patrol_state\", {\n    State = { ticks = 0 },\n    OnAdded = function(event)\n        print(\"component attached to\", event.Entity.Name)\n    end,\n    OnTick = function(event)\n        event.State.ticks = event.State.ticks + 1\n    end,\n    OnHurt = function(event)\n        print(\"entity took damage\", event.Amount)\n    end,\n    OnRemoved = function(event)\n        print(\"component removed\", event.Component.Name)\n    end,\n})\n\nif Components:Has(entity, \"patrol_state\") then\n    print(component.State.ticks)\nend\n\nfor _, attached in ipairs(Components:GetAll(entity)) do\n    print(attached.Name)\nend"));
    }
}
