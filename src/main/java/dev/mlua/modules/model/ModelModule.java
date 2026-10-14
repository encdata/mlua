package dev.mlua.modules.model;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.model.Service.MLuaModelService;
import dev.mlua.common.enums.Side;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public final class ModelModule implements MLuaModule {
    @Override
    public String id() {
        return "model";
    }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        dev.mlua.runtime.client.MLuaClientModelManager.clearRuntime(runtimeSession);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        dev.mlua.runtime.client.MLuaClientModelManager.clearOwned(
            new dev.mlua.runtime.client.MLuaClientModelManager.Owner(runtimeSession, ownerModId));
    }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder model = ServiceEntry.builder("ModelService", MLuaModelService::new)
            .reflective(MLuaModelService.class);
        return List.of(model.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("ModelService", "MLuaModelService",
            "Client-side model part overrides for humanoid and layered cosmetic parts.",
            "services/model.html");
    }
}
