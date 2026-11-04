package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.contextualbar.ExperienceBarRenderer", remap = false)
public abstract class ExperienceBarRendererMixin {
    @Inject(method = "extractRenderState", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeExperience(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeExperience", graphics);
    }

    @Inject(method = "extractRenderState", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterExperience(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterExperience", graphics);
    }
}
