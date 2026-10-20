package dev.mlua.mcapi.client.renderer;

public interface MCEntityRenderer {
    void render(Object entity, float yaw, float partialTick, Object poseStack, Object multiBufferSource, int light);
}
