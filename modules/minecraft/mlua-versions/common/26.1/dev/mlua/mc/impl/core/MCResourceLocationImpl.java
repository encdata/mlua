package dev.mlua.mc.impl.core;

import dev.mlua.mcapi.core.MCResourceLocation;
import net.minecraft.resources.Identifier;

public class MCResourceLocationImpl implements MCResourceLocation {
    private final Identifier inner;

    private MCResourceLocationImpl(Identifier inner) {
        this.inner = inner;
    }

    public static MCResourceLocationImpl fromReal(Identifier inner) {
        return new MCResourceLocationImpl(inner);
    }

    public Identifier inner() {
        return inner;
    }

    @Override public String getNamespace() { return inner.getNamespace(); }
    @Override public String getPath() { return inner.getPath(); }
    @Override public String toString() { return inner.toString(); }
    @Override public boolean equals(Object o) {
        return this == o || o instanceof MCResourceLocationImpl that && inner.equals(that.inner);
    }
    @Override public int hashCode() { return inner.hashCode(); }
}
