package dev.mlua.mc.impl.client.model;

import dev.mlua.mcapi.client.model.MCModelPart;
import net.minecraft.client.model.geom.ModelPart;

public class MCModelPartImpl implements MCModelPart {
    private final ModelPart inner;

    public MCModelPartImpl(ModelPart inner) {
        this.inner = inner;
    }

    public static MCModelPartImpl fromReal(ModelPart inner) {
        return inner == null ? null : new MCModelPartImpl(inner);
    }

    public ModelPart inner() {
        return inner;
    }

    @Override
    public void render(Object poseStack, Object vertexConsumer, int light, int overlay, float r, float g, float b, float a) {
        if (poseStack instanceof com.mojang.blaze3d.vertex.PoseStack ps && vertexConsumer instanceof com.mojang.blaze3d.vertex.VertexConsumer vc) {
            inner.render(ps, vc, light, overlay);
        }
    }
}
