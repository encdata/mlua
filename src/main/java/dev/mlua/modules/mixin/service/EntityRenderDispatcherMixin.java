package dev.mlua.modules.mixin.service;

import net.minecraft.world.entity.LivingEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dispatches entity-level Lua render passes before every vanilla entity renderer. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.BEFORE
        )
    )
    private void mlua$renderEntityPass(Entity entity, double x, double y, double z,
            float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, CallbackInfo callbackInfo) {
        EntityRenderer<?> renderer = ((EntityRenderDispatcher) (Object) this).getRenderer(entity);
        MLuaModelAdapterRegistry.beginEntityRender(entity, renderer);
        MLuaRenderCallbacks.renderEntityDraw(entity, renderer, poseStack, buffers, partialTick, packedLight);
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.AFTER
        )
    )
    private void mlua$renderNonLivingAttachments(Entity entity, double x, double y, double z,
            float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, CallbackInfo callbackInfo) {
        MLuaModelAdapterRegistry.endEntityRender(entity);
        EntityRenderer<?> renderer = ((EntityRenderDispatcher) (Object) this).getRenderer(entity);
        boolean hasPostDrawCallbacks = MLuaRenderCallbacks.hasEntityPostDrawCallbacks(entity.getType());
        boolean living = entity instanceof LivingEntity;
        boolean hasModelAttachments = !living && MLuaClientRenderManager.hasEntityModel(entity)
            && MLuaClientRenderManager.hasEntityBoneAttachments(entity);
        Object model = (hasPostDrawCallbacks || hasModelAttachments)
            ? MLuaModelAdapterRegistry.findAdaptedModel(renderer) : null;
        if (hasPostDrawCallbacks) {
            MLuaRenderCallbacks.renderEntityPostDraw(entity, renderer, poseStack, buffers,
                partialTick, packedLight, null, model);
        }
        if (living || !MLuaClientRenderManager.hasEntityModel(entity)) return;
        MLuaClientRenderManager.renderEntityAttachments(model, MLuaEntityScope.of(entity), poseStack,
            buffers, packedLight, OverlayTexture.NO_OVERLAY, partialTick);
    }

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
        )
    )
    private void mlua$closeModelScopeOnRenderFailure(EntityRenderer<?> renderer, Entity entity,
            float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        try {
            ((EntityRenderer) renderer).render(entity, yaw, partialTick, poseStack, buffers, packedLight);
        } catch (RuntimeException | Error failure) {
            MLuaModelAdapterRegistry.abortEntityRender(entity, renderer);
            throw failure;
        }
    }
}
