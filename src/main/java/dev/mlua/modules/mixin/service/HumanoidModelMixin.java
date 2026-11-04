package dev.mlua.modules.mixin.service;

import dev.mlua.runtime.client.MLuaHumanoidPoseApplier;
import dev.mlua.runtime.client.MLuaClientModelManager;
import net.minecraft.client.model.HumanoidModel;
//--version 1.20.1,1.21.1
import net.minecraft.client.model.PlayerModel;
//--endversion
//--version 26.1
import net.minecraft.client.model.player.PlayerModel;
//--endversion
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Inject(method = "setupAnim", at = @At("HEAD"))
    private void mlua$restoreModelOverrides(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        MLuaClientModelManager.restoreOverrides(this);
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void mlua$applyBonePoses(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity == null) return;
        if ((Object) this instanceof PlayerModel) return;
        MLuaHumanoidPoseApplier.apply((HumanoidModel<?>) (Object) this, entity);
    }
}
