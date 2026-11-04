package dev.mlua.mc.impl.core;

import dev.mlua.mcapi.core.MCBlockPos;
import net.minecraft.core.BlockPos;

public class MCBlockPosImpl implements MCBlockPos {
    private final BlockPos inner;

    public MCBlockPosImpl(BlockPos inner) {
        this.inner = inner;
    }

    public net.minecraft.core.BlockPos inner() {
        return inner;
    }

    public static MCBlockPosImpl fromReal(BlockPos inner) {
        return inner == null ? null : new MCBlockPosImpl(inner);
    }

    @Override
    public int getX() {
        return inner.getX();
    }

    @Override
    public int getY() {
        return inner.getY();
    }

    @Override
    public int getZ() {
        return inner.getZ();
    }

    @Override
    public MCBlockPos immutable() {
        return new MCBlockPosImpl(inner.immutable());
    }

    @Override
    public MCBlockPos below() {
        return new MCBlockPosImpl(inner.below());
    }

    @Override
    public MCBlockPos above() {
        return new MCBlockPosImpl(inner.above());
    }

    @Override
    public MCBlockPos offset(int dx, int dy, int dz) {
        return new MCBlockPosImpl(inner.offset(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCBlockPosImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }

    @Override
    public String toString() {
        return "MCBlockPosImpl{x=" + getX() + ", y=" + getY() + ", z=" + getZ() + "}";
    }
}
