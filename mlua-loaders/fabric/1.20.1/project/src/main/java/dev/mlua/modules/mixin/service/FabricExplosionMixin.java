package dev.mlua.modules.mixin.service;

import dev.mlua.loader.impl.fabric.FabricEventBridge;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class FabricExplosionMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private double x;
    @Shadow @Final private double y;
    @Shadow @Final private double z;

    @Inject(method = "explode", at = @At("HEAD"))
    private void mlua$explosionStarted(CallbackInfo callbackInfo) {
        if (!level.isClientSide()) {
            FabricEventBridge.fireExplosion(level, new Vec3(x, y, z));
        }
    }
}
