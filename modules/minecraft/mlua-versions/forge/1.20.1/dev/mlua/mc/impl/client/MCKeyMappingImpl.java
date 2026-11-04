package dev.mlua.mc.impl.client;

import dev.mlua.mcapi.client.MCKeyMapping;
import net.minecraft.client.KeyMapping;

public class MCKeyMappingImpl implements MCKeyMapping {
    private final KeyMapping inner;

    public MCKeyMappingImpl(KeyMapping inner) {
        this.inner = inner;
    }

    public static MCKeyMappingImpl fromReal(KeyMapping inner) {
        return inner == null ? null : new MCKeyMappingImpl(inner);
    }

    public KeyMapping inner() {
        return inner;
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public String getCategory() {
        return inner.getCategory();
    }

    @Override
    public boolean isDown() {
        return inner.isDown();
    }

    @Override
    public boolean consumeClick() {
        return inner.consumeClick();
    }

    @Override
    public int getKeyCode() {
        return inner.getKey().getValue();
    }
}
