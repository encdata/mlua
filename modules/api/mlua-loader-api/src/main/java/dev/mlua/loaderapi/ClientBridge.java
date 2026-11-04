package dev.mlua.loaderapi;

import java.util.Objects;
import java.util.function.BiConsumer;
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
            @Override public void registerWorldRenderer(Consumer<ClientWorldRenderContext> renderer) { throw unavailable(); }
            @Override public void registerWorldRenderer(String stage, Consumer<ClientWorldRenderContext> renderer) { throw unavailable(); }
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

    static void validateRenderer(Consumer<?> renderer) {
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
    void registerWorldRenderer(Consumer<ClientWorldRenderContext> renderer);
    /**
     * Registers a world callback at one of the cross-loader render stages. Each loader supplies the same
     * frame-scoped context type; its native event, pose stack, source, and camera expire after the callback.
     */
    void registerWorldRenderer(String stage, Consumer<ClientWorldRenderContext> renderer);
    /**
     * Registers at the loader's supported custom-geometry submission point. The frame-scoped context exposes a
     * pose stack and active render collector/buffer source where the loader provides one. On loaders without a
     * separate geometry-submission event, the default implementation uses AFTER_ENTITIES.
     */
    default void registerWorldGeometryRenderer(Consumer<ClientWorldRenderContext> renderer) {
        registerWorldRenderer("AFTER_ENTITIES", renderer);
    }
    /**
     * Queues geometry in a deferred world-render collector when this Minecraft version uses one.
     * The consumer is invoked later by Minecraft with its captured pose and vertex consumer; it must
     * only emit vertices and must not call Lua or retain either argument.
     */
    default boolean submitCustomGeometry(Object renderSource, Object poseStack, Object renderType,
                                         BiConsumer<Object, Object> geometry) {
        return CustomGeometrySubmission.submit(renderSource, poseStack, renderType, geometry);
    }
    /** Returns whether the source accepts deferred custom geometry submissions. */
    default boolean supportsCustomGeometry(Object renderSource) {
        return CustomGeometrySubmission.supports(renderSource);
    }
    /**
     * Returns the loader/version's managed-model submission points. These names are independent of
     * WORLD_RENDER_STAGES because collector-backed renderers do not always run at a named stage.
     */
    default java.util.List<String> worldModelRenderStages() { return WORLD_RENDER_STAGES; }
    /** Returns the first managed-model submission point exposed by this loader/version. */
    default String defaultWorldModelRenderStage() {
        java.util.List<String> supported = worldModelRenderStages();
        if (supported == null || supported.isEmpty()) {
            throw new IllegalStateException("This loader/version does not expose a managed-model render stage");
        }
        return normalizeSupportedWorldModelStage(supported.get(0));
    }
    /** Registers a managed-model draw callback at one of this loader/version's model submission points. */
    default void registerWorldModelRenderer(String stage, Consumer<ClientWorldRenderContext> renderer) {
        registerWorldRenderer(normalizeSupportedWorldModelStage(stage), renderer);
    }
    /** Normalizes a managed-model stage and rejects stages this loader/version cannot draw. */
    default String normalizeSupportedWorldModelStage(String stage) {
        if (stage == null) throw new IllegalArgumentException("World model render stage must not be null");
        String normalized = stage.trim().toUpperCase(java.util.Locale.ROOT);
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
