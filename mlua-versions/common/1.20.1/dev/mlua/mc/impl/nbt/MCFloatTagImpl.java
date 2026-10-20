package dev.mlua.mc.impl.nbt;

import dev.mlua.mcapi.nbt.MCFloatTag;
import net.minecraft.nbt.FloatTag;

public class MCFloatTagImpl extends MCNumericTagImpl implements MCFloatTag {
    private final FloatTag inner;

    public MCFloatTagImpl(FloatTag inner) {
        super(inner);
        this.inner = inner;
    }

    public static MCFloatTagImpl fromReal(FloatTag inner) {
        return inner == null ? null : new MCFloatTagImpl(inner);
    }

    @Override
    public FloatTag inner() {
        return inner;
    }

    @Override
    public float floatValue() {
        return inner.getAsFloat();
    }

    @Override
    public String getAsString() {
        return inner.getAsString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCFloatTagImpl that)) return false;
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
