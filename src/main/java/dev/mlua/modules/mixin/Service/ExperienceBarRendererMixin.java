package dev.mlua.modules.mixin.Service;

import dev.mlua.modules.coregui.Service.MLuaGuiService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//--version 26.1
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//--endversion

//--version 26.1
@Mixin(targets = "net.minecraft.client.gui.contextualbar.ExperienceBarRenderer")
//--endversion
public abstract class ExperienceBarRendererMixin {
    //--version 26.1
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void mlua$beforeExperience(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeExperience", graphics);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mlua$afterExperience(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterExperience", graphics);
    }
    //--endversion
}
