package dev.mlua.modules.mixin.service;

import dev.mlua.loader.impl.fabric.FabricEventBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class FabricItemCraftMixin {
    @Inject(method = "onTake", at = @At("TAIL"))
    private void mlua$crafted(Player player, ItemStack stack, CallbackInfo callbackInfo) {
        if (!player.level().isClientSide()) {
            FabricEventBridge.fireItemCraft(player, stack);
        }
    }
}
