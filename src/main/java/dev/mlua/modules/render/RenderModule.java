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
        ServiceEntry.Builder render = ServiceEntry.builder("RenderService", MLuaRenderService::new)
            .description("Client-side HUD and projected world-space renderables.");
        render.function("CreateRect", "Creates a screen-space rectangle render object.")
            .function("CreateText", "Creates screen-space text from a string.")
            .function("CreateImage", "Creates a screen-space image from a resource identifier.")
            .function("CreateItem", "Creates a screen-space item render object.")
            .function("CreateBlock", "Creates a screen-space block render object.")
            .function("CreateMesh", "Creates a mesh render object from vertices and indices.")
            .function("LoadBBModel", "Loads a Blockbench model as a render object.")
            .function("CreateWorldRect", "Creates a rectangle at a world position.")
            .function("CreateWorldText", "Creates text at a world position.")
            .function("CreateWorldImage", "Creates an image at a world position.")
            .function("CreateWorldItem", "Creates an item render object at a world position.")
            .function("CreateWorldBlock", "Creates a block render object at a world position.")
            .function("CreateWorldBillboard", "Creates a camera-facing image at a world position.")
            .function("CreateWorldLine", "Creates a line between two world positions.")
            .function("CreateWorldBeam", "Creates a beam between two world positions.")
            .function("CreateWorldBox", "Creates a box render object at a world position.")
            .function("Clear", "Removes render objects owned by the calling mod.")
            .function("GetAll", "Returns live render object handles owned by the calling mod.");
        return List.of(render.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("RenderService", "MLuaRenderService",
            "Client-side HUD and projected world-space renderables.",
            "services/render.html");
        docs.clazz("RenderObject", "MLuaRenderService",
            "A live client render instance with mutable screen-space or world-space properties and rendering methods.",
            "classes/render-object.html", "Instance");
        docs.signature("RenderService", "RenderService:CreateRect(properties: RenderObjectProperties?)", "RenderObject");
        docs.signature("RenderService", "RenderService:CreateText(text: string, properties: RenderObjectProperties?)", "RenderObject");
        docs.signature("RenderService", "RenderService:CreateWorldText(text: string, position: Vector3, properties: RenderObjectProperties?)", "RenderObject");
        docs.signature("RenderService", "RenderService:CreateWorldLine(start: Vector3, finish: Vector3, properties: RenderObjectProperties?)", "RenderObject");
        docs.type("RenderObjectProperties", null, "Initial properties accepted when a render object is created.");
        docs.field("RenderObjectProperties", "Position", "Vector2", "Initial screen position.");
        docs.field("RenderObjectProperties", "Size", "Vector2", "Initial screen width and height.");
        docs.field("RenderObjectProperties", "Rotation", "number", "Initial screen rotation in degrees.");
        docs.field("RenderObjectProperties", "Visible", "boolean", "Whether the object starts visible.");
        docs.field("RenderObjectProperties", "Transparency", "number", "Initial transparency from zero to one.");
        docs.field("RenderObjectProperties", "Color", "number", "Initial packed color value.");
        docs.field("RenderObjectProperties", "WorldPosition", "Vector3", "Initial world position.");
        docs.field("RenderObjectProperties", "EndWorldPosition", "Vector3", "Initial endpoint for a world line or beam.");
        docs.field("RenderObjectProperties", "ScaleWithDistance", "boolean", "Whether world-space size follows camera distance.");
        docs.guideStep("RenderService", "Create and update a render instance",
            "Render constructors return a RenderObject instance. Set its properties while it is alive, then destroy it to release the client render resource.",
            "local Render = Minecraft:GetService(\"RenderService\")\nlocal label = Render:CreateText(\"The door is locked\", { Position = Vector2.new(20, 20) })\n\nlabel.Position = Vector2.new(24, 32)\nlabel.Visible = true\nprint(label:IsA(\"RenderObject\"), label.Type)\nlabel:Destroy()");
    }
}
