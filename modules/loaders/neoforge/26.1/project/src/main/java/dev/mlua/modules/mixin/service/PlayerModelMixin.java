package dev.mlua.modules.mixin.service;

import dev.mlua.runtime.client.MLuaHumanoidPoseApplier;
import dev.mlua.runtime.client.MLuaRenderStateEntityResolver;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies MLua player bone poses through the 26.1 render-state API. */
@Mixin(value = PlayerModel.class, remap = false)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim", at = @At(value = "TAIL", remap = false), remap = false)
    private void mlua$applyPlayerBonePoses(AvatarRenderState state, CallbackInfo info) {
        int entityId = MLuaRenderStateEntityResolver.findEntityId(state);
        if (entityId >= 0) {
            MLuaHumanoidPoseApplier.apply((HumanoidModel<?>) (Object) this, entityId);
        }
    }
}
