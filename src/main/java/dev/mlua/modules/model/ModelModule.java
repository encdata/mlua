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
    public void registerEvents(dev.mlua.loaderapi.EventBridge events) {
        events.onClientDisconnect(dev.mlua.runtime.client.MLuaClientModelManager::clearAll);
    }

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
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:RegisterAdapterBone(modelClass: string, boneName: string, partPath: string)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:UnregisterAdapter(modelClass: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:UnregisterAdapterBone(modelClass: string, boneName: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:GetAdapter(modelClass: string)", "table<string, string>|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:GetAdapterLimits()", "ModelAdapterLimits"));
        docs.register(MLuaDocBuilder.Registration.type("ModelAdapterLimits", null, "Current global use and capacity for Lua-registered model adapters."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "ActiveModels", "number", "Number of model classes with an active adapter."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "ActiveParts", "number", "Number of active bone mappings across all model classes and addons."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxModels", "number", "Maximum number of adapted Java model classes."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxPartsPerModel", "number", "Maximum number of bones mapped to one Java model class."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxParts", "number", "Maximum number of active bone mappings across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxClassNameLength", "number", "Maximum full Java class name length."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxBoneNameLength", "number", "Maximum animation bone name length."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxPathLength", "number", "Maximum dot-separated model part path length."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxPathDepth", "number", "Maximum number of dot-separated fields or model part children in a path."));
        docs.register(MLuaDocBuilder.Registration.type("ModelPartProperties", null, "Overrides applied together to one model part."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartProperties", "CFrame", "CFrame?", "Translation and rotation offset from Minecraft's prepared pose; translation uses block units."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartProperties", "Offset", "Vector3?", "Additional translation offset in block units, added to the CFrame translation when both are set."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartProperties", "Scale", "Vector3?", "Per-axis scale."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartProperties", "Visible", "boolean?", "Whether the part is rendered."));
        docs.register(MLuaDocBuilder.Registration.type("ModelPartState", null, "The active override state for one model part."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartState", "CFrame", "CFrame?", "Active translation and rotation offset from Minecraft's prepared pose."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartState", "Offset", "Vector3?", "Additional active translation offset in block units."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartState", "Scale", "Vector3?", "Active per-axis scale."));
        docs.register(MLuaDocBuilder.Registration.field("ModelPartState", "Visible", "boolean?", "Active visibility override."));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Apply typed model transforms",
            "Model positions and scales use native Vector3 values, and rotation uses CFrame. Handmade coordinate tables are rejected.",
            "local Models = Minecraft:GetService(\"ModelService\")\nModels:SetPartCFrame(entity, \"head\", CFrame.new(0, 0.2, 0) * CFrame.Angles(0, math.rad(10), 0))\nModels:SetPartOffset(entity, \"rightArm\", Vector3.new(0.1, 0, 0))\nModels:SetPartScale(entity, \"head\", Vector3.new(1.1, 1.1, 1.1))\nModels:SetPart(entity, \"hat\", { Visible = false, Scale = Vector3.one })"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Map an addon model for animation",
            "Register the Java model class by its full class name. Keys are animation bone names; values are dot-separated ModelPart fields and child names. RegisterAdapter merges mappings for the calling addon; RegisterAdapterBone adds or updates one bone. Multiple addons can add different bone names to one model class, while conflicting names from other addons are rejected. A model subclass inherits its parent mappings, then replaces the bone names it declares. Paths are resolved against the live model on first render; an unresolved path is skipped and reported once in the log. The resolver uses the merged mapping during rendering and never calls Lua from the render hook. GetAdapterLimits reports the shared capacity: 256 classes, 512 bones per class, and 8192 bones total.",
            "local Models = Minecraft:GetService(\"ModelService\")\nModels:RegisterAdapter(\"com.example.client.model.DragonModel\", {\n    body = \"root.body\",\n    head = \"root.neck.head\",\n    tail = \"root.body.tail\",\n    rightWing = \"root.rightWing\",\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Inspect an adapter",
            "GetAdapter returns the merged mappings registered for one exact Java model class. It returns nil when that class has no adapter. Use it to verify a model part path or build animation data against bone names contributed by other addons.",
            "local Models = Minecraft:GetService(\"ModelService\")\nlocal adapter = Models:GetAdapter(\"com.example.client.model.DragonModel\")\nif adapter then\n    for bone, partPath in pairs(adapter) do\n        print(bone, partPath)\n    end\nend"));
    }
}
