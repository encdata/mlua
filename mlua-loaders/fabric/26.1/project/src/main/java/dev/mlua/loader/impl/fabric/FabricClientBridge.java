package dev.mlua.loader.impl.fabric;

import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.runtime.client.MLuaInputHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;

public final class FabricClientBridge implements ClientBridge {
    private final List<Object> keyBindings = new ArrayList<>();
    private final List<Object[]> modelLayers = new ArrayList<>();
    private final Map<String, Consumer<Object>> overlays = new LinkedHashMap<>();
    private boolean glfwRegistered;
    private boolean overlayRegistered;

    @Override public void registerOverlay(String id, Consumer<Object> renderer) {
        overlays.put(id, renderer);
        if (!overlayRegistered) {
            overlayRegistered = true;
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mlua", "overlays"),
                (graphics, tickDelta) -> {
                    for (Consumer<Object> overlay : overlays.values()) {
                        overlay.accept(graphics);
                    }
                });
        }
    }

    @Override public void registerWorldRenderer(Consumer<Object> renderer) {
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> renderer.accept(context));
    }

    @Override public void registerKeyBinding(Object keyMapping) { keyBindings.add(keyMapping); }
    @Override public void registerModelLayer(Object layerLocation, Object modelPart) { modelLayers.add(new Object[]{layerLocation, modelPart}); }

    @Override public void registerInputHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> MLuaInputHandler.handleClientTick());
        registerGlfwCallbacks();
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
