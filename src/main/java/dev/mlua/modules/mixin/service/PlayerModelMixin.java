package dev.mlua.modules.mixin.service;

import dev.mlua.runtime.client.MLuaHumanoidPoseApplier;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    private LivingEntity mlua$entity;

    // PlayerModel inherits this renderer on some mappings (notably Fabric 1.20.1),
    // so the method is not always declared directly on the target class.
    @Inject(method = "renderToBuffer", at = @At("HEAD"), cancellable = true, require = 0)
    private void mlua$hideReplacedPlayer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color, int red, int green, int blue, int alpha, CallbackInfo ci) {
        if (mlua$entity != null && MLuaClientRenderManager.hasEntityModel(mlua$entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void mlua$applyPlayerBonePoses(LivingEntity player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (player == null) return;
        mlua$entity = player;
        MLuaHumanoidPoseApplier.apply((HumanoidModel<?>) (Object) this, player);
    }
}
