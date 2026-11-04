package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCBlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.ItemDisplayContext;

public class MCBlockEntityWithoutLevelRendererImpl implements MCBlockEntityWithoutLevelRenderer {
    private final BlockEntityWithoutLevelRenderer inner;

    public MCBlockEntityWithoutLevelRendererImpl(BlockEntityWithoutLevelRenderer inner) {
        this.inner = inner;
    }

    public static MCBlockEntityWithoutLevelRendererImpl fromReal(BlockEntityWithoutLevelRenderer inner) {
        return inner == null ? null : new MCBlockEntityWithoutLevelRendererImpl(inner);
    }

    public BlockEntityWithoutLevelRenderer inner() {
        return inner;
    }

    @Override
    public void renderByItem(Object itemStack, Object transform, Object poseStack, Object bufferSource, int light, int overlay) {
        if (itemStack instanceof net.minecraft.world.item.ItemStack is
                && transform instanceof ItemDisplayContext tdc
                && poseStack instanceof com.mojang.blaze3d.vertex.PoseStack ps
                && bufferSource instanceof net.minecraft.client.renderer.MultiBufferSource mbs) {
            inner.renderByItem(is, tdc, ps, mbs, light, overlay);
        }
    }
}
