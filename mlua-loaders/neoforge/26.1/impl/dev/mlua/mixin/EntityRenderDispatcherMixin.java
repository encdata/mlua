package dev.mlua.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
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
@Mixin(value = EntityRenderDispatcher.class, remap = false)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "extractEntity", remap = false, at = @At(value = "RETURN", remap = false))
    private void mlua$associateRenderState(Entity entity, float partialTick,
            CallbackInfoReturnable<EntityRenderState> callbackInfo) {
        MLuaRenderStateEntityResolver.associate(callbackInfo.getReturnValue(), entity);
    }

    @Inject(
        method = "submit",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            shift = At.Shift.BEFORE,
            remap = false
        )
    )
    private void mlua$renderEntityPass(EntityRenderState state, CameraRenderState camera,
            double x, double y, double z, PoseStack poseStack, SubmitNodeCollector collector,
            CallbackInfo callbackInfo) {
        if (!MLuaRenderCallbacks.hasEntityDrawCallbacks(state.entityType)) return;
        Entity entity = MLuaRenderStateEntityResolver.findEntity(state);
        if (entity == null) return;
        EntityRenderer<?, ?> renderer = ((EntityRenderDispatcher) (Object) this).getRenderer(state);
        MLuaRenderCallbacks.renderEntityDraw(entity, renderer, poseStack, collector, null, state.lightCoords, state);
    }
}
