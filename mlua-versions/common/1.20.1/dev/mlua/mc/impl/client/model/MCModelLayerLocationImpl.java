package dev.mlua.mc.impl.client.model;

import dev.mlua.mcapi.client.model.MCModelLayerLocation;
import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mc.impl.core.MCResourceLocationImpl;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public class MCModelLayerLocationImpl implements MCModelLayerLocation {
    private final ModelLayerLocation inner;

    public MCModelLayerLocationImpl(ModelLayerLocation inner) {
        this.inner = inner;
    }

    public static MCModelLayerLocationImpl fromReal(ModelLayerLocation inner) {
        return inner == null ? null : new MCModelLayerLocationImpl(inner);
    }

    public ModelLayerLocation inner() {
        return inner;
    }

    @Override
    public MCResourceLocation getModel() {
        return MCResourceLocationImpl.fromReal(inner.getModel());
    }

    @Override
    public String getLayer() {
        return inner.getLayer();
    }
}
