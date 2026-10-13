package dev.mlua.modules.mixin.Service;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Camera.class})
public interface CameraInvoker {
   @Invoker("setPosition")
   void mlua$invokeSetPosition(double x, double y, double z);
}
