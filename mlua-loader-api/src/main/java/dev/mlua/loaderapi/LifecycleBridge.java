package dev.mlua.loaderapi;

import java.util.function.DoubleConsumer;
import java.util.function.Consumer;

public interface LifecycleBridge {
    void onConstruct(Runnable callback);
    void onSetup(Runnable callback);
    void onClientSetup(Runnable callback);
    void onServerStarted(Consumer<Object> callback);
    void onServerStopping(Consumer<Object> callback);
    void onLoadComplete(Runnable callback);
    void onRegisterCommands(Consumer<Object> callback);
    void onServerTickEnd(Runnable callback);
    void onClientTickEnd(Runnable callback);
    void onRenderTickStart(DoubleConsumer callback);
    void onRenderTickEnd(Runnable callback);
}
