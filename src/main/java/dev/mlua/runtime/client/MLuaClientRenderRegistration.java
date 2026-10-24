package dev.mlua.runtime.client;

import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.loaderapi.LoaderApi;
import net.minecraft.client.gui.GuiGraphics;

/** Installs loader-facing render callbacks while keeping hook ownership out of the render-object store. */
final class MLuaClientRenderRegistration {
    private static boolean worldRenderersRegistered;

    private MLuaClientRenderRegistration() {
    }

    static synchronized void register() {
        MLuaRenderFrameLifecycle.registerAnimationClock();
        LoaderApi.client().registerOverlay("mlua_render_service", value -> {
            if (value instanceof GuiGraphics graphics) MLuaClientRenderManager.renderAll(graphics);
        });
        if (worldRenderersRegistered) return;

        for (String stage : ClientBridge.WORLD_RENDER_STAGES) {
            LoaderApi.client().registerWorldRenderer(stage,
                context -> MLuaClientRenderManager.renderWorldContext(stage, context));
        }
        LoaderApi.client().registerWorldGeometryRenderer(context -> {
            Object pose = MLuaRenderContextAccess.optional(context, "matrixStack", "getPoseStack", "poseStack");
            // NeoForge 26.1 submits custom geometry through SubmitNodeCollector instead of a buffer source.
            Object buffers = MLuaRenderContextAccess.optional(context, "renderSource", "consumers", "getRenderTypeBuffer",
                "bufferSource", "getSubmitNodeCollector", "submitNodeCollector", "getRenderCollector");
            Object camera = MLuaRenderContextAccess.optional(context, "camera", "getCamera");
            MLuaRenderCallbacks.renderWorldGeometry(context, pose, buffers, camera);
        });
        worldRenderersRegistered = true;
    }
}
