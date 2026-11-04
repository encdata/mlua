package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricLifecycleBridge;
import dev.mlua.loaderapi.FrameClock;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public final class FabricLifecycleWiring {
    private static volatile FabricLifecycleBridge renderBridge;
    private static FrameClock renderFrameClock;

    private FabricLifecycleWiring() {}
    public static void wireClient(FabricLifecycleBridge bridge) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> bridge.fireClientTickEnd());
    }
    public static void wire(FabricLifecycleBridge bridge) {
        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> bridge.fireServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(bridge::fireServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(server -> bridge.fireServerTickEnd());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> bridge.fireRegisterCommands(dispatcher));
    }
    public static void wireRender(FabricLifecycleBridge bridge) {
        renderFrameClock = new FrameClock();
        renderBridge = bridge;
    }

    public static void fireRenderFrameStart() {
        FabricLifecycleBridge bridge = renderBridge;
        FrameClock clock = renderFrameClock;
        if (bridge != null && clock != null) bridge.fireRenderTickStart(clock.sampleSeconds());
    }

    public static void fireRenderFrameEnd() {
        FabricLifecycleBridge bridge = renderBridge;
        if (bridge != null) bridge.fireRenderTickEnd();
    }
}
