package dev.mlua.ci;

import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SmokeServer implements ModInitializer {
    private static final int EXIT_AFTER_TICKS = 40;

    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Fabric Loader");
        }
        AtomicInteger ticks = new AtomicInteger();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ticks.incrementAndGet() >= EXIT_AFTER_TICKS) {
                System.out.println("MLUA_PACKAGED_SERVER_SMOKE_OK");
                server.halt(false);
            }
        });
    }
}
