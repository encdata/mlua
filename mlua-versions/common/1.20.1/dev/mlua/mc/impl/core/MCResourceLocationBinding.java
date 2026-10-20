package dev.mlua.mc.impl.core;

import net.minecraft.resources.ResourceLocation;

/** 1.20.x direct resource-location constructor. */
public final class MCResourceLocationBinding {
    private MCResourceLocationBinding() { }
    public static Object create(String namespace, String path) { return new ResourceLocation(namespace, path); }
}
