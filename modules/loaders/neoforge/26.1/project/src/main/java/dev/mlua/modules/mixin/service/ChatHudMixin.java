package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatComponent.class, remap = false)
public abstract class ChatHudMixin {
    @Inject(method = "extractRenderState", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeChat(GuiGraphicsExtractor graphics, Font font, int x, int y, int width,
            ChatComponent.DisplayMode displayMode, boolean renderOnly, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeChat", graphics);
    }

    @Inject(method = "extractRenderState", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterChat(GuiGraphicsExtractor graphics, Font font, int x, int y, int width,
            ChatComponent.DisplayMode displayMode, boolean renderOnly, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterChat", graphics);
    }
}
