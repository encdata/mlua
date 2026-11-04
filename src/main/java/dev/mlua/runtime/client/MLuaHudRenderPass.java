package dev.mlua.runtime.client;

/** Shared HUD target classification used by loader-specific render managers. */
final class MLuaHudRenderPass {
    private MLuaHudRenderPass() { }

    static boolean isTarget(MLuaClientRenderManager.RenderTarget target) {
        return target != MLuaClientRenderManager.RenderTarget.MANUAL
            && target != MLuaClientRenderManager.RenderTarget.WORLD_MODEL
            && target != MLuaClientRenderManager.RenderTarget.ENTITY_MODEL
            && target != MLuaClientRenderManager.RenderTarget.FIRST_PERSON_ITEM
            && target != MLuaClientRenderManager.RenderTarget.THIRD_PERSON_ITEM;
    }
}
