package dev.mlua.ci;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SmokeClient implements ClientModInitializer {
    private static int ticks;

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Fabric Loader");
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!"called".equals(System.getProperty("mlua.ci.service.CLIENT"))) {
                if (++ticks == 200) {
                    System.out.println("MLUA_CI_CLIENT_SERVICE_MISSING");
                    client.stop();
                }
                return;
            }
            System.out.println("MLUA_PACKAGED_CLIENT_BOOT_OK");
            client.stop();
        });
    }
}
