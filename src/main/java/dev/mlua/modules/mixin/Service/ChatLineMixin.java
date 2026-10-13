package dev.mlua.modules.mixin.Service;

import dev.mlua.modules.coregui.Service.MLuaGuiService;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets client scripts edit each chat component before the native chat HUD stores it. */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent")
public abstract class ChatLineMixin {
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;I"
            + "Lnet/minecraft/client/GuiMessageTag;Z)V",
        at = @At("HEAD"),
        argsOnly = true,
        require = 0
    )
    @Group(name = "mlua$chatLineAdded", min = 1, max = 3)
    private Component mlua$beforeLegacyChatLineAdded(Component message) {
        return MLuaGuiService.beforeChatLineAdded(message);
    }

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"),
        argsOnly = true,
        require = 0
    )
    @Group(name = "mlua$chatLineAdded")
    private Component mlua$beforeTaggedChatLineAdded(Component message) {
        return MLuaGuiService.beforeChatLineAdded(message);
    }

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"),
        argsOnly = true,
        require = 0
    )
    @Group(name = "mlua$chatLineAdded")
    private Component mlua$beforeSourcedChatLineAdded(Component message) {
        return MLuaGuiService.beforeChatLineAdded(message);
    }
}
