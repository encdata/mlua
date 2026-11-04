package dev.mlua.mcapi.client.renderer;

public interface IClientMobEffectExtensions {
    boolean renderInventoryEffect(Object mobEffectInstance, Object effectRenderingInventoryScreen,
                                  Object poseStack, int x, int y, int blitOffset);
}
