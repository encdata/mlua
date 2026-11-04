package dev.mlua.mc.impl.entity;

import dev.mlua.mc.impl.core.MCAABBImpl;
import dev.mlua.mcapi.core.MCAABB;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCEntityDimensions;
import net.minecraft.world.entity.EntityDimensions;

public class MCEntityDimensionsImpl implements MCEntityDimensions {
    private final EntityDimensions inner;

    public MCEntityDimensionsImpl(EntityDimensions inner) {
        this.inner = inner;
    }

    public static MCEntityDimensionsImpl fromReal(EntityDimensions inner) {
        return inner == null ? null : new MCEntityDimensionsImpl(inner);
    }

    @Override
    public float width() {
        return inner.width();
    }

    @Override
    public float height() {
        return inner.height();
    }

    @Override
    public MCAABB makeBoundingBox(MCVec3 pos) {
        return MCAABBImpl.fromReal(inner.makeBoundingBox(pos.x(), pos.y(), pos.z()));
    }

    @Override
    public String toString() {
        return "MCEntityDimensionsImpl{width=" + width() + ", height=" + height() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCEntityDimensionsImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
