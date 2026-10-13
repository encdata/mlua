package dev.mlua.mc.impl.resources;

import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mcapi.resources.MCResourceKey;
import dev.mlua.mc.impl.core.MCResourceLocationImpl;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class MCResourceKeyImpl<T> implements MCResourceKey<T> {
    private final ResourceKey<T> inner;

    public MCResourceKeyImpl(ResourceKey<T> inner) {
        this.inner = inner;
    }

    public static <T> MCResourceKeyImpl<T> fromReal(ResourceKey<T> inner) {
        return inner == null ? null : new MCResourceKeyImpl<>(inner);
    }

    public ResourceKey<T> inner() {
        return inner;
    }

    @Override
    public T getRegistryKey() {
        return null;
    }

    @Override
    public MCResourceLocation location() {
        return MCResourceLocationImpl.fromReal(inner.location());
    }
}
