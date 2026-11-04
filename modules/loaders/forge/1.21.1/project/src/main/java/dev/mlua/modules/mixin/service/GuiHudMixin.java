package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class, remap = false)
public abstract class GuiHudMixin {
    @Inject(method = "render", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHud", graphics);
    }

    @Inject(method = "render", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHud", graphics);
    }

    @Inject(method = "renderCrosshair", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeCrosshair", graphics);
    }

    @Inject(method = "renderCrosshair", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterCrosshair", graphics);
    }

    @Inject(method = "renderItemHotbar", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHotbar", graphics);
    }

    @Inject(method = "renderItemHotbar", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHotbar", graphics);
    }

    @Inject(method = "renderPlayerHealth", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeHealth(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeHealth", graphics);
    }

    @Inject(method = "renderPlayerHealth", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterHealth(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterHealth", graphics);
    }

    @Inject(method = "renderExperienceBar", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeExperience(GuiGraphics graphics, int x, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeExperience", graphics);
    }

    @Inject(method = "renderExperienceBar", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterExperience(GuiGraphics graphics, int x, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterExperience", graphics);
    }

    @Inject(method = "renderEffects", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeEffects(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeEffects", graphics);
    }

    @Inject(method = "renderEffects", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterEffects(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterEffects", graphics);
    }

    @Inject(method = "renderOverlayMessage", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeOverlayMessage(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeOverlayMessage", graphics);
    }

    @Inject(method = "renderOverlayMessage", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterOverlayMessage(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterOverlayMessage", graphics);
    }

    @Inject(method = "renderTitle", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeTitle(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeTitle", graphics);
    }

    @Inject(method = "renderTitle", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterTitle(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterTitle", graphics);
    }

    @Inject(method = "renderScoreboardSidebar", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeScoreboard(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeScoreboard", graphics);
    }

    @Inject(method = "renderScoreboardSidebar", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterScoreboard(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterScoreboard", graphics);
    }

    @Inject(method = "renderTabList", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforePlayerList(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforePlayerList", graphics);
    }

    @Inject(method = "renderTabList", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterPlayerList(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterPlayerList", graphics);
    }

    @Inject(method = "renderFood", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeFood(GuiGraphics graphics, Player player, int top, int right, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeFood", graphics);
    }

    @Inject(method = "renderFood", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterFood(GuiGraphics graphics, Player player, int top, int right, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterFood", graphics);
    }
}
