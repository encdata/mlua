package dev.mlua.loader.impl.forge;

import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.runtime.client.MLuaGuiRenderer;
import dev.mlua.runtime.client.MLuaInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ForgeClientBridge implements ClientBridge {

    private final IEventBus modBus;
    private final Map<String, Consumer<Object>> overlays = new LinkedHashMap<>();
    private final List<KeyMapping> pendingKeyMappings = new ArrayList<>();
    private final List<ModelLayerRegistration> pendingModelLayers = new ArrayList<>();
    private boolean keyMappingsRegistered;
    private boolean modelLayersRegistered;
    private boolean inputHandlersRegistered;

    private record ModelLayerRegistration(ModelLayerLocation location, LayerDefinition definition) { }

    public ForgeClientBridge(IEventBus modBus) {
        this.modBus = modBus;
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
        modBus.addListener((RegisterGuiOverlaysEvent event) -> {
            event.registerAboveAll("mlua_gui", (gui, graphics, partialTick, screenWidth, screenHeight) -> {
                MLuaGuiRenderer.renderAll(graphics, screenWidth, screenHeight);
            });
            event.registerAboveAll("mlua_overlays", (gui, graphics, partialTick, screenWidth, screenHeight) -> {
                for (Consumer<Object> renderer : overlays.values()) {
                    renderer.accept(graphics);
                }
            });
        });
    }

    @Override
    public void registerOverlay(String id, Consumer<Object> renderer) {
        overlays.put(id, renderer);
    }

    @Override
    public void registerWorldRenderer(Consumer<Object> renderer) {
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderLevelStageEvent event) -> {
            if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                renderer.accept(event);
            }
        });
    }

    @Override
    public synchronized void registerKeyBinding(Object keyMapping) {
        if (!(keyMapping instanceof KeyMapping mapping)) {
            throw new IllegalArgumentException("Forge key binding must be a Minecraft KeyMapping");
        }
        if (keyMappingsRegistered) {
            throw new IllegalStateException("Forge key bindings must be registered before RegisterKeyMappingsEvent");
        }
        pendingKeyMappings.add(mapping);
    }

    @Override
    public synchronized void registerModelLayer(Object layerLocation, Object modelPart) {
        if (!(layerLocation instanceof ModelLayerLocation location)
            || !(modelPart instanceof LayerDefinition definition)) {
            throw new IllegalArgumentException("Forge model layers require a ModelLayerLocation and LayerDefinition");
        }
        if (modelLayersRegistered) {
            throw new IllegalStateException("Forge model layers must be registered before RegisterLayerDefinitions");
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
            if (MLuaInputHandler.handleMouseScroll(event.getScrollDelta())) {
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

}
