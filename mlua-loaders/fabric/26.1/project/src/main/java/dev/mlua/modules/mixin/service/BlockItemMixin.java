package dev.mlua.modules.mixin.service;

import dev.mlua.loader.impl.fabric.FabricEventBridge;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void mlua$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> callbackInfo) {
        InteractionResult result = callbackInfo.getReturnValue();
        if (result != null && result.consumesAction() && !context.getLevel().isClientSide()) {
            FabricEventBridge.fireBlockPlace(context.getPlayer(), context.getLevel(), context.getClickedPos());
        }
    }
}
