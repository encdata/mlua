package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies native widget layouts after Minecraft has initialized a screen. */
@Mixin(Minecraft.class)
public abstract class ScreenInitializationMixin {
    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("TAIL"))
    private void mlua$applyNativeLayouts(Screen screen, CallbackInfo callback) {
        if (screen != null) MLuaGuiService.applyNativeLayouts(screen);
    }
}
