package dev.mlua.modules.camera;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.modules.camera.Service.MLuaCameraService;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaModule;
import dev.mlua.common.enums.Side;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class CameraModule implements MLuaModule {
    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Camera", "MLuaCameraService",
            "Client-side camera control for transforms, subjects, field of view, and held-item offsets.",
            "services/camera.html"));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetCFrame(cframe: CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetPosition(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetFOV(fov: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetFOVMultiplier(multiplier: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:MultiplyFOV(multiplier: number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetMode(mode: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetViewMode(mode: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetSubject(entity: Entity|nil)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:LookAt(target: Vector3|CFrame|Entity)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetRightHandOffset(cframe: CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetLeftHandOffset(cframe: CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Camera", "Move the client camera with native transforms",
            "Camera control is client-side. SetCFrame takes a native CFrame; SetPosition accepts Vector3 or CFrame and preserves the current look direction. LookAt accepts a position or an entity.",
            "local Camera = Minecraft:GetService(\"Camera\")\nlocal player = Minecraft:GetService(\"Players\").LocalPlayer\n\nCamera:SetCFrame(CFrame.lookAt(\n    player.Position + Vector3.new(0, 3, 6),\n    player.Position\n))\nCamera:SetFOV(80)"));
    }

    @Override
    public String id() { return "camera"; }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override
    public List<ServiceEntry> services() {
        if (!LoaderApi.platform().isClient()) return List.of();
        ServiceEntry.Builder camera = ServiceEntry.builder("Camera", MLuaCameraService::new)
            .reflective(MLuaCameraService.class);
        return List.of(camera.build());
    }
}
