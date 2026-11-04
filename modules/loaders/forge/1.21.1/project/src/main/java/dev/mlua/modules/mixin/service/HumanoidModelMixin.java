package dev.mlua.modules.mixin.service;

import dev.mlua.runtime.client.MLuaClientModelManager;
import dev.mlua.runtime.client.MLuaHumanoidPoseApplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HumanoidModel.class, remap = false)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim", at = @At(value = "HEAD", remap = false), remap = false)
    private void mlua$restoreModelOverrides(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        MLuaClientModelManager.restoreOverrides(this);
    }

    @Inject(method = "setupAnim", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$applyBonePoses(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (entity == null || (Object) this instanceof PlayerModel) return;
        MLuaHumanoidPoseApplier.apply((HumanoidModel<?>) (Object) this, entity);
    }
}
