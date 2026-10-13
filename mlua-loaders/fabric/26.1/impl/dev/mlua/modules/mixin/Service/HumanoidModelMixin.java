package dev.mlua.modules.mixin.Service;

import dev.mlua.runtime.client.MLuaHumanoidPoseApplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.1 model hook using the render-state based animation API. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void mlua$applyBonePoses(HumanoidRenderState state, CallbackInfo info) {
        int entityId = MLuaRenderStateEntityResolver.findEntityId(state);
        if (entityId >= 0) {
            MLuaHumanoidPoseApplier.apply((HumanoidModel<?>) (Object) this, entityId);
        }
    }
}
