package dev.mlua.modules.mixin.service;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaHumanoidPoseApplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerModel.class, remap = false)
public abstract class PlayerModelMixin {
    private LivingEntity mlua$entity;

    @Inject(method = "renderToBuffer", at = @At(value = "HEAD", remap = false),
            cancellable = true, require = 0, remap = false)
    private void mlua$hideReplacedPlayer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color, int red, int green, int blue, int alpha, CallbackInfo callback) {
        if (mlua$entity != null && MLuaClientRenderManager.hasEntityModel(mlua$entity)) {
            callback.cancel();
        }
    }

    @Inject(method = "setupAnim", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$applyPlayerBonePoses(LivingEntity player, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (player == null) return;
        mlua$entity = player;
        MLuaHumanoidPoseApplier.apply((HumanoidModel<?>) (Object) this, player);
    }
}
