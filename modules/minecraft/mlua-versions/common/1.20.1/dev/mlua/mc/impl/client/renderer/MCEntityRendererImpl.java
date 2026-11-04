package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;

public class MCEntityRendererImpl implements MCEntityRenderer {
    private final EntityRenderer<?> inner;

    public MCEntityRendererImpl(EntityRenderer<?> inner) {
        this.inner = inner;
    }

    public static MCEntityRendererImpl fromReal(EntityRenderer<?> inner) {
        return inner == null ? null : new MCEntityRendererImpl(inner);
    }

    public EntityRenderer<?> inner() {
        return inner;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(Object entity, float yaw, float partialTick, Object poseStack, Object multiBufferSource, int light) {
        if (entity instanceof net.minecraft.world.entity.Entity e
                && poseStack instanceof com.mojang.blaze3d.vertex.PoseStack ps
                && multiBufferSource instanceof net.minecraft.client.renderer.MultiBufferSource mbs) {
            ((net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.Entity>) inner).render(e, yaw, partialTick, ps, mbs, light);
        }
    }
}
