package dev.mlua.modules.mixin.Service;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        MLuaRenderCallbacks.renderEntityDraw(entity, renderer, poseStack, buffers, partialTick, packedLight);
    }
}
