package dev.mlua.modules.mixin.Service;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
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
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) {
            Float partialTick = MLuaRenderStateEntityResolver.findPartialTick(state);
            MLuaClientRenderManager.renderEntityAttachments(null, MLuaEntityScope.of(entity), poseStack,
                collector, state.lightCoords, partialTick == null ? 0.0F : partialTick);
        }
        if (!MLuaRenderCallbacks.hasEntityDrawCallbacks(state.entityType)) return;
        EntityRenderer<?, ?> renderer = ((EntityRenderDispatcher) (Object) this).getRenderer(state);
        MLuaRenderCallbacks.renderEntityDraw(entity, renderer, poseStack, collector,
            MLuaRenderStateEntityResolver.findPartialTick(state), state.lightCoords, state);
    }
}
