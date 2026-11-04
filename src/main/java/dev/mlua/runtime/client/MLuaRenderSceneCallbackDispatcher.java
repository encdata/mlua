package dev.mlua.runtime.client;

import static dev.mlua.runtime.client.MLuaRenderHookRegistry.*;
import static dev.mlua.runtime.client.MLuaRenderCallbackInvoker.*;

import java.util.List;
import org.luaj.vm2.LuaValue;

/** Owns world-stage, custom-geometry, and HUD callback dispatch. */
final class MLuaRenderSceneCallbackDispatcher {
    private static final int DEFAULT_WORLD_RENDER_LIGHT = 15_728_880;

    private MLuaRenderSceneCallbackDispatcher() { }

    static void renderWorld(String stage, Object nativeContext, Object poseStack,
            Object bufferSource, Object camera) {
        String normalizedStage = dev.mlua.loaderapi.ClientBridge.normalizeWorldRenderStage(stage);
        HookBucket hooks = bucket(HookKind.WORLD, normalizedStage);
        if (hooks == null) return;
        List<Hook> renderHooks = renderHooks(hooks);
        if (renderHooks.isEmpty()) return;
        Float partialTick = partialTick(nativeContext);
        for (Hook hook : renderHooks) {
            Float renderPartialTick = partialTick;
            invokeSafely(hook, () -> renderContext(hook, "World", poseStack, bufferSource, null,
                renderPartialTick == null ? 0.0F : renderPartialTick, renderPartialTick != null,
                DEFAULT_WORLD_RENDER_LIGHT, 0, nativeContext,
                "Stage", LuaValue.valueOf(normalizedStage), "Camera", userdata(camera)));
        }
    }

    static void renderWorldGeometry(Object nativeContext, Object poseStack,
            Object renderSource, Object camera) {
        HookBucket hooks = bucket(HookKind.WORLD_GEOMETRY, "");
        if (hooks == null) return;
        List<Hook> renderHooks = renderHooks(hooks);
        if (renderHooks.isEmpty()) return;
        Float partialTick = partialTick(nativeContext);
        for (Hook hook : renderHooks) {
            Float renderPartialTick = partialTick;
            invokeSafely(hook, () -> renderContext(hook, "WorldGeometry", poseStack, renderSource, null,
                renderPartialTick == null ? 0.0F : renderPartialTick, renderPartialTick != null,
                DEFAULT_WORLD_RENDER_LIGHT, 0, nativeContext,
                "Stage", LuaValue.valueOf("CUSTOM_GEOMETRY"), "Camera", userdata(camera)));
        }
    }

    static void renderHud(Object graphics, int width, int height) {
        HookBucket hud = bucket(HookKind.HUD, "");
        if (hud == null) return;
        List<Hook> renderHooks = renderHooks(hud);
        if (renderHooks.isEmpty()) return;
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> renderContext(hook, "Hud", null, null, null,
                0.0F, false, 0, 0, null,
                "Graphics", userdata(graphics), "Width", LuaValue.valueOf(width),
                "Height", LuaValue.valueOf(height)));
        }
    }

    private static Float partialTick(Object context) {
        Float partialTick = MLuaRenderPartialTickResolver.read(context);
        return partialTick == null ? MLuaRenderPartialTickResolver.currentClient() : partialTick;
    }
}
