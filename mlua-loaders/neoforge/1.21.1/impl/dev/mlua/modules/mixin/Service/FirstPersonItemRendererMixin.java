package dev.mlua.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.minecraft.client.MLuaCameraController;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
        MLuaCameraController.applyFirstPersonHandOffset(poseStack, player, hand);
    }
}
