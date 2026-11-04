package dev.mlua.mc.impl.core;

import net.minecraft.resources.Identifier;

/** 26.x direct resource-location factory. */
public final class MCResourceLocationBinding {
    private MCResourceLocationBinding() { }
    public static Identifier create(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
