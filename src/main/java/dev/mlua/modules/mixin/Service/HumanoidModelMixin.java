package dev.mlua.modules.mixin.Service;

import dev.mlua.runtime.client.MLuaHumanoidPoseApplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void mlua$applyBonePoses(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity == null) return;
        if ((Object) this instanceof PlayerModel) return;
        MLuaHumanoidPoseApplier.apply((HumanoidModel<?>) (Object) this, entity);
    }
}
