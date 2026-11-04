package dev.mlua.modules.mixin.service;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Camera.class, remap = false)
public interface CameraInvoker {
    @Invoker(value = "setPosition", remap = false)
    void mlua$invokeSetPosition(double x, double y, double z);

    @Invoker(value = "setRotation", remap = false)
    void mlua$invokeSetRotation(float yRot, float xRot);
}
