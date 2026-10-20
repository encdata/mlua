package dev.mlua.modules.model;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.model.service.MLuaModelService;
import dev.mlua.common.enums.Side;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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
        events.onClientWorldUnload(world -> {
            if (world instanceof Level level) {
                dev.mlua.runtime.client.MLuaClientModelManager.clearDimension(
                    level.dimension().location().toString(), Side.CLIENT);
            }
        });
        events.onClientEntityUnload(entity -> {
            if (entity instanceof Entity mcEntity) {
                dev.mlua.minecraft.animation.MLuaEntityScope scope =
                    dev.mlua.minecraft.animation.MLuaEntityScope.of(mcEntity);
                dev.mlua.runtime.client.MLuaClientModelManager.clearEntity(
                    scope);
                dev.mlua.runtime.client.MLuaModelAdapterRegistry.clearEntity(scope);
            }
        });
        events.onClientDisconnect(() -> {
            dev.mlua.runtime.client.MLuaClientModelManager.clearAll();
            dev.mlua.runtime.client.MLuaModelAdapterRegistry.clearAppliedEntities();
        });
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
            "Client-side model part overrides and addon-registered adapters for living models or models held by non-living entity renderers. Adapters can use static paths or a frame-scoped Lua resolver.",
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
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:RegisterAdapterResolver(modelClass: string, resolver: function(JavaObject): table<string, string>)", "Connection"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:UnregisterAdapterResolver(modelClass: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:RegisterCustomAdapter(modelClass: string, definition: CustomModelAdapterDefinition)", "Connection"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:UnregisterCustomAdapter(modelClass: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:UnregisterAdapter(modelClass: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:UnregisterAdapterBone(modelClass: string, boneName: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:RegisterRendererAdapter(rendererClass: string, modelFieldPath: string)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:UnregisterRendererAdapter(rendererClass: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:GetRendererAdapter(rendererClass: string)", "string|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:GetAdapter(modelClass: string)", "table<string, string>|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:GetCustomAdapter(modelClass: string)", "string[]|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:GetAppliedAdapter(entity: Entity|number)", "AppliedModelAdapter|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:GetAdapterLimits()", "ModelAdapterLimits"));
        docs.register(MLuaDocBuilder.Registration.signature("ModelService", "ModelService:GetOverrideLimits()", "ModelOverrideLimits"));
        docs.register(MLuaDocBuilder.Registration.type("AppliedModelAdapter", null, "The addon model adapter MLua resolved for the entity's most recently rendered model."));
        docs.register(MLuaDocBuilder.Registration.field("AppliedModelAdapter", "ModelClass", "string", "Full Java class name of the resolved model."));
        docs.register(MLuaDocBuilder.Registration.field("AppliedModelAdapter", "Mappings", "table<string, string>", "Merged animation bone names and model part paths for this model class."));
        docs.register(MLuaDocBuilder.Registration.field("AppliedModelAdapter", "ResolvedBones", "string[]", "Mapped bones whose paths resolved on the live model."));
        docs.register(MLuaDocBuilder.Registration.field("AppliedModelAdapter", "CustomBones", "string[]", "Custom bones declared by the model's Lua adapter and exposed to its render callbacks."));
        docs.register(MLuaDocBuilder.Registration.type("CustomModelAdapterDefinition", null, "Render callbacks for a model whose animated parts are not Minecraft ModelPart objects."));
        docs.register(MLuaDocBuilder.Registration.field("CustomModelAdapterDefinition", "Bones", "string[]", "Dense array of stable bone names used by animation and model part overrides."));
        docs.register(MLuaDocBuilder.Registration.field("CustomModelAdapterDefinition", "Apply", "function(JavaObject, Entity|nil, table<string, CustomModelBoneState>)", "Runs after setupAnim for living models and before the entity renderer for registered non-living models; use the supplied states to update the custom model."));
        docs.register(MLuaDocBuilder.Registration.field("CustomModelAdapterDefinition", "ApplyPose?", "function(JavaObject, string, JavaObject, Entity|nil, CustomModelBoneState): boolean", "Applies one custom bone transform to the supplied scoped PoseStack for attachment rendering; return true when handled. Without it, model animation still runs but attachments on these custom bones cannot follow the model."));
        docs.register(MLuaDocBuilder.Registration.type("CustomModelBoneState", null, "Animation and ModelService override values for one declared custom bone."));
        docs.register(MLuaDocBuilder.Registration.field("CustomModelBoneState", "Animation", "CFrame?", "Animation pose for the bone, when one is active."));
        docs.register(MLuaDocBuilder.Registration.field("CustomModelBoneState", "CFrame", "CFrame?", "ModelService transform override for the bone."));
        docs.register(MLuaDocBuilder.Registration.field("CustomModelBoneState", "Offset", "Vector3?", "ModelService translation override for the bone."));
        docs.register(MLuaDocBuilder.Registration.field("CustomModelBoneState", "Scale", "Vector3?", "ModelService scale override for the bone."));
        docs.register(MLuaDocBuilder.Registration.field("CustomModelBoneState", "Visible", "boolean?", "ModelService visibility override for the bone."));
        docs.register(MLuaDocBuilder.Registration.type("ModelAdapterLimits", null, "Current global use and capacity for model and renderer adapters, plus the calling addon's usage."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "ActiveModels", "number", "Number of Java model classes with static path adapters."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "ActiveParts", "number", "Number of active bone mappings across all model classes and addons."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "AddonParts", "number", "Static and custom bone mappings registered by the calling addon runtime."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "ActiveRenderers", "number", "Number of renderer classes with an explicit model field adapter."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "ActiveRendererAdapters", "number", "Number of addon-owned renderer-to-model field adapters across all renderer classes."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "AddonRendererAdapters", "number", "Renderer-to-model field adapters registered by the calling addon runtime."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "ActiveResolvers", "number", "Number of model classes with a Lua adapter resolver."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "AddonResolvers", "number", "Number of model adapter resolvers registered by the calling addon."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "ActiveCustomAdapters", "number", "Number of Java model classes with at least one custom model adapter."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "AddonCustomAdapters", "number", "Number of custom model adapters registered by the calling addon."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "ActiveCustomBones", "number", "Number of named custom bones registered across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxModels", "number", "Maximum number of adapted Java model classes."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxRenderers", "number", "Maximum number of adapted Java renderer classes."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxRendererAdapters", "number", "Maximum number of addon-owned renderer-to-model field adapters across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxRendererAdaptersPerAddon", "number", "Maximum number of renderer-to-model field adapters one addon can register."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxResolvers", "number", "Maximum number of Java model classes with Lua adapter resolvers."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxResolversPerAddon", "number", "Maximum model adapter resolvers one addon can register."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxCustomAdapters", "number", "Maximum Java model classes with custom model adapters."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxCustomAdaptersPerAddon", "number", "Maximum custom model adapters one addon can register."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxPartsPerModel", "number", "Maximum number of bones mapped to one Java model class."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxParts", "number", "Maximum number of active bone mappings across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxPartsPerAddon", "number", "Maximum static and custom bone mappings one addon runtime can register."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxResolverInstructions", "number", "Maximum interpreted Lua instructions allowed for each model adapter resolver call. Java/native work is not counted."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxClassNameLength", "number", "Maximum full Java class name length."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxBoneNameLength", "number", "Maximum animation bone name length."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxPathLength", "number", "Maximum dot-separated model part path length."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAdapterLimits", "MaxPathDepth", "number", "Maximum number of dot-separated fields or model part children in a path."));
        docs.register(MLuaDocBuilder.Registration.type("ModelOverrideLimits", null, "Current model part override usage and capacity."));
        docs.register(MLuaDocBuilder.Registration.field("ModelOverrideLimits", "ActiveOverrides", "number", "Number of stored overrides across all addons."));
        docs.register(MLuaDocBuilder.Registration.field("ModelOverrideLimits", "AddonOverrides", "number", "Number of overrides owned by the calling addon runtime."));
        docs.register(MLuaDocBuilder.Registration.field("ModelOverrideLimits", "MaxOverrides", "number", "Maximum number of stored overrides across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ModelOverrideLimits", "MaxOverridesPerAddon", "number", "Maximum number of overrides owned by one addon runtime."));
        docs.register(MLuaDocBuilder.Registration.field("ModelOverrideLimits", "MaxOverridesPerAddonPerEntity", "number", "Maximum number of model part overrides one addon runtime can store on one entity."));
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
        docs.register(MLuaDocBuilder.Registration.field("ModelOverrideLimits", "MaxPartNameLength", "number", "Maximum length of a model part name."));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Apply typed model transforms",
            "Model positions and scales use native Vector3 values, and rotation uses CFrame. Handmade coordinate tables and non-finite values are rejected. ModelService:GetOverrideLimits reports the active override count and the limits for the whole client, one addon runtime, and one entity.",
            "local Models = Minecraft:GetService(\"ModelService\")\nModels:SetPartCFrame(entity, \"head\", CFrame.new(0, 0.2, 0) * CFrame.Angles(0, math.rad(10), 0))\nModels:SetPartOffset(entity, \"rightArm\", Vector3.new(0.1, 0, 0))\nModels:SetPartScale(entity, \"head\", Vector3.new(1.1, 1.1, 1.1))\nModels:SetPart(entity, \"hat\", { Visible = false, Scale = Vector3.one })"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Map an addon model for animation",
            "Register the Java model class by its full class name. Keys are animation bone names; values are dot-separated ModelPart fields and child names. RegisterAdapter merges mappings for the calling addon; RegisterAdapterBone adds or updates one bone. A model subclass inherits parent mappings, then applies its own mappings. Static mappings are resolved without Lua callbacks. For models whose fields vary by instance, RegisterAdapterResolver receives the live model and returns the same bone-to-path table once for that model instance; its validated result is cached. Keep that resolver fast and structural: do not retain the model reference, yield, or change Minecraft state from it. The Java model reference expires when the resolver returns. An unresolved path is skipped and reported once in the log. GetAdapterLimits reports global and per-addon adapter limits, including 2,048 static and custom bones per addon runtime and 64 Lua resolvers per addon runtime.",
            "local Models = Minecraft:GetService(\"ModelService\")\nModels:RegisterAdapter(\"com.example.client.model.DragonModel\", {\n    body = \"root.body\",\n    head = \"root.neck.head\",\n    tail = \"root.body.tail\",\n    rightWing = \"root.rightWing\",\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Resolve a model's part paths in Lua",
            "Use a resolver when one model class stores its parts differently across concrete model instances. Resolvers run in registration order the first time each live model instance is adapted. They receive a frame-scoped JavaObject; use Java:GetField or Java:Invoke to inspect it and return bone names mapped to dot-separated ModelPart paths. MLua validates and caches each mapping for that model instance. Static mappings take precedence; among resolvers, the first registered path wins when later mappings conflict, and the later conflicting resolver is disabled. Errors disable a resolver for its owner and model class until it is registered again. Do not store the model JavaObject or yield from the resolver.",
            "local Models = Minecraft:GetService(\"ModelService\")\nlocal Java = Minecraft:GetService(\"Java\")\n\nlocal adapter = Models:RegisterAdapterResolver(\"com.example.client.model.DragonModel\", function(model)\n    local root = Java:GetField(model, \"root\")\n    if root == nil then return {} end\n    return {\n        body = \"root.body\",\n        head = \"root.neck.head\",\n        tail = \"root.body.tail\",\n    }\nend)\n\n-- Disconnect this exact resolver without affecting a later replacement.\nadapter:Disconnect()"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Adapt a custom model hierarchy",
            "Use RegisterCustomAdapter when the Java model is not built from Minecraft ModelPart objects, such as a library-backed or addon-defined model. Bones must be a dense array of stable names. Apply receives the live model, its entity when available, and per-bone animation/override state. MLua calls it after setupAnim for living models; for a non-living renderer it runs before the renderer starts because Minecraft has no shared post-setup model hook there. ApplyPose is optional and runs only when an attachment requests that bone; it receives the live PoseStack and must return true if it applied the transform. Without ApplyPose, custom model animation remains available, but attachments cannot inherit custom-bone transforms. Model and PoseStack Java references expire when each callback returns. Callbacks run on the client render thread with an instruction limit; MLua disables the adapter after a callback error. The adapter is removed when its addon runtime unloads.",
            "local Models = Minecraft:GetService(\"ModelService\")\n\nlocal adapter = Models:RegisterCustomAdapter(\"com.example.client.model.DragonModel\", {\n    Bones = { \"body\", \"head\", \"leftWing\", \"rightWing\" },\n    Apply = function(model, entity, bones)\n        local head = bones.head\n        if head.Animation then\n            -- Apply head.Animation to the model library's head node here.\n        end\n        if head.Visible == false then\n            -- Hide the model library's head node here.\n        end\n    end,\n    -- Add ApplyPose only when you need MLua attachments to follow custom bones.\n})\n\nadapter:Disconnect()"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Inspect an adapter",
            "GetAdapter returns the merged mappings registered for one exact Java model class. It returns nil when that class has no adapter. Use it to verify a model part path or build animation data against bone names contributed by other addons.",
            "local Models = Minecraft:GetService(\"ModelService\")\nlocal adapter = Models:GetAdapter(\"com.example.client.model.DragonModel\")\nif adapter then\n    for bone, partPath in pairs(adapter) do\n        print(bone, partPath)\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Inspect custom bones",
            "GetCustomAdapter returns the declared bone names for a custom adapter registered on that exact model class. Use the names consistently with AnimationService and ModelService part overrides.",
            "local Models = Minecraft:GetService(\"ModelService\")\nlocal bones = Models:GetCustomAdapter(\"com.example.client.model.DragonModel\")\nif bones then\n    for _, bone in ipairs(bones) do\n        print(bone)\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Inspect the adapter on a rendered entity",
            "GetAppliedAdapter reports the model class and part paths MLua resolved for the entity's most recently rendered model. It is nil until an adapter has been applied or a renderer-backed attachment has resolved that model.",
            "local Models = Minecraft:GetService(\"ModelService\")\nlocal applied = Models:GetAppliedAdapter(entity)\nif applied then\n    print(applied.ModelClass)\n    for bone, partPath in pairs(applied.Mappings) do\n        print(bone, partPath)\n    end\n    for _, bone in ipairs(applied.ResolvedBones) do\n        print(\"resolved\", bone)\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ModelService", "Resolve a model from a custom renderer",
            "If a renderer has exactly one field containing an adapted model, MLua can discover it automatically. If multiple adapted models are found, MLua skips the attachment and logs a warning instead of guessing. Register the exact renderer class and dot-separated Java field path to select the intended model; this also supports nested fields. MLua reads the path after the entity renderer submits its model, without calling Lua from the render hook. The mapping is owned by the registering addon and is removed on reload.",
            "local Models = Minecraft:GetService(\"ModelService\")\n\nModels:RegisterAdapter(\"com.example.client.model.ProjectileModel\", {\n    body = \"root\",\n    fins = \"root.fins\",\n})\nModels:RegisterRendererAdapter(\"com.example.client.render.ProjectileRenderer\", \"model\")\n\nprint(Models:GetRendererAdapter(\"com.example.client.render.ProjectileRenderer\"))"));
    }
}
