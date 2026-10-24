package dev.mlua.mixin;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies and releases MLua native widget layouts on NeoForge screens. */
@Mixin(value = Screen.class, remap = false)
public abstract class ScreenLayoutMixin {
    @Inject(method = "init()V", at = @At("TAIL"), remap = false)
    private void mlua$applyNativeLayouts(CallbackInfo callback) {
        MLuaGuiService.applyNativeLayouts(this);
    }

    @Inject(method = "removed", at = @At("TAIL"), remap = false, require = 0)
    private void mlua$releaseNativeScreenState(CallbackInfo callback) {
        MLuaGuiService.nativeScreenRemoved(this);
    }
}
