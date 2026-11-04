package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import dev.mlua.runtime.client.MLuaGuiRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies addon layout rules after a native screen has populated its widgets. */
@Mixin(value = Screen.class, remap = false)
public abstract class ScreenLayoutMixin {
    @Inject(method = "removed", at = @At(value = "TAIL", remap = false), require = 0, remap = false)
    private void mlua$releaseNativeScreenState(CallbackInfo callback) {
        MLuaGuiService.nativeScreenRemoved(this);
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "TAIL", remap = false), require = 0, remap = false)
    private void mlua$renderAddonUiBefore(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo callback) {
        MLuaGuiRenderer.renderScreenLayer((Screen) (Object) this, graphics, true);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "TAIL", remap = false), require = 0, remap = false)
    private void mlua$renderAddonUiAfter(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo callback) {
        MLuaGuiRenderer.renderScreenLayer((Screen) (Object) this, graphics, false);
    }
}
