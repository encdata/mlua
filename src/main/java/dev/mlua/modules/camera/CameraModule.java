package dev.mlua.modules.camera;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.modules.camera.Service.MLuaCameraService;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaModule;
import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.EventBridge;
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
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetRightHandOffset(offset: CFrame|function|nil)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetLeftHandOffset(offset: CFrame|function|nil)", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Camera", "Move the client camera with native transforms",
            "Camera control is client-side. SetCFrame takes a native CFrame; SetPosition accepts Vector3 or CFrame and preserves the current look direction. LookAt accepts a position or an entity.",
            "local Camera = Minecraft:GetService(\"Camera\")\nlocal player = Minecraft:GetService(\"Players\").LocalPlayer\n\nCamera:SetCFrame(CFrame.lookAt(\n    player.Position + Vector3.new(0, 3, 6),\n    player.Position\n))\nCamera:SetFOV(80)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Camera", "Animate a first-person weapon pose",
            "SetRightHandOffset and SetLeftHandOffset accept a CFrame or a callback returning a CFrame. A callback is sampled at the end of each client tick and its last valid pose remains active if it fails. Pass nil to clear one hand's offset.",
            "local Camera = Minecraft:GetService(\"Camera\")\nlocal phase = 0\n\nCamera:SetRightHandOffset(function()\n    phase = (phase + 0.08) % (math.pi * 2)\n    local sway = math.sin(phase) * 0.01\n    local recoil = math.max(0, math.sin(phase)) ^ 8\n    return CFrame.new(0, sway, -recoil * 0.12)\n        * CFrame.Angles(-recoil * 0.18, 0, 0)\nend)\n\n-- Clear only this hand's offset with Camera:SetRightHandOffset(nil)."));
    }

    @Override
    public String id() { return "camera"; }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        if (side == Side.CLIENT || side == Side.BOTH) {
            dev.mlua.minecraft.client.MLuaCameraController.clearHandOffsets();
        }
    }

    @Override
    public void registerEvents(EventBridge events) {
        events.onClientTickEnd(dev.mlua.minecraft.client.MLuaCameraController::tickHandOffsets);
        events.onClientDisconnect(dev.mlua.minecraft.client.MLuaCameraController::clearHandOffsets);
    }

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
