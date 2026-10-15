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
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetRightHandOffset(offset: CFrame|function(player: Player?, hand: Enum.Hand, itemStack: Item, arm: string): CFrame)", "Sets this mod's physical right-arm pose layer. Mod layers compose in first-registration order, and each callback is sampled on the client tick then interpolated during first-person rendering."));
        docs.register(MLuaDocBuilder.Registration.signature("Camera", "Camera:SetLeftHandOffset(offset: CFrame|function(player: Player?, hand: Enum.Hand, itemStack: Item, arm: string): CFrame)", "Sets this mod's physical left-arm pose layer. Mod layers compose in first-registration order, and each callback is sampled on the client tick then interpolated during first-person rendering."));
        docs.register(MLuaDocBuilder.Registration.guideStep("Camera", "Move the client camera with native transforms",
            "Camera control is client-side. SetCFrame takes a native CFrame; SetPosition accepts Vector3 or CFrame and preserves the current look direction. LookAt accepts a position or an entity.",
            "local Camera = Minecraft:GetService(\"Camera\")\nlocal player = Minecraft:GetService(\"Players\").LocalPlayer\n\nCamera:SetCFrame(CFrame.lookAt(\n    player.Position + Vector3.new(0, 3, 6),\n    player.Position\n))\nCamera:SetFOV(80)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Camera", "Animate a first-person weapon pose",
            "Each hand accepts a CFrame or a callback returning one. The callback runs on the client tick with the local player, logical hand, live Item stack, and physical arm name. Each mod owns one layer per arm; MLua composes those CFrames in registration order and interpolates their sampled transforms between ticks. A failed callback keeps its last valid pose and is disabled. Pass nil to remove your mod's layer for that hand.",
            "local Camera = Minecraft:GetService(\"Camera\")\nlocal phase = 0\n\nCamera:SetRightHandOffset(function(player, hand, itemStack, arm)\n    phase = (phase + 0.08) % (math.pi * 2)\n    local itemId = itemStack:GetId()\n    local recoil = itemId == \"minecraft:bow\" and 0.02 or 0.12\n    local sway = math.sin(phase) * 0.01\n    return CFrame.new(0, sway, -recoil)\n        * CFrame.Angles(-math.max(0, math.sin(phase)) * recoil, 0, 0)\nend)\n\n-- Clear only this hand's pose with Camera:SetRightHandOffset(nil)."));
    }

    @Override
    public String id() { return "camera"; }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        if (side == Side.CLIENT || side == Side.BOTH) {
            dev.mlua.minecraft.client.MLuaCameraController.clearHandOffsetsForRuntime(runtimeSession);
        }
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        if (side == Side.CLIENT || side == Side.BOTH) {
            dev.mlua.minecraft.client.MLuaCameraController.clearHandOffsets(runtimeSession, ownerModId);
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
