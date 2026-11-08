package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiService;
//--version 1.20.1,1.21.1
import net.minecraft.client.Minecraft;
//--endversion
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reapplies native widget layouts when Minecraft rebuilds a screen after resize. */
@Mixin(Screen.class)
public abstract class ScreenResizeMixin {
    //--version 1.20.1,1.21.1
    @Inject(method = "resize(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"), require = 0)
    private void mlua$applyNativeLayouts(Minecraft minecraft, int width, int height, CallbackInfo callback) {
        MLuaGuiService.applyNativeLayouts(this);
    }
    //--endversion

    //--version 26.1
    @Inject(method = "resize(II)V", at = @At("TAIL"), require = 0)
    private void mlua$applyNativeLayouts(int width, int height, CallbackInfo callback) {
        MLuaGuiService.applyNativeLayouts(this);
    }
    //--endversion
}
