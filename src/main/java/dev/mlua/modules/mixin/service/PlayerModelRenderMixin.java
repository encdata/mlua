package dev.mlua.modules.mixin.service;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaPlayerModelState;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AgeableListModel.class)
public abstract class PlayerModelRenderMixin {
    //--version 1.20.1
    @Inject(method = "renderToBuffer", at = @At("HEAD"), cancellable = true)
    private void mlua$hideReplacedPlayer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, float red, float green, float blue, float alpha, CallbackInfo callback) {
        mlua$hideReplacedPlayer(callback);
    }
    //--endversion

    //--version 1.21.1
    @Inject(method = "renderToBuffer", at = @At("HEAD"), cancellable = true)
    private void mlua$hideReplacedPlayer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color, CallbackInfo callback) {
        mlua$hideReplacedPlayer(callback);
    }
    //--endversion

    private void mlua$hideReplacedPlayer(CallbackInfo callback) {
        if (!((Object) this instanceof PlayerModel<?>)) return;
        MLuaPlayerModelState state = (MLuaPlayerModelState) this;
        if (MLuaClientRenderManager.hasEntityModel(state.mlua$getEntityScope())) {
            callback.cancel();
        }
    }
}
