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

@Mixin(value = AgeableListModel.class, remap = false)
public abstract class PlayerModelRenderMixin {
    @Inject(method = "renderToBuffer", at = @At(value = "HEAD", remap = false),
            cancellable = true, remap = false)
    private void mlua$hideReplacedPlayer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color, CallbackInfo callback) {
        if (!((Object) this instanceof PlayerModel<?>)) return;
        MLuaPlayerModelState state = (MLuaPlayerModelState) this;
        if (MLuaClientRenderManager.hasEntityModel(state.mlua$getEntityScope())) {
            callback.cancel();
        }
    }
}
