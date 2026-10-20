package dev.mlua.mc.impl.core;

import dev.mlua.mcapi.core.MCVec3;
import net.minecraft.world.phys.Vec3;

public class MCVec3Impl implements MCVec3 {
    private final Vec3 inner;

    public MCVec3Impl(Vec3 inner) {
        this.inner = inner;
    }

    public static MCVec3Impl fromReal(Vec3 inner) {
        return inner == null ? null : new MCVec3Impl(inner);
    }

    @Override
    public double x() {
        return inner.x();
    }

    @Override
    public double y() {
        return inner.y();
    }

    @Override
    public double z() {
        return inner.z();
    }

    @Override
    public String toString() {
        return "MCVec3(" + x() + ", " + y() + ", " + z() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCVec3Impl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
