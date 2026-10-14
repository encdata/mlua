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
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;

public final class FabricClientBridge implements ClientBridge {
    private final List<Object> keyBindings = new ArrayList<>();
    private final List<Object[]> modelLayers = new ArrayList<>();
    private final Map<String, Consumer<Object>> overlays = new LinkedHashMap<>();
    private boolean glfwRegistered = false;
    private boolean overlayRegistered;

    @Override
    public void registerOverlay(String id, Consumer<Object> renderer) {
        overlays.put(id, renderer);
        if (!overlayRegistered) {
            overlayRegistered = true;
            HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
                for (Consumer<Object> overlay : overlays.values()) {
                    overlay.accept(drawContext);
                }
            });
        }
    }

    @Override
    public void registerWorldRenderer(Consumer<Object> renderer) {
        WorldRenderEvents.AFTER_ENTITIES.register(renderer::accept);
    }

    @Override
    public void registerKeyBinding(Object keyMapping) {
        keyBindings.add(keyMapping);
    }

    @Override
    public void registerModelLayer(Object layerLocation, Object modelPart) {
        modelLayers.add(new Object[]{layerLocation, modelPart});
    }

    @Override
    public void registerInputHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> MLuaInputHandler.handleClientTick());
        registerGlfwCallbacks();
    }

    private void registerGlfwCallbacks() {
        if (glfwRegistered) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            ClientTickEvents.START_CLIENT_TICK.register(client -> {
                if (!glfwRegistered && client.getWindow() != null) {
                    registerGlfwCallbacks();
                }
            });
            return;
        }
        glfwRegistered = true;
        long window = mc.getWindow().getWindow();

        GLFWMouseButtonCallbackI oldMouseBtn = GLFW.glfwSetMouseButtonCallback(window, null);
        GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            boolean handled = false;
            if (w == window) {
                handled = MLuaInputHandler.handleMouseButton(button, action);
            }
            if (!handled && oldMouseBtn != null) {
                oldMouseBtn.invoke(w, button, action, mods);
            }
        });

        GLFWScrollCallbackI oldScroll = GLFW.glfwSetScrollCallback(window, null);
        GLFW.glfwSetScrollCallback(window, (w, xOffset, yOffset) -> {
            boolean handled = false;
            if (w == window) {
                handled = MLuaInputHandler.handleMouseScroll(yOffset);
            }
            if (!handled && oldScroll != null) {
                oldScroll.invoke(w, xOffset, yOffset);
            }
        });
    }

    public void flushKeyBindings(Consumer<Object> action) {
        for (Object kb : keyBindings) {
            action.accept(kb);
        }
        keyBindings.clear();
    }

    public void flushModelLayers(BiConsumer<Object, Object> action) {
        for (Object[] pair : modelLayers) {
            action.accept(pair[0], pair[1]);
        }
        modelLayers.clear();
    }
}
