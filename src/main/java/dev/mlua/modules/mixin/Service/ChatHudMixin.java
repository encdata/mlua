package dev.mlua.modules.mixin.Service;

import dev.mlua.modules.coregui.Service.MLuaGuiService;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//--version 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//--endversion
//--version 1.20.1
import net.minecraft.client.gui.GuiGraphics;
//--endversion
//--version 1.21.1
import net.minecraft.client.gui.GuiGraphics;
//--endversion

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
    //--version 26.1
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void mlua$beforeChat(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeChat", graphics);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mlua$afterChat(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterChat", graphics);
    }
    //--endversion

    //--version 1.20.1
    @Inject(method = "render", at = @At("HEAD"))
    private void mlua$beforeChat(GuiGraphics graphics, int tickCount, int mouseX, int mouseY, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeChat", graphics);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mlua$afterChat(GuiGraphics graphics, int tickCount, int mouseX, int mouseY, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterChat", graphics);
    }
    //--endversion

    //--version 1.21.1
    @Inject(method = "render", at = @At("HEAD"))
    private void mlua$beforeChat(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("BeforeChat", graphics);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mlua$afterChat(GuiGraphics graphics, CallbackInfo callback) {
        MLuaGuiService.renderHudElements("AfterChat", graphics);
    }
    //--endversion
}
