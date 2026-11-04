package dev.mlua.mc.impl.client.model;

import dev.mlua.mcapi.client.model.MCModelLayers;
import net.minecraft.client.model.geom.ModelLayers;

public class MCModelLayersImpl implements MCModelLayers {
    private final ModelLayers inner;

    public MCModelLayersImpl(ModelLayers inner) {
        this.inner = inner;
    }

    public static MCModelLayersImpl fromReal(ModelLayers inner) {
        return inner == null ? null : new MCModelLayersImpl(inner);
    }

    public ModelLayers inner() {
        return inner;
    }
}
