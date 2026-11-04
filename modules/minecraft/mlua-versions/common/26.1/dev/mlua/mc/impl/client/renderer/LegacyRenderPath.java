package dev.mlua.mc.impl.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

final class LegacyRenderPath {
    private LegacyRenderPath() { }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void submitEntity(EntityRenderer renderer, Object entity, float partialTick,
                             Object poseStack, Object collector) {
        if (!(entity instanceof Entity minecraftEntity)
                || !(poseStack instanceof PoseStack pose)
                || !(collector instanceof SubmitNodeCollector submitCollector)) {
            throw new IllegalArgumentException("26.1 entity rendering requires an Entity, PoseStack, and SubmitNodeCollector");
        }

        CameraRenderState camera = Minecraft.getInstance().gameRenderer.getGameRenderState()
                .levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized) {
            throw new IllegalStateException("Entity rendering requires an active 26.1 camera render state");
        }
        EntityRenderState state = (EntityRenderState) renderer.createRenderState(minecraftEntity, partialTick);
        renderer.submit(state, pose, submitCollector, camera);
    }

    static UnsupportedOperationException unavailable(String operation) {
        return new UnsupportedOperationException(operation
                + " uses Minecraft's removed immediate rendering API; 26.1 requires render-state extraction");
    }
}
