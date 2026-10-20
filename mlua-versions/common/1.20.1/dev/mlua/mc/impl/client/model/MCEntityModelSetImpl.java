package dev.mlua.mc.impl.client.model;

import dev.mlua.mcapi.client.model.MCEntityModelSet;
import dev.mlua.mcapi.client.model.MCModelLayerLocation;
import dev.mlua.mcapi.client.model.MCModelPart;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

public class MCEntityModelSetImpl implements MCEntityModelSet {
    private final EntityModelSet inner;

    public MCEntityModelSetImpl(EntityModelSet inner) {
        this.inner = inner;
    }

    public static MCEntityModelSetImpl fromReal(EntityModelSet inner) {
        return inner == null ? null : new MCEntityModelSetImpl(inner);
    }

    public EntityModelSet inner() {
        return inner;
    }

    @Override
    public MCModelPart bakeLayer(MCModelLayerLocation layerLocation) {
        if (layerLocation instanceof MCModelLayerLocationImpl impl) {
            ModelPart part = inner.bakeLayer(impl.inner());
            return MCModelPartImpl.fromReal(part);
        }
        return null;
    }
}
