package dev.mlua.modules.mixin.service;

import dev.mlua.modules.modal.service.MLuaModalService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MouseHandler.class, remap = false)
public abstract class MouseHandlerMixin {
    @Inject(method = "grabMouse", at = @At(value = "HEAD", remap = false), cancellable = true, remap = false)
    private void onGrabMouse(CallbackInfo callback) {
        if (MLuaModalService.isGlobalModal() && Minecraft.getInstance().screen == null) {
            callback.cancel();
        }
    }

    @Inject(method = "isMouseGrabbed", at = @At(value = "HEAD", remap = false), cancellable = true, remap = false)
    private void onIsMouseGrabbed(CallbackInfoReturnable<Boolean> callback) {
        if (MLuaModalService.isGlobalModal() && Minecraft.getInstance().screen == null) {
            callback.setReturnValue(false);
        }
    }
}
