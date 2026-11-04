package dev.mlua.mc.impl.nbt;

import dev.mlua.mcapi.nbt.MCDoubleTag;
import net.minecraft.nbt.DoubleTag;

public class MCDoubleTagImpl extends MCNumericTagImpl implements MCDoubleTag {
    private final DoubleTag inner;

    public MCDoubleTagImpl(DoubleTag inner) {
        super(inner);
        this.inner = inner;
    }

    public static MCDoubleTagImpl fromReal(DoubleTag inner) {
        return inner == null ? null : new MCDoubleTagImpl(inner);
    }

    @Override
    public DoubleTag inner() {
        return inner;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCDoubleTagImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }

    @Override
    public String toString() {
        //--version 26.1
        return inner.toString();
        //--endversion
        //--version 1.20.1,1.21.1
        return inner.getAsString();
        //--endversion
    }
}
