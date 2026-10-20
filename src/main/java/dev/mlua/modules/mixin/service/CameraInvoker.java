package dev.mlua.modules.mixin.service;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Camera.class})
public interface CameraInvoker {
   @Invoker("setPosition")
   void mlua$invokeSetPosition(double x, double y, double z);

   @Invoker("setRotation")
   void mlua$invokeSetRotation(float yRot, float xRot);
}
