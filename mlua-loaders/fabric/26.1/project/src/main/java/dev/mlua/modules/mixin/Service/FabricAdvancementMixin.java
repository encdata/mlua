package dev.mlua.modules.mixin.Service;

import dev.mlua.loader.impl.fabric.FabricEventBridge;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class FabricAdvancementMixin {
    @Shadow private ServerPlayer player;
    @Shadow public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

    @Inject(method = "award", at = @At("RETURN"))
    private void mlua$advancementAwarded(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (callbackInfo.getReturnValue() && getOrStartProgress(advancement).isDone()) {
            FabricEventBridge.fireAdvancement(player, advancement.id().toString());
        }
    }
}
