package dev.mlua.modules.mixin.Service;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import dev.mlua.runtime.client.MLuaRenderStateEntityResolver;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Calls addon passes at the final third-person held-item submission point. */
@Mixin(ItemInHandLayer.class)
public abstract class ThirdPersonItemRendererMixin {
    @Inject(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
            shift = At.Shift.BEFORE
        )
    )
    private void mlua$renderThirdPersonItem(ArmedEntityRenderState state,
            ItemStackRenderState renderedStack, ItemStack stack, HumanoidArm arm,
            PoseStack poseStack, SubmitNodeCollector collector, int packedLight, CallbackInfo callbackInfo) {
        if (!MLuaRenderCallbacks.hasThirdPersonItemCallbacks(stack)) return;
        if (MLuaRenderStateEntityResolver.findEntity(state) instanceof LivingEntity entity) {
            MLuaRenderCallbacks.renderThirdPersonItem(entity, stack, arm,
                arm == HumanoidArm.LEFT
                    ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                poseStack, collector, packedLight, MLuaRenderStateEntityResolver.findPartialTick(state));
        }
    }
}
