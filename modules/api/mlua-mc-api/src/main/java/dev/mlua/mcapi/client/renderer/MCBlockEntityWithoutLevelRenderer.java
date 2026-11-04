package dev.mlua.mcapi.client.renderer;

public interface MCBlockEntityWithoutLevelRenderer {
    void renderByItem(Object itemStack, Object transform, Object poseStack, Object bufferSource, int light, int overlay);
}
