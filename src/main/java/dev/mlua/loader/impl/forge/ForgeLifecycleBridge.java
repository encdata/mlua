package dev.mlua.loader.impl.forge;

import dev.mlua.loaderapi.LifecycleBridge;
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
    private Consumer<Object> onRegisterCommands;
    private Runnable onServerTickEnd;
    private Runnable onClientTickEnd;
    private DoubleConsumer onRenderTickStart;
    private Runnable onRenderTickEnd;
    private long lastRenderTimeNanos = System.nanoTime();

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
    public void onServerStarted(Consumer<Object> callback) {
        this.onServerStarted = callback;
    }

    @Override
    public void onServerStopping(Consumer<Object> callback) { this.onServerStopping = callback; }

    @Override
    public void onLoadComplete(Runnable callback) {
        modBus.addListener((FMLLoadCompleteEvent e) -> callback.run());
    }

    @Override
    public void onRegisterCommands(Consumer<Object> callback) {
        this.onRegisterCommands = callback;
    }

    @Override
    public void onServerTickEnd(Runnable callback) {
        this.onServerTickEnd = callback;
    }

    @Override
    public void onClientTickEnd(Runnable callback) {
        this.onClientTickEnd = callback;
    }

    @Override
    public void onRenderTickStart(DoubleConsumer callback) {
        this.onRenderTickStart = callback;
    }

    @Override
    public void onRenderTickEnd(Runnable callback) {
        this.onRenderTickEnd = callback;
    }

    private void handleServerStarted(ServerStartedEvent event) {
        if (onServerStarted != null) {
            onServerStarted.accept(event.getServer());
        }
    }

    private void handleServerStopping(ServerStoppingEvent event) {
        if (onServerStopping != null) onServerStopping.accept(event.getServer());
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
            if (onRenderTickStart != null) {
                long now = System.nanoTime();
                double deltaTime = (now - lastRenderTimeNanos) / 1.0E9;
                lastRenderTimeNanos = now;
                if (deltaTime <= 0.0 || deltaTime > 0.1) {
                    deltaTime = 0.05;
                }
                onRenderTickStart.accept(deltaTime);
            }
        } else if (event.phase == TickEvent.Phase.END && onRenderTickEnd != null) {
            onRenderTickEnd.run();
        }
    }
}
