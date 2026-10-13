package dev.mlua.mc.impl.nbt;

import dev.mlua.mcapi.nbt.MCIntTag;
import net.minecraft.nbt.IntTag;

public class MCIntTagImpl extends MCNumericTagImpl implements MCIntTag {
    private final IntTag inner;

    public MCIntTagImpl(IntTag inner) {
        super(inner);
        this.inner = inner;
    }

    public static MCIntTagImpl fromReal(IntTag inner) {
        return inner == null ? null : new MCIntTagImpl(inner);
    }

    @Override
    public IntTag inner() {
        return inner;
    }

    @Override
    public String getAsString() {
        return inner.getAsString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCIntTagImpl that)) return false;
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
