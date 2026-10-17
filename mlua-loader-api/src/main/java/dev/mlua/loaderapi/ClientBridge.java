package dev.mlua.loaderapi;

import java.util.Objects;
import java.util.function.Consumer;

public interface ClientBridge {
    java.util.List<String> WORLD_RENDER_STAGES = java.util.List.of(
        "BEFORE_ENTITIES", "AFTER_ENTITIES", "AFTER_TRANSLUCENT", "AFTER_LEVEL");

    static ClientBridge unavailable() {
        return new ClientBridge() {
            private IllegalStateException unavailable() {
                return new IllegalStateException("MLua client APIs are unavailable on a dedicated server");
            }

            @Override public void registerOverlay(String id, Consumer<Object> renderer) { throw unavailable(); }
            @Override public void registerWorldRenderer(Consumer<Object> renderer) { throw unavailable(); }
            @Override public void registerWorldRenderer(String stage, Consumer<Object> renderer) { throw unavailable(); }
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

    static String normalizeWorldRenderStage(String stage) {
        if (stage == null) throw new IllegalArgumentException("World render stage must not be null");
        String normalized = stage.trim().toUpperCase(java.util.Locale.ROOT);
        for (String supported : WORLD_RENDER_STAGES) {
            if (supported.equals(normalized)) return normalized;
        }
        throw new IllegalArgumentException("Unknown world render stage '" + stage + "'. Supported stages: "
            + String.join(", ", WORLD_RENDER_STAGES));
    }

    /**
     * Registers an ordered overlay callback. Re-registering an id replaces its callback in place.
     * Implementations keep a stable render snapshot so callbacks may be registered during client play.
     */
    void registerOverlay(String id, Consumer<Object> renderer);
    void registerWorldRenderer(Consumer<Object> renderer);
    /** Registers a world callback at one of the cross-loader render stages. */
    void registerWorldRenderer(String stage, Consumer<Object> renderer);
    /** Returns stages where managed Mesh and BBModel objects can be drawn by this loader/version. */
    default java.util.List<String> worldModelRenderStages() { return WORLD_RENDER_STAGES; }
    /** Normalizes a managed-model stage and rejects stages this loader/version cannot draw. */
    default String normalizeSupportedWorldModelStage(String stage) {
        String normalized = normalizeWorldRenderStage(stage);
        java.util.List<String> supported = worldModelRenderStages();
        if (supported == null || !supported.contains(normalized)) {
            throw new IllegalArgumentException("WorldModel render stage '" + normalized
                + "' is unavailable on this loader/version. Supported stages: "
                + (supported == null || supported.isEmpty() ? "none" : String.join(", ", supported)));
        }
        return normalized;
    }
    void registerKeyBinding(Object keyMapping);
    void registerModelLayer(Object layerLocation, Object modelPart);
    void registerInputHandlers();
}
