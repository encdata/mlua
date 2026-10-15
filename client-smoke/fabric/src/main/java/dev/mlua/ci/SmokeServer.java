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
            runMixinProbe();
            System.out.println("MLUA_PACKAGED_SERVER_BOOT_OK");
            server.halt(false);
        });
    }

    private static void runMixinProbe() {
        try {
            Class.forName("dev.mlua.ci.SmokeTarget", true, SmokeServer.class.getClassLoader())
                .getMethod("probe").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke the Lua mixin smoke target", exception);
        }
    }
}
