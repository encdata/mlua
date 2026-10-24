package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import dev.mlua.runtime.client.MLuaGuiRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies addon layout rules after a native screen has populated its widgets. */
@Mixin(Screen.class)
public abstract class ScreenLayoutMixin {
    @Inject(method = "init()V", at = @At("TAIL"))
    private void mlua$applyNativeLayouts(CallbackInfo callback) {
        MLuaGuiService.applyNativeLayouts(this);
    }

    @Inject(method = "removed", at = @At("TAIL"), require = 0)
    private void mlua$releaseNativeScreenState(CallbackInfo callback) {
        MLuaGuiService.nativeScreenRemoved(this);
    }

    //--version 1.20.1
    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("TAIL"), require = 0)
    private void mlua$renderAddonUiBeforeLegacy(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiRenderer.renderScreenLayer((Screen) (Object) this, graphics, true);
    }
    //--endversion

    //--version 1.21.1,26.1
    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"), require = 0)
    private void mlua$renderAddonUiBefore(GuiGraphics graphics, int mouseX, int mouseY,
                                          float partialTick, CallbackInfo callback) {
        MLuaGuiRenderer.renderScreenLayer((Screen) (Object) this, graphics, true);
    }
    //--endversion

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"), require = 0)
    private void mlua$renderAddonUiAfter(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
        MLuaGuiRenderer.renderScreenLayer((Screen) (Object) this, graphics, false);
    }
}
