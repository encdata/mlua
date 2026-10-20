package dev.mlua.modules.mixin.service;

import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.math.MLuaCFrame;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({Camera.class})
public abstract class CameraMixin {
   @Redirect(
      method = {"setup"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"
      )
   )
   private void modifyPositionSetting(Camera instance, double x, double y, double z) {
      float partialTick = MLuaCameraController.currentRenderPartialTick();
      MLuaCFrame override = MLuaCameraController.getScriptableCFrame(partialTick);
      if (override != null) {
         ((CameraInvoker)instance).mlua$invokeSetPosition(override.x(), override.y(), override.z());
      } else {
         ((CameraInvoker)instance).mlua$invokeSetPosition(x, y, z);
      }
   }

   @Redirect(
      method = {"setup"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;setRotation(FF)V"
      )
   )
   private void modifyRotationSetting(Camera instance, float yRot, float xRot) {
      MLuaCFrame override = MLuaCameraController.getScriptableCFrame(
         MLuaCameraController.currentRenderPartialTick());
      if (override != null) {
         ((CameraInvoker)instance).mlua$invokeSetRotation(override.minecraftYaw(), override.minecraftPitch());
         return;
      }
      ((CameraInvoker)instance).mlua$invokeSetRotation(
         MLuaCameraController.getScriptableYaw(yRot),
         MLuaCameraController.getScriptablePitch(xRot));
   }
}
