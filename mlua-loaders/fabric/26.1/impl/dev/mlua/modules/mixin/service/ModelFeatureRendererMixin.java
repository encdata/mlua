package dev.mlua.modules.mixin.service;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.MLuaClientModelManager;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import dev.mlua.runtime.client.MLuaRenderStateEntityResolver;
import net.minecraft.client.model.Model;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs Lua model behavior at the collector's actual model draw point. */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
    @Shadow @Final private PoseStack poseStack;
    @Unique private SubmitNodeStorage.ModelSubmit<?> mlua$callbackSubmission;

    @Inject(method = "renderModel", at = @At("HEAD"))
    private <S> void mlua$beginModelSubmission(SubmitNodeStorage.ModelSubmit<S> submission,
            RenderType renderType, VertexConsumer vertices, OutlineBufferSource outline,
            MultiBufferSource.BufferSource buffers, CallbackInfo info) {
        mlua$callbackSubmission = null;
    }

    @Inject(method = "renderModel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
        shift = At.Shift.BEFORE))
    private <S> void mlua$applyEntityModelBehavior(SubmitNodeStorage.ModelSubmit<S> submission,
            RenderType renderType, VertexConsumer vertices, OutlineBufferSource outline,
            MultiBufferSource.BufferSource buffers, CallbackInfo info) {
        if (!(submission.state() instanceof EntityRenderState state)) return;
        Entity entity = MLuaRenderStateEntityResolver.findEntity(state);
        if (entity == null) return;
        MLuaEntityScope scope = MLuaEntityScope.of(entity);

        Model<?> model = submission.model();
        // Nonliving adapters are already prepared by the dispatcher scope; applying here would run them twice.
        if (entity instanceof LivingEntity) MLuaModelAdapterRegistry.apply(model, scope);
        if (mlua$callbackSubmission == submission) return;
        mlua$callbackSubmission = submission;
        Object primaryModel = MLuaModelAdapterRegistry.findAdaptedModel(
            Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state));
        if (primaryModel != submission.model()) return;
        Float partialTick = MLuaRenderStateEntityResolver.findPartialTick(state);
        if (entity instanceof LivingEntity livingEntity) {
            MLuaRenderCallbacks.render(livingEntity, submission.model(), poseStack, vertices,
                partialTick == null ? 0.0F : partialTick, submission.lightCoords(),
                submission.overlayCoords(), partialTick != null, state);
            MLuaClientRenderManager.renderEntityAttachmentsAtModelDraw(submission.model(),
                scope, poseStack, buffers, submission.lightCoords(),
                submission.overlayCoords(), partialTick == null ? 0.0F : partialTick);
        }
    }

    @Redirect(method = "renderModel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void mlua$restoreEntityModelBehaviorAfterDraw(Model<?> model, PoseStack poseStack,
            VertexConsumer vertices, int packedLight, int packedOverlay, int packedColor) {
        try {
            model.renderToBuffer(poseStack, vertices, packedLight, packedOverlay, packedColor);
        } finally {
            MLuaClientModelManager.restoreOverrides(model);
        }
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private <S> void mlua$restoreEntityModelBehaviorOnReturn(SubmitNodeStorage.ModelSubmit<S> submission,
            RenderType renderType, VertexConsumer vertices, OutlineBufferSource outline,
            MultiBufferSource.BufferSource buffers, CallbackInfo info) {
        MLuaClientModelManager.restoreOverrides(submission.model());
    }
}
