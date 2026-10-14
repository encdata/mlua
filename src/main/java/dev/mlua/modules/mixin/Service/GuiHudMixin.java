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

    @Inject(method = "extractCrosshair", at = @At("HEAD"))
    private void mlua$beforeCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeCrosshair", graphics);
    }

    @Inject(method = "extractCrosshair", at = @At("TAIL"))
    private void mlua$afterCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterCrosshair", graphics);
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"))
    private void mlua$beforeHotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHotbar", graphics);
    }

    @Inject(method = "extractItemHotbar", at = @At("TAIL"))
    private void mlua$afterHotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHotbar", graphics);
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"))
    private void mlua$beforeHealth(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHealth", graphics);
    }

    @Inject(method = "extractPlayerHealth", at = @At("TAIL"))
    private void mlua$afterHealth(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHealth", graphics);
    }

    @Inject(method = "extractExperienceBar", at = @At("HEAD"))
    private void mlua$beforeExperience(GuiGraphicsExtractor graphics, int x, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeExperience", graphics);
    }

    @Inject(method = "extractExperienceBar", at = @At("TAIL"))
    private void mlua$afterExperience(GuiGraphicsExtractor graphics, int x, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterExperience", graphics);
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

    @Inject(method = "renderCrosshair", at = @At("HEAD"))
    private void mlua$beforeCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeCrosshair", graphics);
    }

    @Inject(method = "renderCrosshair", at = @At("TAIL"))
    private void mlua$afterCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterCrosshair", graphics);
    }

    @Inject(method = "renderItemHotbar", at = @At("HEAD"))
    private void mlua$beforeHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHotbar", graphics);
    }

    @Inject(method = "renderItemHotbar", at = @At("TAIL"))
    private void mlua$afterHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHotbar", graphics);
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"))
    private void mlua$beforeHealth(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHealth", graphics);
    }

    @Inject(method = "renderPlayerHealth", at = @At("TAIL"))
    private void mlua$afterHealth(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHealth", graphics);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"))
    private void mlua$beforeExperience(GuiGraphics graphics, int x, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeExperience", graphics);
    }

    @Inject(method = "renderExperienceBar", at = @At("TAIL"))
    private void mlua$afterExperience(GuiGraphics graphics, int x, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterExperience", graphics);
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

    @Inject(method = "renderCrosshair", at = @At("HEAD"))
    private void mlua$beforeCrosshair(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeCrosshair", graphics);
    }

    @Inject(method = "renderCrosshair", at = @At("TAIL"))
    private void mlua$afterCrosshair(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterCrosshair", graphics);
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void mlua$beforeHotbar(float partialTick, GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHotbar", graphics);
    }

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void mlua$afterHotbar(float partialTick, GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHotbar", graphics);
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"))
    private void mlua$beforeHealth(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHealth", graphics);
    }

    @Inject(method = "renderPlayerHealth", at = @At("TAIL"))
    private void mlua$afterHealth(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHealth", graphics);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"))
    private void mlua$beforeExperience(GuiGraphics graphics, int x, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeExperience", graphics);
    }

    @Inject(method = "renderExperienceBar", at = @At("TAIL"))
    private void mlua$afterExperience(GuiGraphics graphics, int x, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterExperience", graphics);
    }
    //--endversion
}
