package dev.mlua.modules.mixin.service;

import dev.mlua.modules.coregui.service.MLuaGuiChatLineBridge;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets client scripts edit sourced chat lines before the native chat HUD stores them. */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent", remap = false)
public abstract class ChatLineMixin {
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At(value = "HEAD", remap = false),
        argsOnly = true,
        require = 1,
        remap = false
    )
    @Group(name = "mlua$chatLineAdded", min = 1)
    private Component mlua$beforeSourcedChatLineAdded(Component message) {
        return MLuaGuiChatLineBridge.beforeChatLineAdded(message);
    }
}
