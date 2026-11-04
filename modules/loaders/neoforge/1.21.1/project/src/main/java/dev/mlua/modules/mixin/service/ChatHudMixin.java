package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatComponent.class, remap = false)
public abstract class ChatHudMixin {
    @Inject(method = "render", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$beforeChat(GuiGraphics graphics, int tickCount, int mouseX, int mouseY,
            boolean focused, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeChat", graphics);
    }

    @Inject(method = "render", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$afterChat(GuiGraphics graphics, int tickCount, int mouseX, int mouseY,
            boolean focused, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterChat", graphics);
    }
}
