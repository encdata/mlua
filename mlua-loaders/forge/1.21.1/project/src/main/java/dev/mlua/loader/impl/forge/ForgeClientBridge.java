package dev.mlua.loader.impl.forge;

import dev.mlua.core.MLuaLogger;
import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.runtime.client.MLuaGuiRenderer;
import dev.mlua.runtime.client.MLuaInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ForgeClientBridge implements ClientBridge {
    private static final String ADD_GUI_OVERLAY_LAYERS_EVENT = "net.minecraftforge.client.event.AddGuiOverlayLayersEvent";
    private static final String REGISTER_GUI_OVERLAYS_EVENT = "net.minecraftforge.client.event.RegisterGuiOverlaysEvent";
    private static final ResourceLocation MLUA_GUI = ResourceLocation.fromNamespaceAndPath("mlua", "gui");
    private static final ResourceLocation MLUA_OVERLAYS = ResourceLocation.fromNamespaceAndPath("mlua", "overlays");

    private final Map<String, Consumer<Object>> overlays = new LinkedHashMap<>();
    private final List<KeyMapping> pendingKeyMappings = new ArrayList<>();
    private final List<ModelLayerRegistration> pendingModelLayers = new ArrayList<>();
    private boolean keyMappingsRegistered;
    private boolean modelLayersRegistered;
    private boolean inputHandlersRegistered;

    private record ModelLayerRegistration(ModelLayerLocation location, LayerDefinition definition) { }

    public ForgeClientBridge(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            synchronized (this) {
                for (KeyMapping mapping : pendingKeyMappings) event.register(mapping);
                pendingKeyMappings.clear();
                keyMappingsRegistered = true;
            }
        });
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> {
            synchronized (this) {
                for (ModelLayerRegistration layer : pendingModelLayers) {
                    event.registerLayerDefinition(layer.location(), () -> layer.definition());
                }
                pendingModelLayers.clear();
                modelLayersRegistered = true;
            }
        });
        registerOverlayHooks(modBus);
    }

    @Override
    public void registerOverlay(String id, Consumer<Object> renderer) {
        ClientBridge.validateOverlayRegistration(id, renderer);
        overlays.put(id, renderer);
    }

    @Override
    public void registerWorldRenderer(Consumer<Object> renderer) {
        ClientBridge.validateRenderer(renderer);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderLevelStageEvent event) -> {
            if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                renderer.accept(event);
            }
        });
    }

    @Override
    public synchronized void registerKeyBinding(Object keyMapping) {
        if (!(keyMapping instanceof KeyMapping mapping)) {
            throw new IllegalArgumentException("Minecraft key binding must be a KeyMapping");
        }
        if (keyMappingsRegistered) {
            throw new IllegalStateException("Key bindings must be registered before RegisterKeyMappingsEvent");
        }
        pendingKeyMappings.add(mapping);
    }

    @Override
    public synchronized void registerModelLayer(Object layerLocation, Object modelPart) {
        if (!(layerLocation instanceof ModelLayerLocation location)
            || !(modelPart instanceof LayerDefinition definition)) {
            throw new IllegalArgumentException("Model layers require a ModelLayerLocation and LayerDefinition");
        }
        if (modelLayersRegistered) {
            throw new IllegalStateException("Model layers must be registered before RegisterLayerDefinitions");
        }
        pendingModelLayers.add(new ModelLayerRegistration(location, definition));
    }

    @Override
    public synchronized void registerInputHandlers() {
        if (inputHandlersRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre event) -> {
            if (MLuaInputHandler.handleMouseButton(event.getButton(), event.getAction())) {
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent event) -> {
            if (MLuaInputHandler.handleMouseScroll(extractScrollDelta(event))) {
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                MLuaInputHandler.handleClientTick();
            }
        });
        inputHandlersRegistered = true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerOverlayHooks(IEventBus modBus) {
        if (tryRegisterOverlayListener(modBus, ADD_GUI_OVERLAY_LAYERS_EVENT, this::handleAddGuiOverlayLayers)) {
            return;
        }
        if (tryRegisterOverlayListener(modBus, REGISTER_GUI_OVERLAYS_EVENT, this::handleRegisterGuiOverlays)) {
            return;
        }
        MLuaLogger.LOGGER.warn("[MLua] No supported Forge overlay registration event found; client overlays are disabled");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean tryRegisterOverlayListener(IEventBus modBus, String eventClassName, Consumer<Object> listener) {
        try {
            Class<?> eventClass = Class.forName(eventClassName, false, modBus.getClass().getClassLoader());
            modBus.addListener(EventPriority.NORMAL, false, (Class) eventClass, (Consumer) listener);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable throwable) {
            MLuaLogger.LOGGER.warn("[MLua] Failed to register Forge overlay listener for {}", eventClassName, throwable);
            return false;
        }
    }

    private void handleAddGuiOverlayLayers(Object event) {
        try {
            Object layeredDraw = event.getClass().getMethod("getLayeredDraw").invoke(event);
            Method addMethod = layeredDraw.getClass().getMethod("add", ResourceLocation.class, LayeredDraw.Layer.class);
            addMethod.invoke(layeredDraw, MLUA_GUI, (LayeredDraw.Layer) (graphics, deltaTracker) -> {
                Minecraft minecraft = Minecraft.getInstance();
                MLuaGuiRenderer.renderAll(
                    graphics,
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight()
                );
            });
            addMethod.invoke(layeredDraw, MLUA_OVERLAYS, (LayeredDraw.Layer) (graphics, deltaTracker) -> {
                for (Consumer<Object> renderer : overlays.values()) {
                    renderer.accept(graphics);
                }
            });
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("[MLua] Failed to register AddGuiOverlayLayersEvent overlays", exception);
        }
    }

    private void handleRegisterGuiOverlays(Object event) {
        try {
            Method registerAboveAll = findRegisterAboveAllMethod(event.getClass());
            registerAboveAll.invoke(event, "mlua_gui", createLegacyOverlay(registerAboveAll.getParameterTypes()[1], true));
            registerAboveAll.invoke(event, "mlua_overlays", createLegacyOverlay(registerAboveAll.getParameterTypes()[1], false));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("[MLua] Failed to register RegisterGuiOverlaysEvent overlays", exception);
        }
    }

    private Method findRegisterAboveAllMethod(Class<?> eventClass) {
        for (Method method : eventClass.getMethods()) {
            if (method.getName().equals("registerAboveAll") && method.getParameterCount() == 2) {
                return method;
            }
        }
        throw new IllegalStateException("[MLua] registerAboveAll(String, overlay) method not found on " + eventClass.getName());
    }

    private Object createLegacyOverlay(Class<?> overlayInterface, boolean guiOverlay) {
        return Proxy.newProxyInstance(
            overlayInterface.getClassLoader(),
            new Class<?>[]{overlayInterface},
            (proxy, method, args) -> {
                if (!method.getName().equals("render") || args == null || args.length < 5 || !(args[1] instanceof GuiGraphics graphics)) {
                    return null;
                }

                if (guiOverlay) {
                    MLuaGuiRenderer.renderAll(graphics, (Integer) args[3], (Integer) args[4]);
                } else {
                    for (Consumer<Object> renderer : overlays.values()) {
                        renderer.accept(graphics);
                    }
                }
                return null;
            }
        );
    }

    private double extractScrollDelta(InputEvent.MouseScrollingEvent event) {
        try {
            return ((Number) event.getClass().getMethod("getDeltaY").invoke(event)).doubleValue();
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            return ((Number) event.getClass().getMethod("getScrollDelta").invoke(event)).doubleValue();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("[MLua] Failed to read Forge mouse scroll delta", exception);
        }
    }
}
