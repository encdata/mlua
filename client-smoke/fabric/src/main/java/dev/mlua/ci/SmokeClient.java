package dev.mlua.ci;

import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SmokeClient implements ClientModInitializer {
    private static final int EXIT_AFTER_TICKS = 40;

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Fabric Loader");
        }
        AtomicInteger ticks = new AtomicInteger();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ticks.incrementAndGet() >= EXIT_AFTER_TICKS) {
                System.out.println("MLUA_PACKAGED_CLIENT_SMOKE_OK");
                client.stop();
            }
        });
    }
}
