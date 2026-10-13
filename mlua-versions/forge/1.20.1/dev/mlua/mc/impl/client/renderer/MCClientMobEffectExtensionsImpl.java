package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.IClientMobEffectExtensions;

public class MCClientMobEffectExtensionsImpl implements IClientMobEffectExtensions {
    private final net.minecraftforge.client.extensions.common.IClientMobEffectExtensions inner;

    public MCClientMobEffectExtensionsImpl(net.minecraftforge.client.extensions.common.IClientMobEffectExtensions inner) {
        this.inner = inner;
    }

    public static MCClientMobEffectExtensionsImpl fromReal(net.minecraftforge.client.extensions.common.IClientMobEffectExtensions inner) {
        return inner == null ? null : new MCClientMobEffectExtensionsImpl(inner);
    }

    public net.minecraftforge.client.extensions.common.IClientMobEffectExtensions inner() {
        return inner;
    }

    @Override
    public boolean renderInventoryEffect(Object mobEffectInstance, Object effectRenderingInventoryScreen,
                                          Object poseStack, int x, int y, int blitOffset) {
        return false;
    }
}
