package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCProperty;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.level.block.state.properties.Property;

public class MCPropertyImpl<T extends Comparable<T>> implements MCProperty<T> {
    private final Property<T> inner;

    public MCPropertyImpl(Property<T> inner) {
        this.inner = inner;
    }

    public Property<T> inner() {
        return inner;
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public Class<T> getValueClass() {
        return inner.getValueClass();
    }

    @Override
    public Collection<T> getPossibleValues() {
        return List.copyOf(inner.getPossibleValues());
    }

    @Override
    public String getName(T value) {
        return inner.getName(value);
    }

    @Override
    public T getValue(String name) {
        return inner.getValue(name).orElse(null);
    }
}
