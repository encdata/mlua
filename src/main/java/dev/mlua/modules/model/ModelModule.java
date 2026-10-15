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
        dev.mlua.runtime.client.MLuaModelAdapterRegistry.clearRuntime(runtimeSession);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        dev.mlua.runtime.client.MLuaClientModelManager.clearOwned(
            new dev.mlua.runtime.client.MLuaClientModelManager.Owner(runtimeSession, ownerModId));
        dev.mlua.runtime.client.MLuaModelAdapterRegistry.clearOwned(
            new dev.mlua.runtime.client.MLuaModelAdapterRegistry.Owner(runtimeSession, ownerModId));
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
        docs.register(MLuaDocBuilder.Registration.service("ModelService", "MLuaModelService",
            "Client-side model part overrides and Lua-registered bone adapters for living entity models.",
            "services/model.html"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:SetPartCFrame(entity: Entity|number, part: string, transform: CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:SetPartOffset(entity: Entity|number, part: string, offset: Vector3)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:SetPartScale(entity: Entity|number, part: string, scale: Vector3)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:SetPartVisible(entity: Entity|number, part: string, visible: boolean)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:SetPart(entity: Entity|number, part: string, properties: ModelPartProperties)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:ClearPart(entity: Entity|number, part: string)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:ClearModel(entity: Entity|number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:ClearAll()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:GetPartNames(entity: Entity|number)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:GetModelState(entity: Entity|number)", "table<string, ModelPartState>"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:ResolvePartName(name: string)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:RegisterAdapter(modelClass: string, parts: table<string, string>)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:UnregisterAdapter(modelClass: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.type("ModelPartProperties", null, "Overrides applied together to one model part."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartProperties", "CFrame", "CFrame?", "Position and rotation override."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartProperties", "Offset", "Vector3?", "Translation offset."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartProperties", "Scale", "Vector3?", "Per-axis scale."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartProperties", "Visible", "boolean?", "Whether the part is rendered."));
        docs.register(MLuaDocBuilder.Registration.type("ModelPartState", null, "The active override state for one model part."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartState", "CFrame", "CFrame?", "Active position and rotation override."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartState", "Offset", "Vector3?", "Active translation offset."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartState", "Scale", "Vector3?", "Active per-axis scale."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartState", "Visible", "boolean?", "Active visibility override."));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Apply typed model transforms",
            "Model positions and scales use native Vector3 values, and rotation uses CFrame. Handmade coordinate tables are rejected.",
            "local Models = Minecraft:GetService(\"ModelService\")\nModels:SetPartCFrame(entity, \"head\", CFrame.new(0, 0.2, 0) * CFrame.Angles(0, math.rad(10), 0))\nModels:SetPartOffset(entity, \"rightArm\", Vector3.new(0.1, 0, 0))\nModels:SetPartScale(entity, \"head\", Vector3.new(1.1, 1.1, 1.1))\nModels:SetPart(entity, \"hat\", { Visible = false, Scale = Vector3.one })"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Map an addon model for animation",
            "Register your living entity's Java model class by its full class name. Keys are animation bone names; values are dot-separated ModelPart fields and child names. The resolver uses this mapping during rendering and never calls Lua from the render hook. Each model class has one adapter owner.",
            "local Models = Minecraft:GetService(\"ModelService\")\nModels:RegisterAdapter(\"com.example.client.model.DragonModel\", {\n    body = \"root.body\",\n    head = \"root.neck.head\",\n    tail = \"root.body.tail\",\n    rightWing = \"root.rightWing\",\n})"));
    }
}
