package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class, remap = false)
public abstract class GuiHudMixin {
    @Inject(method = "extractRenderState", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHud", graphics);
    }

    @Inject(method = "extractRenderState", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHud", graphics);
    }

    @Inject(method = "extractCrosshair", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeCrosshair", graphics);
    }

    @Inject(method = "extractCrosshair", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterCrosshair", graphics);
    }

    @Inject(method = "extractItemHotbar", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeHotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHotbar", graphics);
    }

    @Inject(method = "extractItemHotbar", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterHotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHotbar", graphics);
    }

    @Inject(method = "extractPlayerHealth", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeHealth(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHealth", graphics);
    }

    @Inject(method = "extractPlayerHealth", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterHealth(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHealth", graphics);
    }

    @Inject(method = "extractEffects", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeEffects", graphics);
    }

    @Inject(method = "extractEffects", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterEffects", graphics);
    }

    @Inject(method = "extractOverlayMessage", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeOverlayMessage(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeOverlayMessage", graphics);
    }

    @Inject(method = "extractOverlayMessage", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterOverlayMessage(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterOverlayMessage", graphics);
    }

    @Inject(method = "extractTitle", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeTitle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeTitle", graphics);
    }

    @Inject(method = "extractTitle", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterTitle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterTitle", graphics);
    }

    @Inject(method = "extractScoreboardSidebar", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeScoreboard(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeScoreboard", graphics);
    }

    @Inject(method = "extractScoreboardSidebar", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterScoreboard(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterScoreboard", graphics);
    }

    @Inject(method = "extractTabList", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforePlayerList(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforePlayerList", graphics);
    }

    @Inject(method = "extractTabList", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterPlayerList(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
            CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterPlayerList", graphics);
    }
}
