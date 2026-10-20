package dev.mlua.modules.mixin.service;

import dev.mlua.loader.impl.fabric.FabricEventBridge;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class FabricItemPickupMixin {
    @Unique private ItemStack mlua$stackBeforePickup = ItemStack.EMPTY;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void mlua$beforePickup(Player player, CallbackInfo callbackInfo) {
        mlua$stackBeforePickup = ((ItemEntity) (Object) this).getItem().copy();
    }

    @Inject(method = "playerTouch", at = @At("TAIL"))
    private void mlua$afterPickup(Player player, CallbackInfo callbackInfo) {
        ItemEntity item = (ItemEntity) (Object) this;
        ItemStack remaining = item.getItem();
        int pickedCount = mlua$stackBeforePickup.getCount() - (remaining.isEmpty() ? 0 : remaining.getCount());
        if (!player.level().isClientSide() && pickedCount > 0) {
            ItemStack picked = mlua$stackBeforePickup.copy();
            picked.setCount(pickedCount);
            FabricEventBridge.fireItemPickup(player, picked);
        }
        mlua$stackBeforePickup = ItemStack.EMPTY;
    }
}
