package dev.mlua.loader.impl.forge;

import dev.mlua.loaderapi.LifecycleBridge;
import dev.mlua.loaderapi.FrameClock;
import java.util.function.DoubleConsumer;
import java.util.function.Consumer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

public class ForgeLifecycleBridge implements LifecycleBridge {
    private final IEventBus modBus;
    private Consumer<Object> onServerStarted;
    private Consumer<Object> onServerStopping;
    private volatile Object startedServer;
    private Consumer<Object> onRegisterCommands;
    private Runnable onServerTickEnd;
    private Runnable onClientTickEnd;
    private DoubleConsumer onRenderTickStart;
    private DoubleConsumer onRenderTickEnd;
    private double renderFrameDeltaTime;
    private final FrameClock renderFrameClock = new FrameClock();

    public ForgeLifecycleBridge(IEventBus modBus) {
        this.modBus = modBus;
        MinecraftForge.EVENT_BUS.addListener(this::handleServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::handleServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::handleRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::handleServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::handleClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::handleRenderTick);
    }

    @Override
    public void onConstruct(Runnable callback) {
        callback.run();
    }

    @Override
    public void onSetup(Runnable callback) {
        modBus.addListener((FMLCommonSetupEvent e) -> callback.run());
    }

    @Override
    public void onClientSetup(Runnable callback) {
        modBus.addListener((FMLClientSetupEvent e) -> callback.run());
    }

    @Override
    public synchronized void onServerStarted(Consumer<Object> callback) {
        this.onServerStarted = append(this.onServerStarted, callback);
        Object currentServer = startedServer;
        if (currentServer != null) callback.accept(currentServer);
    }

    @Override
    public void onServerStopping(Consumer<Object> callback) { this.onServerStopping = append(this.onServerStopping, callback); }

    @Override
    public void onLoadComplete(Runnable callback) {
        modBus.addListener((FMLLoadCompleteEvent e) -> callback.run());
    }

    @Override
    public void onRegisterCommands(Consumer<Object> callback) {
        this.onRegisterCommands = append(this.onRegisterCommands, callback);
    }

    @Override
    public void onServerTickEnd(Runnable callback) {
        this.onServerTickEnd = append(this.onServerTickEnd, callback);
    }

    @Override
    public void onClientTickEnd(Runnable callback) {
        this.onClientTickEnd = append(this.onClientTickEnd, callback);
    }

    @Override
    public void onRenderTickStart(DoubleConsumer callback) {
        this.onRenderTickStart = append(this.onRenderTickStart, callback);
    }

    @Override
    public void onRenderTickEnd(DoubleConsumer callback) {
        this.onRenderTickEnd = append(this.onRenderTickEnd, callback);
    }

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
        if (onServerStarted != null) {
            onServerStarted.accept(server);
        }
    }

    private synchronized void handleServerStopping(ServerStoppingEvent event) {
        Object server = event.getServer();
        if (startedServer == server) startedServer = null;
        if (onServerStopping != null) onServerStopping.accept(server);
    }

    private void handleRegisterCommands(RegisterCommandsEvent event) {
        if (onRegisterCommands != null) {
            onRegisterCommands.accept(event.getDispatcher());
        }
    }

    private void handleServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && onServerTickEnd != null) {
            onServerTickEnd.run();
        }
    }

    private void handleClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && onClientTickEnd != null) {
            onClientTickEnd.run();
        }
    }

    private void handleRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            renderFrameDeltaTime = renderFrameClock.sampleSeconds();
            if (onRenderTickStart != null) {
                onRenderTickStart.accept(renderFrameDeltaTime);
            }
        } else if (event.phase == TickEvent.Phase.END && onRenderTickEnd != null) {
            onRenderTickEnd.accept(renderFrameDeltaTime);
        }
    }
}
