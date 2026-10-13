package dev.mlua.mc.impl.nbt;

import dev.mlua.mcapi.nbt.MCByteTag;
import net.minecraft.nbt.ByteTag;

public class MCByteTagImpl extends MCNumericTagImpl implements MCByteTag {
    private final ByteTag inner;

    public MCByteTagImpl(ByteTag inner) {
        super(inner);
        this.inner = inner;
    }

    public static MCByteTagImpl fromReal(ByteTag inner) {
        return inner == null ? null : new MCByteTagImpl(inner);
    }

    @Override
    public ByteTag inner() {
        return inner;
    }

    @Override
    public String getAsString() {
        return inner.getAsString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCByteTagImpl that)) return false;
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
