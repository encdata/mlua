package dev.mlua.modules.mixin.Service;

import dev.mlua.minecraft.client.MLuaCameraController;
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
        Vec3 position = MLuaCameraController.getScriptablePosition();
        if (position == null) return;

        CameraInvoker camera = (CameraInvoker) (Object) this;
        camera.mlua$invokeSetPosition(position.x, position.y, position.z);
        camera.mlua$invokeSetRotation(
            MLuaCameraController.getScriptableYaw(((Camera) (Object) this).yRot()),
            MLuaCameraController.getScriptablePitch(((Camera) (Object) this).xRot())
        );
    }
}
