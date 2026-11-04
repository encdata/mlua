package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;

public class MCMobRendererImpl implements MCMobRenderer {
    private final MobRenderer<?, ?> inner;

    public MCMobRendererImpl(MobRenderer<?, ?> inner) {
        this.inner = inner;
    }

    public static MCMobRendererImpl fromReal(MobRenderer<?, ?> inner) {
        return inner == null ? null : new MCMobRendererImpl(inner);
    }

    public MobRenderer<?, ?> inner() {
        return inner;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(Object entity, float yaw, float partialTick, Object poseStack, Object multiBufferSource, int light) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity e
                && poseStack instanceof com.mojang.blaze3d.vertex.PoseStack ps
                && multiBufferSource instanceof net.minecraft.client.renderer.MultiBufferSource mbs) {
            ((net.minecraft.client.renderer.entity.MobRenderer) inner).render(e, yaw, partialTick, ps, mbs, light);
        }
    }
}
