package dev.mlua.loaderapi;

import java.util.Objects;
import java.util.function.Consumer;

public interface ClientBridge {
    static ClientBridge unavailable() {
        return new ClientBridge() {
            private IllegalStateException unavailable() {
                return new IllegalStateException("MLua client APIs are unavailable on a dedicated server");
            }

            @Override public void registerOverlay(String id, Consumer<Object> renderer) { throw unavailable(); }
            @Override public void registerWorldRenderer(Consumer<Object> renderer) { throw unavailable(); }
            @Override public void registerKeyBinding(Object keyMapping) { throw unavailable(); }
            @Override public void registerModelLayer(Object layerLocation, Object modelPart) { throw unavailable(); }
            @Override public void registerInputHandlers() { throw unavailable(); }
        };
    }

    static void validateOverlayRegistration(String id, Consumer<Object> renderer) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Overlay id must not be blank");
        }
        validateRenderer(renderer);
    }

    static void validateRenderer(Consumer<Object> renderer) {
        Objects.requireNonNull(renderer, "renderer");
    }

    void registerOverlay(String id, Consumer<Object> renderer);
    void registerWorldRenderer(Consumer<Object> renderer);
    void registerKeyBinding(Object keyMapping);
    void registerModelLayer(Object layerLocation, Object modelPart);
    void registerInputHandlers();
}
