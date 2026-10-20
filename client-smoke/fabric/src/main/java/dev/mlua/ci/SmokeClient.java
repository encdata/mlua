package dev.mlua.ci;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SmokeClient implements ClientModInitializer {
    private static int serviceTicks;
    private static int lifecycleStartupTicks;
    private static int lifecycleTicks;
    private static boolean booted;

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Fabric Loader");
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!"called".equals(System.getProperty("mlua.ci.service.CLIENT"))) {
                if (++serviceTicks == 200) {
                    System.out.println("MLUA_CI_CLIENT_SERVICE_MISSING");
                    client.stop();
                }
                return;
            }
            if (!"complete".equals(System.getProperty("mlua.ci.native_screen_lifecycle"))) {
                lifecycleStartupTicks++;
                String screenClass = client.screen == null ? "none" : client.screen.getClass().getName();
                if (screenClass.equals("dev.mlua.modules.coregui.service.MLuaScriptScreen")) {
                    lifecycleTicks++;
                } else {
                    lifecycleTicks = 0;
                }
                if (lifecycleTicks == 1) System.out.println("MLUA_CI_NATIVE_SCREEN_OBSERVED");
                if (lifecycleStartupTicks == 2400 || lifecycleTicks == 200) {
                    System.out.println("MLUA_CI_ACTIVE_SCREEN=" + screenClass);
                    System.out.println("MLUA_CI_NATIVE_SCREEN_LIFECYCLE_MISSING");
                    client.stop();
                }
                return;
            }
            if (booted) return;
            PlayerTestServerConnector.connectIfRequested(client);
            booted = true;
            System.out.println("MLUA_PACKAGED_CLIENT_BOOT_OK");
            if (!Boolean.parseBoolean(System.getenv("MLUA_CI_PLAYER_TEST"))) client.stop();
        });
    }
}
