package dev.mlua.loader.impl.fabric;

import dev.mlua.loaderapi.LifecycleBridge;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.Objects;

public final class FabricLifecycleBridge implements LifecycleBridge {
    private Runnable onConstruct;
    private Runnable onSetup;
    private Runnable onClientSetup;
    private Consumer<Object> onServerStarted;
    private Consumer<Object> onServerStopping;
    private Runnable onLoadComplete;
    private Consumer<Object> onRegisterCommands;
    private Runnable onServerTickEnd;
    private Runnable onClientTickEnd;
    private DoubleConsumer onRenderTickStart;
    private Runnable onRenderTickEnd;
    private boolean constructFired;
    private boolean setupFired;
    private boolean clientSetupFired;
    private boolean loadCompleteFired;
    private Object startedServer;

    @Override
    public void onConstruct(Runnable callback) {
        if (constructFired) {
            callback.run();
            return;
        }
        this.onConstruct = append(this.onConstruct, callback);
    }

    @Override
    public void onSetup(Runnable callback) {
        if (setupFired) {
            callback.run();
            return;
        }
        this.onSetup = append(this.onSetup, callback);
    }

    @Override
    public void onClientSetup(Runnable callback) {
        if (clientSetupFired) {
            callback.run();
            return;
        }
        this.onClientSetup = append(this.onClientSetup, callback);
    }

    @Override
    public void onServerStarted(Consumer<Object> callback) {
        if (startedServer != null) {
            callback.accept(startedServer);
            return;
        }
        this.onServerStarted = append(this.onServerStarted, callback);
    }

    @Override
    public void onServerStopping(Consumer<Object> callback) {
        this.onServerStopping = append(this.onServerStopping, callback);
    }

    @Override
    public void onLoadComplete(Runnable callback) {
        if (loadCompleteFired) {
            callback.run();
            return;
        }
        this.onLoadComplete = append(this.onLoadComplete, callback);
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
    public void onRenderTickEnd(Runnable callback) {
        this.onRenderTickEnd = append(this.onRenderTickEnd, callback);
    }

    private static Runnable append(Runnable current, Runnable next) {
        Objects.requireNonNull(next, "callback");
        if (current == null) return next;
        return () -> { current.run(); next.run(); };
    }

    private static Consumer<Object> append(Consumer<Object> current, Consumer<Object> next) {
        Objects.requireNonNull(next, "callback");
        return current == null ? next : current.andThen(next);
    }

    private static DoubleConsumer append(DoubleConsumer current, DoubleConsumer next) {
        Objects.requireNonNull(next, "callback");
        return current == null ? next : current.andThen(next);
    }

    public void fireConstruct() {
        constructFired = true;
        if (onConstruct != null) {
            onConstruct.run();
        }
    }

    public void fireSetup() {
        setupFired = true;
        if (onSetup != null) {
            onSetup.run();
        }
    }

    public void fireClientSetup() {
        clientSetupFired = true;
        if (onClientSetup != null) {
            onClientSetup.run();
        }
    }

    public void fireServerStarted(Object server) {
        startedServer = server;
        if (onServerStarted != null) {
            onServerStarted.accept(server);
        }
    }

    public void fireServerStopping(Object server) {
        if (onServerStopping != null) onServerStopping.accept(server);
        if (startedServer == server) startedServer = null;
    }

    public void fireLoadComplete() {
        loadCompleteFired = true;
        if (onLoadComplete != null) {
            onLoadComplete.run();
        }
    }

    public void fireRegisterCommands(Object dispatcher) {
        if (onRegisterCommands != null) {
            onRegisterCommands.accept(dispatcher);
        }
    }

    public void fireServerTickEnd() {
        if (onServerTickEnd != null) {
            onServerTickEnd.run();
        }
    }

    public void fireClientTickEnd() {
        if (onClientTickEnd != null) {
            onClientTickEnd.run();
        }
    }

    public void fireRenderTickStart(double deltaTime) {
        if (onRenderTickStart != null) {
            onRenderTickStart.accept(deltaTime);
        }
    }

    public void fireRenderTickEnd() {
        if (onRenderTickEnd != null) {
            onRenderTickEnd.run();
        }
    }

    public boolean hasClientLifecycle() {
        return onClientSetup != null || onClientTickEnd != null || onRenderTickStart != null || onRenderTickEnd != null;
    }
}
