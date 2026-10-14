package dev.mlua.modules.mixin.Service;

import dev.mlua.loader.impl.fabric.FabricEventBridge;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class FabricProjectileHitMixin {
    @Inject(method = "onHit", at = @At("HEAD"))
    private void mlua$onHit(HitResult hitResult, CallbackInfo callbackInfo) {
        FabricEventBridge.fireProjectileHit(this, hitResult);
    }
}
