package dev.mlua.loaderapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Ordered, reloadable callback storage behind each loader's single Minecraft overlay hook. */
public final class ClientOverlayRegistry {
    private static final System.Logger LOGGER = System.getLogger(ClientOverlayRegistry.class.getName());
    private final Map<String, Overlay> renderers = new LinkedHashMap<>();
    private volatile List<Overlay> snapshot = List.of();

    /** Adds an overlay or replaces the callback for an existing id without changing its order. */
    public synchronized void register(String id, Consumer<Object> renderer) {
        ClientBridge.validateOverlayRegistration(id, renderer);
        renderers.put(id, new Overlay(id, renderer));
        snapshot = List.copyOf(renderers.values());
    }

    /** Runs a stable snapshot so registration can safely happen while a frame is being drawn. */
    public void render(Object graphics) {
        for (Overlay overlay : snapshot) {
            try {
                overlay.renderer().accept(graphics);
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.ERROR,
                    "MLua overlay '" + overlay.id() + "' failed; continuing with later overlays", failure);
            }
        }
    }

    private record Overlay(String id, Consumer<Object> renderer) { }
}
