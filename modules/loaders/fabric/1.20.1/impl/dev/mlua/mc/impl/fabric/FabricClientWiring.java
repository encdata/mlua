package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricClientBridge;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public final class FabricClientWiring {
    private FabricClientWiring() {
    }

    public static void wire(FabricClientBridge bridge) {
        bridge.flushKeyBindings(obj -> {
            if (obj instanceof KeyMapping mapping) {
                KeyBindingHelper.registerKeyBinding(mapping);
            }
        });
        bridge.flushModelLayers((loc, def) -> {
            if (loc instanceof ModelLayerLocation layerLocation && def instanceof LayerDefinition definition) {
                EntityModelLayerRegistry.registerModelLayer(layerLocation, () -> definition);
            }
        });
    }
}
