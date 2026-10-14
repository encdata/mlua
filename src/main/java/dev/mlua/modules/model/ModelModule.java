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
        docs.signature("ModelService", "ModelService:SetPartCFrame(entity: Entity|number, part: string, transform: CFrame)", "void");
        docs.signature("ModelService", "ModelService:SetPartOffset(entity: Entity|number, part: string, offset: Vector3)", "void");
        docs.signature("ModelService", "ModelService:SetPartScale(entity: Entity|number, part: string, scale: Vector3)", "void");
        docs.signature("ModelService", "ModelService:SetPartVisible(entity: Entity|number, part: string, visible: boolean)", "void");
        docs.signature("ModelService", "ModelService:SetPart(entity: Entity|number, part: string, properties: ModelPartProperties)", "void");
        docs.signature("ModelService", "ModelService:ClearPart(entity: Entity|number, part: string)", "void");
        docs.signature("ModelService", "ModelService:ClearModel(entity: Entity|number)", "void");
        docs.signature("ModelService", "ModelService:ClearAll()", "void");
        docs.signature("ModelService", "ModelService:GetPartNames(entity: Entity|number)", "string[]");
        docs.signature("ModelService", "ModelService:GetModelState(entity: Entity|number)", "table<string, ModelPartState>");
        docs.signature("ModelService", "ModelService:ResolvePartName(name: string)", "string");
        docs.type("ModelPartProperties", null, "Overrides applied together to one model part.");
        docs.field("ModelPartProperties", "CFrame", "CFrame?", "Position and rotation override.");
        docs.field("ModelPartProperties", "Offset", "Vector3?", "Translation offset.");
        docs.field("ModelPartProperties", "Scale", "Vector3?", "Per-axis scale.");
        docs.field("ModelPartProperties", "Visible", "boolean?", "Whether the part is rendered.");
        docs.type("ModelPartState", null, "The active override state for one model part.");
        docs.field("ModelPartState", "CFrame", "CFrame?", "Active position and rotation override.");
        docs.field("ModelPartState", "Offset", "Vector3?", "Active translation offset.");
        docs.field("ModelPartState", "Scale", "Vector3?", "Active per-axis scale.");
        docs.field("ModelPartState", "Visible", "boolean?", "Active visibility override.");
        docs.guideStep("ModelService", "Apply typed model transforms",
            "Model positions and scales use native Vector3 values, and rotation uses CFrame. Handmade coordinate tables are rejected.",
            "local Models = Minecraft:GetService(\"ModelService\")\nModels:SetPartCFrame(entity, \"head\", CFrame.new(0, 0.2, 0) * CFrame.Angles(0, math.rad(10), 0))\nModels:SetPartOffset(entity, \"rightArm\", Vector3.new(0.1, 0, 0))\nModels:SetPartScale(entity, \"head\", Vector3.new(1.1, 1.1, 1.1))\nModels:SetPart(entity, \"hat\", { Visible = false, Scale = Vector3.one })");
    }
}
