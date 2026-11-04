package dev.mlua.mc.impl.core;

import dev.mlua.mcapi.core.MCAABB;
import net.minecraft.world.phys.AABB;

public class MCAABBImpl implements MCAABB {
    private final AABB inner;

    public MCAABBImpl(AABB inner) {
        this.inner = inner;
    }

    public static MCAABBImpl fromReal(AABB inner) {
        return inner == null ? null : new MCAABBImpl(inner);
    }

    @Override
    public double minX() {
        return inner.minX;
    }

    @Override
    public double minY() {
        return inner.minY;
    }

    @Override
    public double minZ() {
        return inner.minZ;
    }

    @Override
    public double maxX() {
        return inner.maxX;
    }

    @Override
    public double maxY() {
        return inner.maxY;
    }

    @Override
    public double maxZ() {
        return inner.maxZ;
    }

    @Override
    public MCAABB inflate(double value) {
        return new MCAABBImpl(inner.inflate(value));
    }

    @Override
    public String toString() {
        return "MCAABB[" + minX() + ", " + minY() + ", " + minZ() + " -> " + maxX() + ", " + maxY() + ", " + maxZ() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCAABBImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
