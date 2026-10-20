package dev.mlua.loader.impl.neoforge;

import dev.mlua.loaderapi.LifecycleBridge;
import dev.mlua.loaderapi.FrameClock;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class NeoForgeLifecycleBridge implements LifecycleBridge {
    private final IEventBus modBus;
    private Consumer<Object> serverStarted;
    private Consumer<Object> serverStopping;
    private volatile Object startedServer;
    private Consumer<Object> registerCommands;
    private Runnable serverTickEnd;
    private Runnable clientTickEnd;
    private DoubleConsumer renderTickStart;
    private DoubleConsumer renderTickEnd;
    private double renderFrameDeltaTime;
    private final FrameClock renderFrameClock = new FrameClock();

    public NeoForgeLifecycleBridge(IEventBus modBus) {
        this.modBus = modBus;
        NeoForge.EVENT_BUS.addListener(this::handleServerStarted);
        NeoForge.EVENT_BUS.addListener(this::handleServerStopping);
        NeoForge.EVENT_BUS.addListener(this::handleRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::handleServerTickEnd);
        NeoForge.EVENT_BUS.addListener(this::handleClientTickEnd);
        NeoForge.EVENT_BUS.addListener(this::handleRenderFrameStart);
        NeoForge.EVENT_BUS.addListener(this::handleRenderFrameEnd);
    }

    @Override public void onConstruct(Runnable callback) { callback.run(); }
    @Override public void onSetup(Runnable callback) { modBus.addListener((FMLCommonSetupEvent event) -> callback.run()); }
    @Override public void onClientSetup(Runnable callback) { modBus.addListener((FMLClientSetupEvent event) -> callback.run()); }
    @Override public synchronized void onServerStarted(Consumer<Object> callback) {
        serverStarted = append(serverStarted, callback);
        Object currentServer = startedServer;
        if (currentServer != null) callback.accept(currentServer);
    }
    @Override public void onServerStopping(Consumer<Object> callback) { serverStopping = append(serverStopping, callback); }
    @Override public void onLoadComplete(Runnable callback) { modBus.addListener((FMLLoadCompleteEvent event) -> callback.run()); }
    @Override public void onRegisterCommands(Consumer<Object> callback) { registerCommands = append(registerCommands, callback); }
    @Override public void onServerTickEnd(Runnable callback) { serverTickEnd = append(serverTickEnd, callback); }
    @Override public void onClientTickEnd(Runnable callback) { clientTickEnd = append(clientTickEnd, callback); }
    @Override public void onRenderTickStart(DoubleConsumer callback) { renderTickStart = append(renderTickStart, callback); }
    @Override public void onRenderTickEnd(DoubleConsumer callback) { renderTickEnd = append(renderTickEnd, callback); }

    private static Runnable append(Runnable current, Runnable next) {
        return dev.mlua.loaderapi.MLuaListenerChain.append(current, next);
    }

    private static Consumer<Object> append(Consumer<Object> current, Consumer<Object> next) {
        return dev.mlua.loaderapi.MLuaListenerChain.append(current, next);
    }

    private static DoubleConsumer append(DoubleConsumer current, DoubleConsumer next) {
        return dev.mlua.loaderapi.MLuaListenerChain.append(current, next);
    }

    private synchronized void handleServerStarted(ServerStartedEvent event) {
        Object server = event.getServer();
        startedServer = server;
        if (serverStarted != null) serverStarted.accept(server);
    }
    private synchronized void handleServerStopping(ServerStoppingEvent event) {
        Object server = event.getServer();
        if (startedServer == server) startedServer = null;
        if (serverStopping != null) serverStopping.accept(server);
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
    private void handleRenderFrameStart(RenderFrameEvent.Pre event) {
        renderFrameDeltaTime = renderFrameClock.sampleSeconds();
        if (renderTickStart != null) renderTickStart.accept(renderFrameDeltaTime);
    }
    private void handleRenderFrameEnd(RenderFrameEvent.Post event) {
        if (renderTickEnd != null) renderTickEnd.accept(renderFrameDeltaTime);
    }
}
