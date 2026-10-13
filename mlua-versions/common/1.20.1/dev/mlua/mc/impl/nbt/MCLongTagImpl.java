package dev.mlua.mc.impl.nbt;

import dev.mlua.mcapi.nbt.MCLongTag;
import net.minecraft.nbt.LongTag;

public class MCLongTagImpl extends MCNumericTagImpl implements MCLongTag {
    private final LongTag inner;

    public MCLongTagImpl(LongTag inner) {
        super(inner);
        this.inner = inner;
    }

    public static MCLongTagImpl fromReal(LongTag inner) {
        return inner == null ? null : new MCLongTagImpl(inner);
    }

    @Override
    public LongTag inner() {
        return inner;
    }

    @Override
    public String getAsString() {
        return inner.getAsString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCLongTagImpl that)) return false;
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
