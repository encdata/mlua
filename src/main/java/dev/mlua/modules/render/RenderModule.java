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
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        dev.mlua.runtime.client.MLuaClientRenderManager.clearOwned(
            new dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner(runtimeSession, ownerModId));
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
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldRect(position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldText(text: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldImage(resource: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldItem(itemId: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBlock(blockId: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBillboard(resource: string, position: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldLine(start: Vector3|CFrame, finish: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBeam(start: Vector3|CFrame, finish: Vector3|CFrame, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderService", "RenderService:CreateWorldBox(center: Vector3|CFrame, size: Vector3, properties: RenderObjectProperties?)", "RenderObject"));
        docs.register(MLuaDocBuilder.Registration.property("RenderObject", "object.ClassName", "string", "The registered Lua class used by this render instance.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.signature("RenderObject", "RenderObject:SetWorldPosition(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RenderObject", "RenderObject:SetEndWorldPosition(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RenderObject", "RenderObject:SetTarget(target: string)", "Selects HUD, projected world, free world-model, or entity-model-part rendering."));
        docs.register(MLuaDocBuilder.Registration.signature("RenderObject", "RenderObject:PlayAnimation(name: string, options: ModelAnimationOptions?)", "void"));
        docs.register(MLuaDocBuilder.Registration.type("ModelAnimationOptions", null, "Playback options shared by entity-attached and standalone Blockbench models."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAnimationOptions", "Loop", "boolean", "Whether playback repeats. Defaults to true."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAnimationOptions", "Speed", "number", "Finite, non-negative playback speed. Defaults to one."));
        docs.register(MLuaDocBuilder.Registration.type("RenderObjectProperties", null, "Initial properties accepted when a render object is created. Use the RenderObject property names in PascalCase or lower camel case."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Position", "Vector2", "Initial screen position."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "ClassName", "string", "Registered RenderObject subclass to attach to this instance. The class and its inherited properties are preserved by RenderService:GetAll()."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "EndPosition", "Vector2", "Initial second screen position for line and beam objects."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Size", "Vector2", "Initial screen width and height."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Rotation", "number", "Initial screen rotation in degrees."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Visible", "boolean", "Whether the object starts visible."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Transparency", "number", "Initial transparency from zero to one. Provide Alpha or Transparency, not both."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Alpha", "number", "Initial opacity from zero to one; the inverse of Transparency. Provide Alpha or Transparency, not both."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Color", "number", "Initial packed color value."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Depth", "number", "Initial non-negative depth offset."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "ZIndex", "number", "Initial screen draw order."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "WorldPosition", "Vector3|CFrame", "Initial world position."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "EndWorldPosition", "Vector3|CFrame", "Initial endpoint for a world line or beam."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "WorldSpace", "boolean", "Whether the object is positioned in world space."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Target", "string", "Hud, WorldOverlay, WorldModel, or EntityModel for a model part attachment."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "ScaleWithDistance", "boolean", "Whether world-space size follows camera distance."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "ModelScale", "number", "Initial uniform scale for model objects."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "AnimationPath", "string", "Animation file to load with a Blockbench model. LoadBBModel also accepts the alias AnimationsPath."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "AnimationsPath", "string", "Alias for AnimationPath when loading a Blockbench model; provide only one."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Thickness", "number", "Initial line or outline thickness."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "FollowEntity", "Entity|number|nil", "Entity instance or id to follow."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "FollowBone", "string|nil", "Registered model adapter bone name for attaching a mesh or Blockbench model to the entity's posed model."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "FollowOffset", "CFrame|function", "Local transform after the followed model bone; a function returning CFrame is sampled once per client tick."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Resource", "string|nil", "Initial image, item, block, or model resource. Provide Resource or Text, not both."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Text", "string|nil", "Initial text for a text render object. Provide Text or Resource, not both."));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Create and update a render instance",
            "Render constructors return an owned RenderObject. HUD, projected-world, and 3D world-model draws use separate targets under the same object lifecycle. World-space positions accept native Vector3 or CFrame values; screen positions use Vector2. GuiService keeps its layout tree and screen lifecycle.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal label = Render:CreateText(\"The door is locked\", { Position = Vector2.new(20, 20), Target = \"Hud\" })\nlocal marker = Render:CreateWorldText(\"Exit\", CFrame.new(12, 65, -4), { Target = \"WorldOverlay\" })\n\nlabel.Position = Vector2.new(24, 32)\nlabel.Visible = true\nmarker.WorldPosition = CFrame.new(14, 65, -4)\nprint(label.Target, marker.Target)\nlabel:Destroy()\nmarker:Destroy()"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderObject", "Extend the render object class",
            "Register a subclass under RenderObject and pass its className in constructor properties. The handle keeps that class through GetAll, so addon methods and inherited properties remain available for the owned render object's lifetime.",
            "Minecraft:RegisterClass(\"WarningLabel\", \"RenderObject\", \"A warning label with a critical style.\")\nMinecraft:RegisterClassFunction(\"WarningLabel\", \"SetCriticalStyle\", function(self)\n    self:SetColor(0xFF3030)\n    self:SetZIndex(20)\nend, \"Applies the critical warning color and draw order.\")\n\nlocal Render = Minecraft:GetService(\"RenderService\")\nlocal label = Render:CreateText(\"Low health\", { ClassName = \"WarningLabel\", Position = Vector2.new(12, 12) })\nlabel:SetCriticalStyle()\nfor _, object in ipairs(Render:GetAll()) do\n    if object:IsA(\"WarningLabel\") then\n        print(object.ClassName, object.Text)\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Attach a 3D model to an entity part",
            "EntityModel draws a mesh or Blockbench model from a registered bone path after Minecraft has posed that entity. FollowOffset accepts a CFrame or a function returning a CFrame. MLua samples the function once per client tick and applies its returned position and rotation after the bone pose.",
            "local Models = Minecraft:GetService(\"ModelService\")\nlocal Render = Minecraft:GetService(\"RenderService\")\nlocal Players = Minecraft:GetService(\"Players\")\n\nModels:RegisterAdapter(\"net.minecraft.client.model.PlayerModel\", {\n    head = \"head\",\n})\n\nlocal hat = Render:LoadBBModel(\"myaddon:models/hat.bbmodel\")\nhat.Target = \"EntityModel\"\nhat.FollowEntity = Players.LocalPlayer\nhat.FollowBone = \"head\"\nlocal phase = 0\nhat.FollowOffset = function()\n    phase = phase + 0.1\n    return CFrame.new(0, 0.35 + math.sin(phase) * 0.02, 0)\n        * CFrame.Angles(0, math.sin(phase) * 0.08, 0)\nend\nhat.ModelScale = 1\nhat.Visible = true"));
    }
}
