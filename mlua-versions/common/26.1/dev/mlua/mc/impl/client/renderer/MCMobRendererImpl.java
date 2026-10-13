package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;

public class MCMobRendererImpl implements MCMobRenderer {
    private final MobRenderer<?, ?, ?> inner;
    public MCMobRendererImpl(MobRenderer<?, ?, ?> inner) { this.inner = inner; }
    public static MCMobRendererImpl fromReal(MobRenderer<?, ?, ?> inner) { return inner == null ? null : new MCMobRendererImpl(inner); }
    public MobRenderer<?, ?, ?> inner() { return inner; }
    @Override public void render(Object entity, float yaw, float partialTick, Object poseStack, Object multiBufferSource, int light) {
        LegacyRenderPath.submitEntity(inner, entity, partialTick, poseStack, multiBufferSource);
    }
}
