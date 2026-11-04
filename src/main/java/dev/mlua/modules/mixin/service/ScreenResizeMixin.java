package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reapplies native widget layouts when Minecraft rebuilds a screen after resize. */
@Mixin(Screen.class)
public abstract class ScreenResizeMixin {
    @Inject(method = "resize(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"), require = 0)
    private void mlua$applyNativeLayouts(Minecraft minecraft, int width, int height, CallbackInfo callback) {
        MLuaGuiService.applyNativeLayouts(this);
    }
}
