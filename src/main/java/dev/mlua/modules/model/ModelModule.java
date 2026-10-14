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
            .description("Client-side model part overrides for humanoid and layered cosmetic parts.");
        model.function("ResolvePartName", "Normalizes a model part name for the active renderer.")
            .function("SetPartCFrame", "Sets a model part transform override.")
            .function("SetPartOffset", "Sets a model part position and rotation offset.")
            .function("SetPartScale", "Sets a model part scale override.")
            .function("SetPartVisible", "Sets whether a model part is rendered.")
            .function("SetPart", "Sets multiple model part overrides from a definition table.")
            .function("ClearPart", "Clears overrides for one model part.")
            .function("ClearModel", "Clears overrides for one entity model.")
            .function("ClearAll", "Clears model overrides owned by the calling script.")
            .function("Clear", "Clears model overrides owned by the calling script.")
            .function("GetModelState", "Returns current override state for an entity model.")
            .function("GetPartNames", "Returns the model part names supported by the active renderer.");
        return List.of(model.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("ModelService", "MLuaModelService",
            "Client-side model part overrides for humanoid and layered cosmetic parts.",
            "services/model.html");
    }
}
