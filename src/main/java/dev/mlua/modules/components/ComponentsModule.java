package dev.mlua.modules.components;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.components.Service.MLuaComponentsService;
import dev.mlua.common.enums.Side;
import dev.mlua.runtime.entity.MLuaEntityComponentManager;
import java.util.List;
import java.util.Map;

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
    public List<ServiceEntry> services() {
        ServiceEntry.Builder components = ServiceEntry.builder("Components", MLuaComponentsService::new)
            .description("Attach reusable Lua behavior components to entities with tick and event callbacks.");
        components.function("Attach", "Attaches a named Lua behavior component to an entity.")
            .function("Get", "Returns one of the calling mod's components attached to an entity.")
            .function("List", "Returns component names attached by the calling mod to an entity.")
            .function("Remove", "Removes one of the calling mod's components from an entity.")
            .function("Clear", "Removes all components attached by the calling mod to an entity.");
        return List.of(components.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Components", "MLuaComponentsService",
            "Attach reusable Lua behavior components to entities with tick and event callbacks.",
            "services/components.html");
        docs.clazz("EntityComponent", "MLuaComponentsService",
            "Mutable entity behavior component handle returned by Components:Attach.",
            "classes/entity-component.html", "Instance");
    }
}
