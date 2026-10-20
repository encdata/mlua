package dev.mlua.mixin;

import net.minecraft.world.entity.LivingEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.runtime.client.MLuaClientModelManager;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores MLua's live entity model access at the render-state submit stage. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity,
        S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Shadow protected M model;

    @Inject(method = "submit", remap = false, at = @At("HEAD"))
    private void mlua$restoreRegisteredModelOverrides(S state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
        MLuaClientModelManager.restoreOverrides(model);
    }

    @Inject(method = "submit", remap = false, at = @At("RETURN"))
    private void mlua$clearRegisteredModelOverrides(S state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
        MLuaClientModelManager.restoreOverrides(model);
    }

}
