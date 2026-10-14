package dev.mlua.modules.mixin.Service;

import dev.mlua.loader.impl.fabric.FabricEventBridge;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class FabricItemDropMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))
    private void mlua$onDrop(ItemStack stack, boolean dropAround, CallbackInfoReturnable<ItemEntity> callbackInfo) {
        ItemEntity dropped = callbackInfo.getReturnValue();
        Player player = (Player) (Object) this;
        if (dropped != null && !player.level().isClientSide()) {
            FabricEventBridge.fireItemDrop(player, dropped.getItem());
        }
    }
}
