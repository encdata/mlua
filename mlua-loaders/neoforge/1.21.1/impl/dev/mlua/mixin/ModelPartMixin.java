package dev.mlua.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies addon animation and part state to custom entity model parts only while they render. */
@Mixin(value = ModelPart.class, remap = false)
public abstract class ModelPartMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
        at = @At("HEAD"), remap = false)
    private void mlua$applyEntityBonePose(PoseStack poseStack, VertexConsumer consumer,
            int packedLight, int packedOverlay, CallbackInfo callbackInfo) {
        MLuaModelAdapterRegistry.beginModelPartRender((ModelPart) (Object) this);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
        at = @At("RETURN"), remap = false)
    private void mlua$restoreEntityBonePose(PoseStack poseStack, VertexConsumer consumer,
            int packedLight, int packedOverlay, CallbackInfo callbackInfo) {
        MLuaModelAdapterRegistry.endModelPartRender((ModelPart) (Object) this);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
        at = @At("HEAD"), remap = false)
    private void mlua$applyEntityBonePoseWithColor(PoseStack poseStack, VertexConsumer consumer,
            int packedLight, int packedOverlay, int packedColor, CallbackInfo callbackInfo) {
        MLuaModelAdapterRegistry.beginModelPartRender((ModelPart) (Object) this);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
        at = @At("RETURN"), remap = false)
    private void mlua$restoreEntityBonePoseWithColor(PoseStack poseStack, VertexConsumer consumer,
            int packedLight, int packedOverlay, int packedColor, CallbackInfo callbackInfo) {
        MLuaModelAdapterRegistry.endModelPartRender((ModelPart) (Object) this);
    }
}
