package dev.mlua.modules.mixin.Service;

import dev.mlua.modules.modal.Service.MLuaModalService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void onGrabMouse(CallbackInfo ci) {
        if (MLuaModalService.isGlobalModal() && Minecraft.getInstance().screen == null) {
            ci.cancel();
        }
    }

    @Inject(method = "isMouseGrabbed", at = @At("HEAD"), cancellable = true)
    private void onIsMouseGrabbed(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
      if (MLuaModalService.isGlobalModal() && Minecraft.getInstance().screen == null) {
         cir.setReturnValue(false);
      }
   }
}
