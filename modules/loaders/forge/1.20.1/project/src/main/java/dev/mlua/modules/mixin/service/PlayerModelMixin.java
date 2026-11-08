package dev.mlua.modules.mixin.service;

import dev.mlua.runtime.client.MLuaHumanoidPoseApplier;
import dev.mlua.runtime.client.MLuaPlayerModelState;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin implements MLuaPlayerModelState {
    private MLuaEntityScope mlua$entityScope;

    @Override
    public MLuaEntityScope mlua$getEntityScope() {
        return mlua$entityScope;
    }

    @Override
    public void mlua$setEntityScope(MLuaEntityScope scope) {
        mlua$entityScope = scope;
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void mlua$applyPlayerBonePoses(LivingEntity player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (player == null) return;
        mlua$setEntityScope(MLuaEntityScope.of(player));
        MLuaHumanoidPoseApplier.apply((HumanoidModel<?>) (Object) this, player.getId());
    }
}
