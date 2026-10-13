package dev.mlua.mc.impl.nbt;

import dev.mlua.mcapi.nbt.MCNumericTag;

public abstract class MCNumericTagImpl implements MCNumericTag {
    protected final net.minecraft.nbt.NumericTag inner;

    protected MCNumericTagImpl(net.minecraft.nbt.NumericTag inner) {
        this.inner = inner;
    }

    public net.minecraft.nbt.NumericTag inner() {
        return inner;
    }

    @Override
    public double getAsDouble() {
        return inner.getAsDouble();
    }

    @Override
    public int getAsInt() {
        return inner.getAsInt();
    }

    @Override
    public long getAsLong() {
        return inner.getAsLong();
    }

    @Override
    public String getAsString() {
        return inner.getAsString();
    }
}
