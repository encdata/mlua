package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.IClientItemExtensions;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

public class MCClientItemExtensionsImpl implements IClientItemExtensions {
    private final net.minecraftforge.client.extensions.common.IClientItemExtensions inner;

    public MCClientItemExtensionsImpl(net.minecraftforge.client.extensions.common.IClientItemExtensions inner) {
        this.inner = inner;
    }

    public static MCClientItemExtensionsImpl fromReal(net.minecraftforge.client.extensions.common.IClientItemExtensions inner) {
        return inner == null ? null : new MCClientItemExtensionsImpl(inner);
    }

    public net.minecraftforge.client.extensions.common.IClientItemExtensions inner() {
        return inner;
    }

    @Override
    public Object getCustomRenderer() {
        BlockEntityWithoutLevelRenderer renderer = inner.getCustomRenderer();
        return MCBlockEntityWithoutLevelRendererImpl.fromReal(renderer);
    }
}
