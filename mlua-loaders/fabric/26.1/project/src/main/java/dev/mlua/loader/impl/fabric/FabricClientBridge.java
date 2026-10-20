package dev.mlua.loader.impl.fabric;

import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.loaderapi.ClientOverlayRegistry;
import dev.mlua.loaderapi.ClientWorldRenderContext;
import dev.mlua.runtime.client.MLuaInputHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;

public final class FabricClientBridge implements ClientBridge {
    private final List<Object> keyBindings = new ArrayList<>();
    private final List<Object[]> modelLayers = new ArrayList<>();
    private final ClientOverlayRegistry overlays = new ClientOverlayRegistry();
    private boolean glfwRegistered;
    private boolean overlayRegistered;
    private boolean inputHandlersRegistered;

    @Override public synchronized void registerOverlay(String id, Consumer<Object> renderer) {
        overlays.register(id, renderer);
        if (!overlayRegistered) {
            overlayRegistered = true;
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mlua", "overlays"),
                (graphics, tickDelta) -> {
                    overlays.render(graphics);
                });
        }
    }

    @Override public void registerWorldRenderer(Consumer<ClientWorldRenderContext> renderer) {
        registerWorldRenderer("AFTER_ENTITIES", renderer);
    }

    @Override public void registerWorldRenderer(String stage, Consumer<ClientWorldRenderContext> renderer) {
        ClientBridge.validateRenderer(renderer);
        Consumer<net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext> scoped = event -> {
            Object source = event.submitNodeCollector() != null ? event.submitNodeCollector() : event.bufferSource();
            ClientWorldRenderContext context = new ClientWorldRenderContext(event, event.poseStack(), source,
                Minecraft.getInstance().gameRenderer.getMainCamera(), null);
            context.invoke(renderer);
        };
        String normalized = stage != null && "CUSTOM_GEOMETRY".equalsIgnoreCase(stage.trim())
            ? "CUSTOM_GEOMETRY" : ClientBridge.normalizeWorldRenderStage(stage);
        switch (normalized) {
            case "BEFORE_ENTITIES" -> LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(event -> {
                ClientWorldRenderContext context = new ClientWorldRenderContext(event, null, null,
                    Minecraft.getInstance().gameRenderer.getMainCamera(), null);
                context.invoke(renderer);
            });
            case "AFTER_ENTITIES" -> LevelRenderEvents.AFTER_SOLID_FEATURES.register(scoped::accept);
            case "AFTER_TRANSLUCENT" -> LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(scoped::accept);
            case "AFTER_LEVEL" -> LevelRenderEvents.END_MAIN.register(scoped::accept);
            case "CUSTOM_GEOMETRY" -> LevelRenderEvents.AFTER_SOLID_FEATURES.register(scoped::accept);
            default -> throw new IllegalArgumentException("Unsupported world render stage: " + stage);
        }
    }

    @Override public List<String> worldModelRenderStages() {
        return List.of("CUSTOM_GEOMETRY");
    }

    @Override public void registerKeyBinding(Object keyMapping) {
        if (!(keyMapping instanceof KeyMapping)) {
            throw new IllegalArgumentException("Fabric key binding must be a Minecraft KeyMapping");
        }
        keyBindings.add(keyMapping);
    }

    @Override public void registerModelLayer(Object layerLocation, Object modelPart) {
        if (!(layerLocation instanceof ModelLayerLocation) || !(modelPart instanceof LayerDefinition)) {
            throw new IllegalArgumentException("Fabric model layers require a ModelLayerLocation and LayerDefinition");
        }
        modelLayers.add(new Object[]{layerLocation, modelPart});
    }

    @Override public synchronized void registerInputHandlers() {
        if (inputHandlersRegistered) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> MLuaInputHandler.handleClientTick());
        registerGlfwCallbacks();
        inputHandlersRegistered = true;
    }

    private void registerGlfwCallbacks() {
        if (glfwRegistered) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            ClientTickEvents.START_CLIENT_TICK.register(client -> {
                if (!glfwRegistered && client.getWindow() != null) registerGlfwCallbacks();
            });
            return;
        }
        glfwRegistered = true;
        long window = mc.getWindow().handle();
        GLFWMouseButtonCallbackI oldMouseBtn = GLFW.glfwSetMouseButtonCallback(window, null);
        GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            boolean handled = w == window && MLuaInputHandler.handleMouseButton(button, action);
            if (!handled && oldMouseBtn != null) oldMouseBtn.invoke(w, button, action, mods);
        });
        GLFWScrollCallbackI oldScroll = GLFW.glfwSetScrollCallback(window, null);
        GLFW.glfwSetScrollCallback(window, (w, xOffset, yOffset) -> {
            boolean handled = w == window && MLuaInputHandler.handleMouseScroll(yOffset);
            if (!handled && oldScroll != null) oldScroll.invoke(w, xOffset, yOffset);
        });
    }

    public void flushKeyBindings(Consumer<Object> action) { keyBindings.forEach(action); keyBindings.clear(); }
    public void flushModelLayers(BiConsumer<Object, Object> action) { modelLayers.forEach(pair -> action.accept(pair[0], pair[1])); modelLayers.clear(); }
}
