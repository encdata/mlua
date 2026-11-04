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
        //--version 26.1
        return inner.doubleValue();
        //--endversion
        //--version 1.20.1,1.21.1
        return inner.getAsDouble();
        //--endversion
    }

    @Override
    public int getAsInt() {
        //--version 26.1
        return inner.intValue();
        //--endversion
        //--version 1.20.1,1.21.1
        return inner.getAsInt();
        //--endversion
    }

    @Override
    public long getAsLong() {
        //--version 26.1
        return inner.longValue();
        //--endversion
        //--version 1.20.1,1.21.1
        return inner.getAsLong();
        //--endversion
    }

    @Override
    public String getAsString() {
        //--version 26.1
        return inner.toString();
        //--endversion
        //--version 1.20.1,1.21.1
        return inner.getAsString();
        //--endversion
    }
}
