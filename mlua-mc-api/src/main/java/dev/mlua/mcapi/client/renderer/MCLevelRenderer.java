package dev.mlua.mcapi.client.renderer;

public interface MCLevelRenderer {
    void renderLevel(Object poseStack, float partialTick, long nanoTime, boolean renderBlockOutline,
                     Object camera, Object gameRenderer, Object lightTexture, Object projectionMatrix);
}
