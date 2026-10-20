package dev.mlua.modules.mixin.service;

import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.1 camera hook: vanilla now updates the camera through update(), not setup(). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void mlua$applyScriptableCamera(DeltaTracker deltaTracker, CallbackInfo info) {
        Float renderPartialTick = MLuaRenderCallbacks.readPartialTick(deltaTracker);
        float partialTick = renderPartialTick == null
            ? MLuaCameraController.currentRenderPartialTick() : renderPartialTick;
        MLuaCFrame frame = MLuaCameraController.getScriptableCFrame(partialTick);
        if (frame == null) return;

        CameraInvoker camera = (CameraInvoker) (Object) this;
        camera.mlua$invokeSetPosition(frame.x(), frame.y(), frame.z());
        camera.mlua$invokeSetRotation(frame.minecraftYaw(), frame.minecraftPitch());
    }
}
