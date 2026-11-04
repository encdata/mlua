package dev.mlua.mcapi.client.model;

public interface MCModelPart {
    void render(Object poseStack, Object vertexConsumer, int light, int overlay, float r, float g, float b, float a);
}
