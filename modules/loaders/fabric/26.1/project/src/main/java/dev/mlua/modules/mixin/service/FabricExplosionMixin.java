package dev.mlua.modules.mixin.service;

import dev.mlua.loader.impl.fabric.FabricEventBridge;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerExplosion.class)
public abstract class FabricExplosionMixin {
    @Inject(method = "explode", at = @At("HEAD"))
    private void mlua$explosionStarted(CallbackInfoReturnable<Integer> callbackInfo) {
        ServerExplosion explosion = (ServerExplosion) (Object) this;
        FabricEventBridge.fireExplosion(explosion.level(), explosion.center());
    }
}
