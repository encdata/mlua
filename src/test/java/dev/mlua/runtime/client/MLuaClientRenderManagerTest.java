package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaClientRenderManagerTest {
    @Test
    void hudPassIncludesOnlyHudAndWorldOverlayTargets() {
        assertTrue(MLuaHudRenderPass.isTarget(MLuaClientRenderManager.RenderTarget.HUD));
        assertTrue(MLuaHudRenderPass.isTarget(MLuaClientRenderManager.RenderTarget.WORLD_OVERLAY));
        assertFalse(MLuaHudRenderPass.isTarget(MLuaClientRenderManager.RenderTarget.MANUAL));
        assertFalse(MLuaHudRenderPass.isTarget(MLuaClientRenderManager.RenderTarget.WORLD_MODEL));
        assertFalse(MLuaHudRenderPass.isTarget(MLuaClientRenderManager.RenderTarget.ENTITY_MODEL));
        assertFalse(MLuaHudRenderPass.isTarget(MLuaClientRenderManager.RenderTarget.FIRST_PERSON_ITEM));
        assertFalse(MLuaHudRenderPass.isTarget(MLuaClientRenderManager.RenderTarget.THIRD_PERSON_ITEM));
    }
}
