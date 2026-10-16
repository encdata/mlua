package dev.mlua.modules.render;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.render.Service.MLuaRenderService;
import dev.mlua.common.enums.Side;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public final class RenderModule implements MLuaModule {
    @Override
    public String id() {
        return "render";
    }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        dev.mlua.runtime.client.MLuaClientRenderManager.clearRuntime(runtimeSession);
        dev.mlua.runtime.client.MLuaRenderCallbacks.clearRuntime(runtimeSession);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        dev.mlua.runtime.client.MLuaClientRenderManager.clearOwned(
            new dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner(runtimeSession, ownerModId));
        dev.mlua.runtime.client.MLuaRenderCallbacks.clearOwned(runtimeSession, ownerModId);
    }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override public List<String> dependencies() { return List.of("core-classes"); }

    @Override public void registerClasses(MLuaRegistration registration) {
        MLuaRenderService.registerRenderObjectClass(registration);
    }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("RenderService", MLuaRenderService::new)
            .reflective(MLuaRenderService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("RenderService", "MLuaRenderService",
            "Client-side HUD and projected world-space renderables.",
            "services/render.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("RenderObject", "MLuaRenderService",
            "A live client render instance with mutable screen-space or world-space properties and rendering methods.",
            "classes/render-object.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateRect(properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateText(text: string, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateImage(resource: string, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateItem(itemId: string, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateBlock(blockId: string, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateMesh(definition: MeshDefinition, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:LoadBBModel(path: string, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldRect(position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldText(text: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldImage(resource: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldItem(itemId: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBlock(blockId: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBillboard(resource: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldLine(start: Vector3|CFrame, finish: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBeam(start: Vector3|CFrame, finish: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBox(center: Vector3|CFrame, size: Vector3, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:Clear()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:GetAll()", "RenderObject[]"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterEntityRenderer(entityType: string, callback: function(Entity, RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterEntityRenderer(entityType: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterWorldRenderer(callback: function(RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterWorldRenderer()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:RegisterHudRenderer(callback: function(RenderContext))", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:UnregisterHudRenderer()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.type("RenderContext", null, "Native values supplied at a HUD, world, or living entity render point. All Java objects are frame-scoped; callbacks must not retain them or yield."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "PoseStack", "Java object|nil", "The live com.mojang.blaze3d.vertex.PoseStack when the loader exposes one."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "BufferSource", "Java object|nil", "Minecraft's live MultiBufferSource, render submit collector, or nil when the loader event does not expose one."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "Model", "Java object|nil", "The native entity model after Minecraft has applied its animation pose."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "PartialTick", "number", "Frame interpolation from zero to one where the render API provides it; 26.1 extraction render hooks currently pass zero."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "PackedLight", "number", "Minecraft packed light value when supplied by the entity render hook."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "PackedOverlay", "number", "Minecraft packed overlay value when supplied by the entity render hook."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "Target", "string", "Render callback target: EntityModel, World, or Hud."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "NativeContext", "Java object|nil", "The loader's native render event/context for world render callbacks."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "Camera", "Java object|nil", "The native render camera for world render callbacks."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "Graphics", "Java object|nil", "The live GuiGraphics for HUD callbacks."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "Width", "number|nil", "HUD width in GUI-scaled pixels."));
        docs.register(MLuaDocBuilder.Registration.field("RenderContext", "Height", "number|nil", "HUD height in GUI-scaled pixels."));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetWorldPosition(position: Vector3|CFrame)", "Sets this object's world position.", "Native Vector3 or CFrame", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetWorldPosition()", "Returns this object's world position as a native Vector3.", null, "Vector3", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetEndWorldPosition(position: Vector3|CFrame)", "Sets the endpoint used by world lines and beams.", "Native Vector3 or CFrame", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetEndWorldPosition()", "Returns the world endpoint as a native Vector3.", null, "Vector3", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetTarget()", "Returns the canonical render target name.", null, "string", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetTarget(target: string)", "Selects where this object is drawn.", "Hud, WorldOverlay, WorldModel, or EntityModel; matching is case-insensitive", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:PlayAnimation(name: string, options: ModelAnimationOptions?)", "Plays a named Blockbench model animation.", "Animation name and optional playback settings", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetAnimations()", "Lists animation names on a loaded Blockbench model.", null, "string[]", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:StopAnimation()", "Stops the current Blockbench model animation.", null, "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetAnimationTime()", "Returns elapsed playback time for the current Blockbench model animation.", null, "number", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:GetFollowEntity()", "Returns the followed entity's runtime id, or nil when unattached.", null, "number|nil", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetFollowEntity(entity?: Entity|number)", "Attaches this render object to an entity or clears the attachment with nil.", "Optional live Entity instance or runtime id", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:ClearFollowEntity()", "Removes this object's entity attachment.", null, "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetFollowBone(bone?: string)", "Attaches this object to a named bone on the followed entity's registered model adapter.", "Registered bone name, or nil to clear", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:SetFollowOffset(offset: CFrame|function)", "Sets a local transform after the followed bone. A callback is sampled once per client tick and must return a CFrame.", "CFrame value or callback returning CFrame", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("RenderObject", "RenderObject:Destroy()", "Removes this render object and releases its owned render state.", null, "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.type("ModelAnimationOptions", null, "Playback options shared by entity-attached and standalone Blockbench models."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAnimationOptions", "Loop", "boolean", "Whether playback repeats. Defaults to true."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAnimationOptions", "Speed", "number", "Finite, non-negative playback speed. Defaults to one."));
        docs.register(MLuaDocBuilder.Registration.type("MeshDefinition", null, "Vertex data, zero-based triangle indices, and optional texture for RenderService:CreateMesh."));
        docs.register(MLuaDocBuilder.Registration.field("MeshDefinition", "Vertices", "MeshVertex[]", "At least three vertices used by the mesh."));
        docs.register(MLuaDocBuilder.Registration.field("MeshDefinition", "Indices", "number[]", "One or more complete triangles. Each zero-based index must refer to an entry in Vertices."));
        docs.register(MLuaDocBuilder.Registration.field("MeshDefinition", "Texture?", "string", "Optional texture resource identifier."));
        docs.register(MLuaDocBuilder.Registration.type("MeshVertex", null, "One mesh vertex with a world position, optional texture coordinate, and optional packed color."));
        docs.register(MLuaDocBuilder.Registration.field("MeshVertex", "Position", "Vector3|CFrame", "Finite position in mesh-local space, relative to RenderObject.WorldPosition."));
        docs.register(MLuaDocBuilder.Registration.field("MeshVertex", "UV?", "Vector2", "Optional texture coordinate."));
        docs.register(MLuaDocBuilder.Registration.field("MeshVertex", "Color?", "number", "Optional packed 32-bit vertex color. Defaults to opaque white."));
        docs.register(MLuaDocBuilder.Registration.type("RenderObjectProperties", null, "Initial properties accepted when a render object is created. Use the RenderObject property names in PascalCase or lower camel case."));
        MLuaRenderService.contributeRenderObjectPropertyDocs(docs);
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "ClassName", "string", "Registered RenderObject subclass to attach to this instance. The class and its inherited properties are preserved by RenderService:GetAll()."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "AnimationPath", "string", "Animation file to load with a Blockbench model. LoadBBModel also accepts the alias AnimationsPath."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "AnimationsPath", "string", "Alias for AnimationPath when loading a Blockbench model; provide only one."));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Create and update a render instance",
            "Render constructors return an owned RenderObject. HUD, projected-world, and 3D world-model draws use separate targets under the same object lifecycle. World-space positions accept native Vector3 or CFrame values; screen positions use Vector2. GuiService keeps its layout tree and screen lifecycle.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal label = Render:CreateText(\"The door is locked\", { Position = Vector2.new(20, 20), Target = \"Hud\" })\nlocal marker = Render:CreateWorldText(\"Exit\", CFrame.new(12, 65, -4), { Target = \"WorldOverlay\" })\n\nlabel.Position = Vector2.new(24, 32)\nlabel.Visible = true\nmarker.WorldPosition = CFrame.new(14, 65, -4)\nprint(label.Target, marker.Target)\nlabel:Destroy()\nmarker:Destroy()"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderObject", "Extend the render object class",
            "Register a subclass under RenderObject and pass its className in constructor properties. The handle keeps that class through GetAll, so addon methods and inherited properties remain available for the owned render object's lifetime.",
            "Minecraft:RegisterClass(\"WarningLabel\", \"RenderObject\", \"A warning label with a critical style.\")\nMinecraft:RegisterClassFunction(\"WarningLabel\", \"SetCriticalStyle\", function(self)\n    self:SetColor(0xFF3030)\n    self:SetZIndex(20)\nend, \"Applies the critical warning color and draw order.\")\n\nlocal Render = Minecraft:GetService(\"RenderService\")\nlocal label = Render:CreateText(\"Low health\", { ClassName = \"WarningLabel\", Position = Vector2.new(12, 12) })\nlabel:SetCriticalStyle()\nfor _, object in ipairs(Render:GetAll()) do\n    if object:IsA(\"WarningLabel\") then\n        print(object.ClassName, object.Text)\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Attach a 3D model to an entity part",
            "EntityModel draws a mesh or Blockbench model from a registered bone path after Minecraft has posed that entity. FollowOffset accepts a CFrame or a function returning a CFrame. MLua samples the function once per client tick and applies its returned position and rotation after the bone pose.",
            "local Models = Minecraft:GetService(\"ModelService\")\nlocal Render = Minecraft:GetService(\"RenderService\")\nlocal Players = Minecraft:GetService(\"Players\")\n\nModels:RegisterAdapter(\"net.minecraft.client.model.PlayerModel\", {\n    head = \"head\",\n})\n\nlocal hat = Render:LoadBBModel(\"myaddon:models/hat.bbmodel\")\nhat.Target = \"EntityModel\"\nhat.FollowEntity = Players.LocalPlayer\nhat.FollowBone = \"head\"\nlocal phase = 0\nhat.FollowOffset = function()\n    phase = phase + 0.1\n    return CFrame.new(0, 0.35 + math.sin(phase) * 0.02, 0)\n        * CFrame.Angles(0, math.sin(phase) * 0.08, 0)\nend\nhat.ModelScale = 1\nhat.Visible = true"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Hook a native entity render point",
            "RegisterEntityRenderer adds an addon-owned callback after Minecraft has posed a living entity's model and before its model is drawn. It follows the same render-point pattern as a native entity renderer, while the callback receives the real entity, PoseStack, buffer source, model, partial tick, light, and overlay. Use Java:Invoke or Java object proxies for native rendering calls. The render context is frame-scoped: do not save it, yield from the callback, or use it after the callback returns. A callback error disables that callback for the rest of the session.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal Java = Minecraft:GetService(\"Java\")\n\nRender:RegisterEntityRenderer(\"myaddon:stalker\", function(entity, context)\n    local pose = context.PoseStack\n    Java:Invoke(pose, \"pushPose\", \"()V\")\n    -- Use the live pose, model, and buffer source for a native render pass.\n    Java:Invoke(pose, \"popPose\", \"()V\")\nend)\n\n-- Remove this addon's renderer when it is no longer needed.\nRender:UnregisterEntityRenderer(\"myaddon:stalker\")"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Attach a world or HUD render callback",
            "World callbacks run through the loader's native world-render event; HUD callbacks run in MLua's registered GUI layer before MLua RenderObjects. Both callbacks receive a frame-scoped RenderContext. Use Java:Invoke or Java:Proxy to call Minecraft rendering APIs, and never retain frame objects or yield from a callback.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal Java = Minecraft:GetService(\"Java\")\n\nRender:RegisterHudRenderer(function(context)\n    local graphics = context.Graphics\n    -- Call GuiGraphics methods here through Java:Invoke.\nend)\n\nRender:RegisterWorldRenderer(function(context)\n    -- NativeContext, PoseStack, BufferSource and Camera are supplied when that\n    -- loader's stage exposes them.\nend)\n\nRender:UnregisterHudRenderer()\nRender:UnregisterWorldRenderer()"));
    }
}
