package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCOverlayTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class MCOverlayTextureImpl implements MCOverlayTexture {
    private final OverlayTexture inner;

    public MCOverlayTextureImpl(OverlayTexture inner) {
        this.inner = inner;
    }

    public static MCOverlayTextureImpl fromReal(OverlayTexture inner) {
        return inner == null ? null : new MCOverlayTextureImpl(inner);
    }

    public OverlayTexture inner() {
        return inner;
    }
}
