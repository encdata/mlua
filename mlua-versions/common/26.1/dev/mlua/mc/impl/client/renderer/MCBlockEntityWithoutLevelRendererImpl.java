package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCBlockEntityWithoutLevelRenderer;

public class MCBlockEntityWithoutLevelRendererImpl implements MCBlockEntityWithoutLevelRenderer {
    private final Object inner;
    public MCBlockEntityWithoutLevelRendererImpl(Object inner) { this.inner = inner; }
    public static MCBlockEntityWithoutLevelRendererImpl fromReal(Object inner) { return inner == null ? null : new MCBlockEntityWithoutLevelRendererImpl(inner); }
    public Object inner() { return inner; }
    @Override public void renderByItem(Object itemStack, Object transform, Object poseStack, Object bufferSource, int light, int overlay) {
        throw LegacyRenderPath.unavailable("BlockEntityWithoutLevelRenderer.renderByItem");
    }
}
