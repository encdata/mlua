package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCRenderType;

public class MCRenderTypeImpl implements MCRenderType {
    private final Object inner;
    public MCRenderTypeImpl(Object inner) { this.inner = inner; }
    public static MCRenderTypeImpl fromReal(Object inner) { return inner == null ? null : new MCRenderTypeImpl(inner); }
    public Object inner() { return inner; }
    public static MCRenderType solid() { return new MCRenderTypeImpl(null); }
    public static MCRenderType cutout() { return new MCRenderTypeImpl(null); }
    public static MCRenderType cutoutMipped() { return new MCRenderTypeImpl(null); }
    public static MCRenderType translucent() { return new MCRenderTypeImpl(null); }
    public static MCRenderType entityTranslucent(dev.mlua.mcapi.core.MCResourceLocation texture) { return new MCRenderTypeImpl(null); }
}
