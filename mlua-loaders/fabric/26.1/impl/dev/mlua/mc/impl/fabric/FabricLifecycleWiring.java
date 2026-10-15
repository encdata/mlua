package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricLifecycleBridge;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public final class FabricLifecycleWiring {
    private FabricLifecycleWiring() {}
    public static void wireClient(FabricLifecycleBridge bridge) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> bridge.fireClientTickEnd());
    }
    public static void wire(FabricLifecycleBridge bridge) {
        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> bridge.fireServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(bridge::fireServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(server -> bridge.fireServerTickEnd());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> bridge.fireRegisterCommands(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(client -> bridge.fireClientTickEnd());
    }
    public static void wireRender(FabricLifecycleBridge bridge) {
        long[] previousFrameNanos = {System.nanoTime()};
        LevelRenderEvents.START_MAIN.register(context -> {
            long now = System.nanoTime();
            double delta = (now - previousFrameNanos[0]) / 1.0E9;
            previousFrameNanos[0] = now;
            if (delta <= 0.0 || delta > 0.1) delta = 0.05;
            bridge.fireRenderTickStart(delta);
        });
        LevelRenderEvents.END_MAIN.register(context -> bridge.fireRenderTickEnd());
    }
}
