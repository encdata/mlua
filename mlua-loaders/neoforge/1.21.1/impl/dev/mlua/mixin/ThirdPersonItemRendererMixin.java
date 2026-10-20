package dev.mlua.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Calls addon passes immediately before NeoForge submits a third-person held item. */
@Mixin(value = ItemInHandLayer.class, remap = false)
public abstract class ThirdPersonItemRendererMixin {
    @Inject(
        method = "renderArmWithItem",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.BEFORE,
            remap = false
        )
    )
    private void mlua$renderThirdPersonItem(LivingEntity entity, ItemStack stack,
            ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, CallbackInfo callbackInfo) {
        MLuaRenderCallbacks.renderThirdPersonItem(entity, stack, arm, displayContext,
            poseStack, buffers, packedLight);
    }
}
