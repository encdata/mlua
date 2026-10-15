package dev.mlua.modules.mixin.Service;

import dev.mlua.runtime.client.MLuaHumanoidPoseApplier;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.1 player model hook using AvatarRenderState. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    private int mlua$entityId = -1;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void mlua$applyPlayerBonePoses(AvatarRenderState state, CallbackInfo info) {
        mlua$entityId = MLuaRenderStateEntityResolver.findEntityId(state);
        if (mlua$entityId >= 0) {
            MLuaHumanoidPoseApplier.apply((HumanoidModel<?>) (Object) this, mlua$entityId);
            MLuaModelAdapterRegistry.apply(this, MLuaEntityScope.legacy(mlua$entityId));
        }
    }
}
