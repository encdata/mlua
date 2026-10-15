package dev.mlua.ci;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SmokeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Fabric Loader");
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            System.out.println("MLUA_PACKAGED_CLIENT_BOOT_OK");
            client.stop();
        });
    }
}
