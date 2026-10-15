package dev.mlua.loader.impl.neoforge;

import dev.mlua.loaderapi.LifecycleBridge;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class NeoForgeLifecycleBridge implements LifecycleBridge {
    private final IEventBus modBus;
    private Consumer<Object> serverStarted;
    private Consumer<Object> serverStopping;
    private Consumer<Object> registerCommands;
    private Runnable serverTickEnd;
    private Runnable clientTickEnd;
    private DoubleConsumer renderTickStart;
    private Runnable renderTickEnd;
    private long lastRenderNanos = System.nanoTime();

    public NeoForgeLifecycleBridge(IEventBus modBus) {
        this.modBus = modBus;
        NeoForge.EVENT_BUS.addListener(this::handleServerStarted);
        NeoForge.EVENT_BUS.addListener(this::handleServerStopping);
        NeoForge.EVENT_BUS.addListener(this::handleRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::handleServerTickEnd);
        NeoForge.EVENT_BUS.addListener(this::handleClientTickEnd);
        NeoForge.EVENT_BUS.addListener(this::handleRenderFrame);
    }

    @Override public void onConstruct(Runnable callback) { callback.run(); }
    @Override public void onSetup(Runnable callback) { modBus.addListener((FMLCommonSetupEvent event) -> callback.run()); }
    @Override public void onClientSetup(Runnable callback) { modBus.addListener((FMLClientSetupEvent event) -> callback.run()); }
    @Override public void onServerStarted(Consumer<Object> callback) { serverStarted = callback; }
    @Override public void onServerStopping(Consumer<Object> callback) { serverStopping = callback; }
    @Override public void onLoadComplete(Runnable callback) { modBus.addListener((FMLLoadCompleteEvent event) -> callback.run()); }
    @Override public void onRegisterCommands(Consumer<Object> callback) { registerCommands = callback; }
    @Override public void onServerTickEnd(Runnable callback) { serverTickEnd = callback; }
    @Override public void onClientTickEnd(Runnable callback) { clientTickEnd = callback; }
    @Override public void onRenderTickStart(DoubleConsumer callback) { renderTickStart = callback; }
    @Override public void onRenderTickEnd(Runnable callback) { renderTickEnd = callback; }

    private void handleServerStarted(ServerStartedEvent event) {
        if (serverStarted != null) serverStarted.accept(event.getServer());
    }
    private void handleServerStopping(ServerStoppingEvent event) {
        if (serverStopping != null) serverStopping.accept(event.getServer());
    }
    private void handleRegisterCommands(RegisterCommandsEvent event) {
        if (registerCommands != null) registerCommands.accept(event.getDispatcher());
    }
    private void handleServerTickEnd(ServerTickEvent.Post event) {
        if (serverTickEnd != null) serverTickEnd.run();
    }
    private void handleClientTickEnd(ClientTickEvent.Post event) {
        if (clientTickEnd != null) clientTickEnd.run();
    }
    private void handleRenderFrame(RenderGuiEvent.Post event) {
        long now = System.nanoTime();
        double delta = (now - lastRenderNanos) / 1.0E9;
        lastRenderNanos = now;
        if (delta <= 0.0 || delta > 0.1) delta = 0.05;
        if (renderTickStart != null) renderTickStart.accept(delta);
        if (renderTickEnd != null) renderTickEnd.run();
    }
}
