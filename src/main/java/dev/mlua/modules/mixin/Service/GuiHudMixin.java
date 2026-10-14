package dev.mlua.modules.mixin.Service;

import dev.mlua.modules.coregui.Service.MLuaGuiService;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//--version 26.1
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//--endversion
//--version 1.21.1
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
//--endversion
//--version 1.20.1
import net.minecraft.client.gui.GuiGraphics;
//--endversion

@Mixin(Gui.class)
public abstract class GuiHudMixin {
    //--version 26.1
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void mlua$beforeHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHud", graphics);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mlua$afterHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHud", graphics);
    }
    //--endversion

    //--version 1.21.1
    @Inject(method = "render", at = @At("HEAD"))
    private void mlua$beforeHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHud", graphics);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mlua$afterHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHud", graphics);
    }
    //--endversion

    //--version 1.20.1
    @Inject(method = "render", at = @At("HEAD"))
    private void mlua$beforeHud(GuiGraphics graphics, float partialTick, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHud", graphics);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mlua$afterHud(GuiGraphics graphics, float partialTick, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHud", graphics);
    }
    //--endversion
}
