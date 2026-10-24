package dev.mlua.runtime.client;

import dev.mlua.runtime.assets.MLuaGeneratedAssetPack;
import dev.mlua.loaderapi.LoaderApi.LoaderProvider;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.modules.coregui.service.MLuaNativeScreenManager;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Client-only runtime entry points, loaded reflectively by shared/server code. */
public final class MLuaClientRuntimeHooks {
    private MLuaClientRuntimeHooks() { }

    public static void initialize(LoaderProvider provider, MLuaRuntime runtime) {
        MLuaCallbackBudget.installClientWaitPump(callback ->
            MLuaClientThread.runWithResumeWaitHook(callback::get));
        MLuaPackFinder.registerResourcePack();
        provider.lifecycle().onClientTickEnd(() -> {
            MLuaClientThread.captureCurrent();
            MLuaClientThread.runSynchronousClientCallback(() -> {
                runtime.runClientScriptsOnce();
                runtime.onClientTickEnd();
            });
            MLuaClientSoundManager.tick();
            MLuaClientParticleManager.tick();
            MLuaNativeScreenManager.tickPending();
        });
        provider.events().onClientWorldLoad(world -> {
            if (world instanceof Level level) runtime.queueClientWorldLifecycle(level, true);
        });
        provider.events().onClientWorldUnload(world -> {
            if (world instanceof Level level) runtime.queueClientWorldLifecycle(level, false);
        });
        provider.lifecycle().onRenderTickStart(deltaTime -> MLuaClientEventHandlers.onRenderTickStart(runtime, deltaTime));
        provider.lifecycle().onRenderTickEnd(MLuaClientEventHandlers::onRenderTickEnd);
        provider.lifecycle().onClientSetup(() -> {
            MLuaConfigClientHooks.registerConfigScreens();
            if ("fabric".equalsIgnoreCase(provider.platform().loaderName())) {
                MLuaClientModels.registerEntityRenderers();
            }
            MLuaGuiRenderer.registerOverlay();
            MLuaClientRenderManager.registerOverlay();
            MLuaInputHandler.registerHandlers();
        });
    }

    public static void generateAssets() {
        MLuaGeneratedAssetPack.generate();
    }

    public static void flushDiagnostics(List<String> messages) {
        LocalPlayer player;
        try {
            player = MLuaMinecraftClient.localPlayer();
        } catch (IllegalStateException unavailable) {
            return;
        }
        if (player == null) return;
        for (String message : messages) player.sendSystemMessage(Component.literal(message));
    }

    public static boolean isWindowActive(boolean fallback) {
        try {
            return MLuaMinecraftClient.instance().isWindowActive();
        } catch (Throwable unavailable) {
            return fallback;
        }
    }

    public static Level currentLevel() {
        return MLuaMinecraftClient.instance().level;
    }
}
