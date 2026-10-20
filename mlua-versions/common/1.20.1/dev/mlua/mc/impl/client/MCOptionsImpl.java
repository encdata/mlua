package dev.mlua.mc.impl.client;

import dev.mlua.mcapi.client.MCKeyMapping;
import dev.mlua.mcapi.client.MCOptions;
import net.minecraft.client.Options;

import java.util.ArrayList;
import java.util.Collection;

public class MCOptionsImpl implements MCOptions {
    private final Options inner;

    public MCOptionsImpl(Options inner) {
        this.inner = inner;
    }

    public static MCOptionsImpl fromReal(Options inner) {
        return inner == null ? null : new MCOptionsImpl(inner);
    }

    public Options inner() {
        return inner;
    }

    @Override
    public int guiScale() {
        return inner.guiScale().get();
    }

    @Override
    public double getMouseSensitivity() {
        return inner.sensitivity().get();
    }

    @Override
    public int getRenderDistance() {
        return inner.renderDistance().get();
    }

    @Override
    public Collection<Object> getKeyMappings() {
        Collection<Object> result = new ArrayList<>();
        for (net.minecraft.client.KeyMapping mapping : inner.keyMappings) {
            result.add(new MCKeyMappingImpl(mapping));
        }
        return result;
    }
}
