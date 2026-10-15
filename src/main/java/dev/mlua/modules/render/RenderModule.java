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
        docs.register(MLuaDocBuilder.Registration.signature("RenderObject", "RenderObject:SetWorldPosition(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RenderObject", "RenderObject:SetEndWorldPosition(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RenderObject", "RenderObject:PlayAnimation(name: string, options: ModelAnimationOptions?)", "void"));
        docs.register(MLuaDocBuilder.Registration.type("ModelAnimationOptions", null, "Playback options shared by entity-attached and standalone Blockbench models."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAnimationOptions", "Loop", "boolean", "Whether playback repeats. Defaults to true."));
        docs.register(MLuaDocBuilder.Registration.field("ModelAnimationOptions", "Speed", "number", "Finite, non-negative playback speed. Defaults to one."));
        docs.register(MLuaDocBuilder.Registration.type("RenderObjectProperties", null, "Initial properties accepted when a render object is created. Use the RenderObject property names in PascalCase or lower camel case."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Position", "Vector2", "Initial screen position."));
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
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "ScaleWithDistance", "boolean", "Whether world-space size follows camera distance."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "ModelScale", "number", "Initial uniform scale for model objects."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "AnimationPath", "string", "Animation file to load with a Blockbench model. LoadBBModel also accepts the alias AnimationsPath."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "AnimationsPath", "string", "Alias for AnimationPath when loading a Blockbench model; provide only one."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Thickness", "number", "Initial line or outline thickness."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "FollowEntity", "Entity|number|nil", "Entity instance or id to follow."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Resource", "string|nil", "Initial image, item, block, or model resource. Provide Resource or Text, not both."));
        docs.register(MLuaDocBuilder.Registration.field("RenderObjectProperties", "Text", "string|nil", "Initial text for a text render object. Provide Text or Resource, not both."));
        docs.register(MLuaDocBuilder.Registration.guideStep("RenderService", "Create and update a render instance",
            "Render constructors return a RenderObject instance. Set its properties while it is alive, then destroy it to release the client render resource. World-space positions accept native Vector3 or CFrame values; screen positions use Vector2.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal label = Render:CreateText(\"The door is locked\", { Position = Vector2.new(20, 20) })\nlocal marker = Render:CreateWorldText(\"Exit\", CFrame.new(12, 65, -4))\n\nlabel.Position = Vector2.new(24, 32)\nlabel.Visible = true\nmarker.WorldPosition = CFrame.new(14, 65, -4)\nprint(label:IsA(\"RenderObject\"), marker.Type)\nlabel:Destroy()\nmarker:Destroy()"));
    }
}
