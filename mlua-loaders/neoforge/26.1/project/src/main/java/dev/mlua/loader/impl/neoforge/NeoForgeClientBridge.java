package dev.mlua.loader.impl.neoforge;

import dev.mlua.loaderapi.ClientBridge;
import dev.mlua.loaderapi.ClientOverlayRegistry;
import dev.mlua.runtime.client.MLuaInputHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class NeoForgeClientBridge implements ClientBridge {
    private final IEventBus modBus;
    private final ClientOverlayRegistry overlays = new ClientOverlayRegistry();
    private final List<KeyMapping> keyMappings = new ArrayList<>();
    private final List<ModelLayerRegistration> modelLayers = new ArrayList<>();
    private boolean keysRegistered;
    private boolean layersRegistered;
    private boolean inputHandlersRegistered;

    private record ModelLayerRegistration(ModelLayerLocation location, LayerDefinition definition) { }

    public NeoForgeClientBridge(IEventBus modBus) {
        this.modBus = modBus;
        modBus.addListener(this::registerKeys);
        modBus.addListener(this::registerLayers);
        modBus.addListener(this::registerGuiLayers);
    }

    private synchronized void registerKeys(RegisterKeyMappingsEvent event) {
        keyMappings.forEach(event::register);
        keyMappings.clear();
        keysRegistered = true;
    }

    private synchronized void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (ModelLayerRegistration registration : modelLayers) {
            event.registerLayerDefinition(registration.location(), () -> registration.definition());
        }
        modelLayers.clear();
        layersRegistered = true;
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("mlua", "overlays"),
            (graphics, delta) -> overlays.render(graphics));
    }

    @Override
    public void registerOverlay(String id, Consumer<Object> renderer) {
        overlays.register(id, renderer);
    }

    @Override
    public void registerWorldRenderer(Consumer<Object> renderer) {
        registerWorldRenderer("AFTER_ENTITIES", renderer);
    }

    @Override
    public void registerWorldRenderer(String stage, Consumer<Object> renderer) {
        ClientBridge.validateRenderer(renderer);
        switch (ClientBridge.normalizeWorldRenderStage(stage)) {
            case "BEFORE_ENTITIES" -> NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterOpaqueBlocks event) -> renderer.accept(event));
            case "AFTER_ENTITIES" -> NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterOpaqueFeatures event) -> renderer.accept(event));
            case "AFTER_TRANSLUCENT" -> NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentFeatures event) -> renderer.accept(event));
            case "AFTER_LEVEL" -> NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterLevel event) -> renderer.accept(event));
            default -> throw new IllegalArgumentException("Unsupported world render stage: " + stage);
        }
    }

    @Override
    public java.util.List<String> worldModelRenderStages() {
        return java.util.List.of("AFTER_ENTITIES");
    }

    @Override
    public synchronized void registerKeyBinding(Object keyMapping) {
        if (!(keyMapping instanceof KeyMapping mapping)) throw new IllegalArgumentException("NeoForge key binding must be a Minecraft KeyMapping");
        if (keysRegistered) throw new IllegalStateException("Key mappings must be registered before RegisterKeyMappingsEvent");
        keyMappings.add(mapping);
    }

    @Override
    public synchronized void registerModelLayer(Object layerLocation, Object modelPart) {
        if (!(layerLocation instanceof ModelLayerLocation location) || !(modelPart instanceof LayerDefinition definition)) {
            throw new IllegalArgumentException("NeoForge model layers require a ModelLayerLocation and LayerDefinition");
        }
        if (layersRegistered) throw new IllegalStateException("Model layers must be registered before RegisterLayerDefinitions");
        modelLayers.add(new ModelLayerRegistration(location, definition));
    }

    @Override
    public synchronized void registerInputHandlers() {
        if (inputHandlersRegistered) return;
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseButton.Pre event) -> {
            if (MLuaInputHandler.handleMouseButton(event.getButton(), event.getAction())) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent event) -> {
            if (MLuaInputHandler.handleMouseScroll(extractScrollDelta(event))) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> MLuaInputHandler.handleClientTick());
        inputHandlersRegistered = true;
    }

    private double extractScrollDelta(InputEvent.MouseScrollingEvent event) {
        try {
            return ((Number) event.getClass().getMethod("getDeltaY").invoke(event)).doubleValue();
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            return ((Number) event.getClass().getMethod("getScrollDelta").invoke(event)).doubleValue();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("[MLua] Failed to read NeoForge mouse scroll delta", exception);
        }
    }
}
