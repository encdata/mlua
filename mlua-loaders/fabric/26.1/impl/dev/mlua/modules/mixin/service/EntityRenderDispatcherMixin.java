package dev.mlua.modules.mixin.service;

import net.minecraft.world.entity.LivingEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry;
import dev.mlua.runtime.client.MLuaRenderStateEntityResolver;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dispatches Lua passes for render-state entities before their native renderer submits. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void mlua$associateRenderState(Entity entity, float partialTick,
            CallbackInfoReturnable<EntityRenderState> callbackInfo) {
        MLuaRenderStateEntityResolver.associate(callbackInfo.getReturnValue(), entity, partialTick);
    }

    @Inject(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            shift = At.Shift.BEFORE
        )
    )
    private void mlua$renderEntityPass(EntityRenderState state, CameraRenderState camera,
            double x, double y, double z, PoseStack poseStack, SubmitNodeCollector collector,
            CallbackInfo callbackInfo) {
        Entity entity = MLuaRenderStateEntityResolver.findEntity(state);
        if (entity == null) return;
        EntityRenderer<?, ?> renderer = ((EntityRenderDispatcher) (Object) this).getRenderer(state);
        MLuaModelAdapterRegistry.beginEntityRender(entity, renderer);
        if (MLuaRenderCallbacks.hasEntityDrawCallbacks(state.entityType)) {
            MLuaRenderCallbacks.renderEntityDraw(entity, renderer, poseStack, collector,
                MLuaRenderStateEntityResolver.findPartialTick(state), state.lightCoords, state);
        }
    }

    @Inject(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            shift = At.Shift.AFTER
        )
    )
    private void mlua$renderNonLivingAttachments(EntityRenderState state, CameraRenderState camera,
            double x, double y, double z, PoseStack poseStack, SubmitNodeCollector collector,
            CallbackInfo callbackInfo) {
        Entity entity = MLuaRenderStateEntityResolver.findEntity(state);
        if (entity == null) return;
        MLuaModelAdapterRegistry.endEntityRender(entity);
        EntityRenderer<?, ?> renderer = ((EntityRenderDispatcher) (Object) this).getRenderer(state);
        boolean hasPostDrawCallbacks = MLuaRenderCallbacks.hasEntityPostDrawCallbacks(entity.getType());
        boolean living = entity instanceof LivingEntity;
        boolean hasModelAttachments = !living && MLuaClientRenderManager.hasEntityModel(entity)
            && MLuaClientRenderManager.hasEntityBoneAttachments(entity);
        Object model = (hasPostDrawCallbacks || hasModelAttachments)
            ? MLuaModelAdapterRegistry.findAdaptedModel(renderer) : null;
        if (hasPostDrawCallbacks) {
            MLuaRenderCallbacks.renderEntityPostDraw(entity, renderer, poseStack, collector,
                MLuaRenderStateEntityResolver.findPartialTick(state), state.lightCoords, state, model);
        }
        if (living || !MLuaClientRenderManager.hasEntityModel(entity)) return;
        Float partialTick = MLuaRenderStateEntityResolver.findPartialTick(state);
        MLuaClientRenderManager.renderEntityAttachments(model, MLuaEntityScope.of(entity), poseStack,
            collector, state.lightCoords, partialTick == null ? 0.0F : partialTick);
    }

    @Redirect(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
        )
    )
    private void mlua$closeModelScopeOnSubmitFailure(EntityRenderer<?, ?> renderer, EntityRenderState state,
            PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        try {
            ((net.minecraft.client.renderer.entity.EntityRenderer) renderer).submit(state, poseStack, collector, camera);
        } catch (RuntimeException | Error failure) {
            MLuaModelAdapterRegistry.abortEntityRender(MLuaRenderStateEntityResolver.findEntity(state), renderer);
            throw failure;
        }
    }
}
