package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCLevelRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;

public class MCLevelRendererImpl implements MCLevelRenderer {
    private final LevelRenderer inner;

    public MCLevelRendererImpl(LevelRenderer inner) {
        this.inner = inner;
    }

    public static MCLevelRendererImpl fromReal(LevelRenderer inner) {
        return inner == null ? null : new MCLevelRendererImpl(inner);
    }

    public LevelRenderer inner() {
        return inner;
    }

    @Override
    public void renderLevel(Object poseStack, float partialTick, long nanoTime, boolean renderBlockOutline,
                            Object camera, Object gameRenderer, Object lightTexture, Object projectionMatrix) {
        if (camera instanceof net.minecraft.client.Camera c
                && gameRenderer instanceof net.minecraft.client.renderer.GameRenderer gr
                && lightTexture instanceof net.minecraft.client.renderer.LightTexture lt
                && projectionMatrix instanceof org.joml.Matrix4f pm) {
            inner.renderLevel(DeltaTracker.ZERO, renderBlockOutline, c, gr, lt, new org.joml.Matrix4f(), pm);
        }
    }
}
