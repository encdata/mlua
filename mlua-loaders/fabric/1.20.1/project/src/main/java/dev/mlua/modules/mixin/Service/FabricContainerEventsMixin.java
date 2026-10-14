package dev.mlua.modules.mixin.Service;

import dev.mlua.loader.impl.fabric.FabricEventBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class FabricContainerEventsMixin {
    @Inject(method = "openMenu", at = @At("TAIL"))
    private void mlua$containerOpened(net.minecraft.world.MenuProvider provider, CallbackInfoReturnable<java.util.OptionalInt> callbackInfo) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        AbstractContainerMenu menu = player.containerMenu;
        if (callbackInfo.getReturnValue().isPresent() && menu != player.inventoryMenu) {
            FabricEventBridge.fireContainerOpen(player, menu);
        }
    }

    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void mlua$containerClosed(CallbackInfo callbackInfo) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null && menu != player.inventoryMenu) {
            FabricEventBridge.fireContainerClose(player, menu);
        }
    }
}
