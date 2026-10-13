package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCRenderType;
import net.minecraft.client.renderer.RenderType;

public class MCRenderTypeImpl implements MCRenderType {
    private final RenderType inner;

    public MCRenderTypeImpl(RenderType inner) {
        this.inner = inner;
    }

    public static MCRenderTypeImpl fromReal(RenderType inner) {
        return inner == null ? null : new MCRenderTypeImpl(inner);
    }

    public RenderType inner() {
        return inner;
    }

    static net.minecraft.resources.ResourceLocation resolveTexture(dev.mlua.mcapi.core.MCResourceLocation texture) {
        if (texture == null) throw new IllegalArgumentException("entity_translucent requires a texture");
        if (texture instanceof dev.mlua.mc.impl.core.MCResourceLocationImpl impl) return impl.inner();
        return (net.minecraft.resources.ResourceLocation) dev.mlua.mc.impl.core.MCResourceLocationBinding.create(
            texture.getNamespace(), texture.getPath());
    }

    public static MCRenderType solid() {
        return new MCRenderTypeImpl(RenderType.solid());
    }

    public static MCRenderType cutout() {
        return new MCRenderTypeImpl(RenderType.cutout());
    }

    public static MCRenderType cutoutMipped() {
        return new MCRenderTypeImpl(RenderType.cutoutMipped());
    }

    public static MCRenderType translucent() {
        return new MCRenderTypeImpl(RenderType.translucent());
    }

    public static MCRenderType entityTranslucent(dev.mlua.mcapi.core.MCResourceLocation texture) {
        return new MCRenderTypeImpl(RenderType.entityTranslucent(resolveTexture(texture), false));
    }
}
