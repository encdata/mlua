package dev.mlua.modules.mixin.service;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies MLua's physical hand pose inside vanilla's scoped first-person arm and item render. */
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonItemRendererMixin {
    @Inject(method = "renderOneHandedMap", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/item/ItemStack;)V",
        shift = At.Shift.BEFORE))
    private void mlua$renderOneHandedMap(PoseStack poseStack, SubmitNodeCollector collector, int light,
        float equipProgress, HumanoidArm arm, float swingProgress, ItemStack stack, CallbackInfo callbackInfo) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Float frameTick = MLuaRenderCallbacks.currentClientPartialTick();
        InteractionHand hand = player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        MLuaRenderCallbacks.renderFirstPersonItem(player, hand, stack, poseStack, collector,
            frameTick, frameTick == null ? 0.0F : player.getXRot(frameTick),
            swingProgress, equipProgress, light);
    }

    @Inject(method = "renderTwoHandedMap", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/item/ItemStack;)V",
        shift = At.Shift.BEFORE))
    private void mlua$renderTwoHandedMap(PoseStack poseStack, SubmitNodeCollector collector, int light,
        float pitch, float equipProgress, float swingProgress, CallbackInfo callbackInfo) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (!stack.has(DataComponents.MAP_ID)) {
            stack = player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
        }
        if (!stack.has(DataComponents.MAP_ID)) return;
        Float frameTick = MLuaRenderCallbacks.currentClientPartialTick();
        MLuaRenderCallbacks.renderFirstPersonItem(player, hand, stack, poseStack, collector,
            frameTick, pitch, swingProgress, equipProgress, light);
    }

    @Inject(
        method = "renderArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void mlua$applyHandOffset(AbstractClientPlayer player, float partialTick, float pitch,
        InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress,
        PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo callbackInfo) {
        MLuaCameraController.applyFirstPersonHandOffset(poseStack, player, hand, partialTick);
    }

    @Inject(
        method = "renderArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            shift = At.Shift.BEFORE
        )
    )
    private void mlua$renderFirstPersonItem(AbstractClientPlayer player, float partialTick, float pitch,
        InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress,
        PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo callbackInfo) {
        MLuaRenderCallbacks.renderFirstPersonItem(player, hand, stack, poseStack, collector,
            partialTick, pitch, swingProgress, equipProgress, light);
    }
}
