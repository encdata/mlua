package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricLifecycleBridge;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Server-only lifecycle wiring; client Fabric API must never be resolved on a dedicated server. */
public final class FabricServerLifecycleWiring {
    private FabricServerLifecycleWiring() {}

    public static void wire(FabricLifecycleBridge bridge) {
        ServerLifecycleEvents.SERVER_STARTED.register(bridge::fireServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(bridge::fireServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(server -> bridge.fireServerTickEnd());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> bridge.fireRegisterCommands(dispatcher));
    }
}
