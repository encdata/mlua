package dev.mlua.modules.mixin.Service;

import dev.mlua.minecraft.client.MLuaCameraController;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
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
      Vec3 overridePos = MLuaCameraController.getScriptablePosition();
      if (overridePos != null) {
         ((CameraInvoker)instance).mlua$invokeSetPosition(overridePos.x, overridePos.y, overridePos.z);
      } else {
         ((CameraInvoker)instance).mlua$invokeSetPosition(x, y, z);
      }
   }
}
