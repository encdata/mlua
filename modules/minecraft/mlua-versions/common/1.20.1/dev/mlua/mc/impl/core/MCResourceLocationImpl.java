package dev.mlua.mc.impl.core;

import dev.mlua.mcapi.core.MCResourceLocation;
import net.minecraft.resources.ResourceLocation;

public class MCResourceLocationImpl implements MCResourceLocation {
    private final ResourceLocation inner;

    private MCResourceLocationImpl(ResourceLocation inner) {
        this.inner = inner;
    }

    public static MCResourceLocationImpl fromReal(ResourceLocation inner) {
        return new MCResourceLocationImpl(inner);
    }

    public ResourceLocation inner() {
        return inner;
    }

    @Override
    public String getNamespace() {
        return inner.getNamespace();
    }

    @Override
    public String getPath() {
        return inner.getPath();
    }

    @Override
    public String toString() {
        return inner.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCResourceLocationImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
