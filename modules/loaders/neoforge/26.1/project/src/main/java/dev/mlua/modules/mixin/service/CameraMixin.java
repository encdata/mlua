package dev.mlua.modules.mixin.service;

import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Camera pose hooks for the 26.1 update/extract render-state pipeline. */
@Mixin(value = Camera.class, remap = false)
public abstract class CameraMixin {
   @Inject(method = "update", at = @At(value = "TAIL", remap = false), remap = false)
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
