package dev.mlua.ci;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class SmokeServer implements ModInitializer {
    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Fabric Loader");
        }
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            System.out.println("MLUA_PACKAGED_SERVER_BOOT_OK");
            server.halt(false);
        });
    }
}
