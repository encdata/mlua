package dev.mlua.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.MLuaClientModelManager;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies Lua model adapters and render passes to living entities on NeoForge 1.21.1. */
@Mixin(value = LivingEntityRenderer.class, remap = false)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow protected M model;

    @Inject(method = "render", remap = false, at = @At("HEAD"))
    private void mlua$restoreRegisteredModelOverrides(T entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, CallbackInfo info) {
        MLuaClientModelManager.restoreOverrides(model);
    }

    @Inject(method = "render", remap = false, at = @At("RETURN"))
    private void mlua$clearRegisteredModelOverrides(T entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, CallbackInfo info) {
        MLuaClientModelManager.restoreOverrides(model);
    }

    @Inject(
        method = "render",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
            shift = At.Shift.AFTER,
            remap = false
        )
    )
    private void mlua$applyRegisteredModelAdapter(T entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, CallbackInfo info) {
        if (entity == null) return;
        MLuaEntityScope scope = MLuaEntityScope.of(entity);
        MLuaModelAdapterRegistry.apply(model, scope);
        MLuaRenderCallbacks.render(entity, model, poseStack, buffers,
            partialTick, packedLight, OverlayTexture.NO_OVERLAY);
        MLuaClientRenderManager.renderEntityAttachments(model, scope, poseStack, buffers,
            packedLight, OverlayTexture.NO_OVERLAY, partialTick);
    }
}
