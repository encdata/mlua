package dev.mlua.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.runtime.client.MLuaRenderCallbacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies MLua's physical hand pose inside vanilla's scoped first-person arm and item render. */
// NeoForge ModDevGradle compiles against named Mojang classes but does not
// expose the mappings to Mixin's annotation processor. Keep this target in
// the same named namespace used by the NeoForge runtime.
@Mixin(value = ItemInHandRenderer.class, remap = false)
public abstract class FirstPersonItemRendererMixin {
    @Inject(method = "renderOneHandedMap", remap = false, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;)V", shift = At.Shift.BEFORE, remap = false))
    private void mlua$renderOneHandedMap(PoseStack poseStack, MultiBufferSource buffers, int light,
        float equipProgress, HumanoidArm arm, float swingProgress, ItemStack stack, CallbackInfo callbackInfo) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Float frameTick = MLuaRenderCallbacks.currentClientPartialTick();
        InteractionHand hand = player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        MLuaRenderCallbacks.renderFirstPersonItem(player, hand, stack, poseStack, buffers,
            frameTick, player.getXRot(),
            swingProgress, equipProgress, light);
    }

    @Inject(method = "renderTwoHandedMap", remap = false, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;)V", shift = At.Shift.BEFORE, remap = false))
    private void mlua$renderTwoHandedMap(PoseStack poseStack, MultiBufferSource buffers, int light,
        float pitch, float equipProgress, float swingProgress, CallbackInfo callbackInfo) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (!stack.is(Items.FILLED_MAP)) {
            stack = player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
        }
        if (!stack.is(Items.FILLED_MAP)) return;
        Float frameTick = MLuaRenderCallbacks.currentClientPartialTick();
        MLuaRenderCallbacks.renderFirstPersonItem(player, hand, stack, poseStack, buffers,
            frameTick, pitch, swingProgress, equipProgress, light);
    }

    @Inject(
        method = "renderArmWithItem",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            ordinal = 0,
            shift = At.Shift.AFTER,
            remap = false
        )
    )
    private void mlua$applyHandOffset(AbstractClientPlayer player, float partialTick, float pitch,
        InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress,
        PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo callbackInfo) {
        MLuaCameraController.applyFirstPersonHandOffset(poseStack, player, hand, partialTick);
    }

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
    private void mlua$renderFirstPersonItem(AbstractClientPlayer player, float partialTick, float pitch,
        InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress,
        PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo callbackInfo) {
        MLuaRenderCallbacks.renderFirstPersonItem(player, hand, stack, poseStack, buffers,
            partialTick, pitch, swingProgress, equipProgress, light);
    }
}
