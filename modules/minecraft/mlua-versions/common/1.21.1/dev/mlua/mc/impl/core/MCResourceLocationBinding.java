package dev.mlua.mc.impl.core;

import net.minecraft.resources.ResourceLocation;

/** 1.21.x direct resource-location factory. */
public final class MCResourceLocationBinding {
    private MCResourceLocationBinding() { }
    public static Object create(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }
}
