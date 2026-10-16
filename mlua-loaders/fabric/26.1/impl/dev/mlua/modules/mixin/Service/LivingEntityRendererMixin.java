package dev.mlua.modules.mixin.Service;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry;
import net.minecraft.client.Minecraft;
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

/** Render-state version of the living entity adapter hook. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends net.minecraft.world.entity.LivingEntity,
        S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Shadow protected M model;

    @Inject(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V",
            shift = At.Shift.AFTER
        )
    )
    private void mlua$applyRegisteredModelAdapter(S state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
        int entityId = MLuaRenderStateEntityResolver.findEntityId(state);
        if (entityId >= 0) {
            var level = Minecraft.getInstance().level;
            var entity = level == null ? null : level.getEntity(entityId);
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity livingEntity)) return;
            MLuaEntityScope scope = MLuaEntityScope.of(livingEntity);
            MLuaModelAdapterRegistry.apply(model, scope);
            MLuaRenderCallbacks.render(livingEntity, model, poseStack, collector, 0.0F, state.lightCoords, 0);
            MLuaClientRenderManager.renderEntityAttachments(model, scope, poseStack, collector, state.lightCoords);
        }
    }
}
