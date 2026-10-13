package dev.mlua.loaderapi;

import java.util.function.Consumer;

public interface ClientBridge {
    void registerOverlay(String id, Consumer<Object> renderer);
    void registerWorldRenderer(Consumer<Object> renderer);
    void registerKeyBinding(Object keyMapping);
    void registerModelLayer(Object layerLocation, Object modelPart);
    void registerInputHandlers();
}
