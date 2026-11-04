package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricClientBridge;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public final class FabricClientWiring {
    private FabricClientWiring() {}
    public static void wire(FabricClientBridge bridge) {
        bridge.flushKeyBindings(obj -> { if (obj instanceof KeyMapping mapping) KeyMappingHelper.registerKeyMapping(mapping); });
        bridge.flushModelLayers((loc, def) -> { if (loc instanceof ModelLayerLocation layer && def instanceof LayerDefinition definition) ModelLayerRegistry.registerModelLayer(layer, () -> definition); });
    }
}
