package dev.mlua.loader.impl.forge;

import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.runtime.client.MLuaGuiRenderer;
import dev.mlua.runtime.client.MLuaInputHandler;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;

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
        this.modBus = modBus;
        modBus.addListener((RegisterGuiLayersEvent event) -> {
            ResourceLocation guiLayerId = MLuaResourceLocations.build("mlua", "gui");
            ResourceLocation overlaysLayerId = MLuaResourceLocations.build("mlua", "overlays");
            event.registerAboveAll(guiLayerId, (graphics, partialTick) -> {
                int screenWidth = graphics.guiWidth();
                int screenHeight = graphics.guiHeight();
                MLuaGuiRenderer.renderAll(graphics, screenWidth, screenHeight);
            });
            event.registerAboveAll(overlaysLayerId, (graphics, partialTick) -> {
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
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderLevelStageEvent event) -> {
            if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
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
    public void registerInputHandlers() {
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre event) -> {
            if (MLuaInputHandler.handleMouseButton(event.getButton(), event.getAction())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent event) -> {
            if (MLuaInputHandler.handleMouseScroll(event.getScrollDeltaY())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            MLuaInputHandler.handleClientTick();
        });
    }

}
