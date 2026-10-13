package dev.mlua.mc.impl.nbt;

import dev.mlua.mcapi.nbt.MCStringTag;
import net.minecraft.nbt.StringTag;

public class MCStringTagImpl implements MCStringTag {
    private final StringTag inner;

    public MCStringTagImpl(StringTag inner) {
        this.inner = inner;
    }

    public static MCStringTagImpl fromReal(StringTag inner) {
        return inner == null ? null : new MCStringTagImpl(inner);
    }

    public StringTag inner() {
        return inner;
    }

    @Override
    public String getAsString() {
        return inner.getAsString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCStringTagImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }

    @Override
    public String toString() {
        return inner.getAsString();
    }
}
