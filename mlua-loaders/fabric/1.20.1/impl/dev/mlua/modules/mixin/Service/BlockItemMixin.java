package dev.mlua.modules.mixin.Service;

import dev.mlua.loader.impl.fabric.MLuaFabricMod;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.BlockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void mlua$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = cir.getReturnValue();
        var provider = MLuaFabricMod.provider();
        if (result != null && result.consumesAction() && provider != null) {
            provider.fabricEvents().fireBlockPlace(context.getPlayer(), context.getLevel(), context.getClickedPos());
        }
    }
}
