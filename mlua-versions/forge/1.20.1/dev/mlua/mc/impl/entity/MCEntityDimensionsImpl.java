package dev.mlua.mc.impl.entity;

import dev.mlua.mcapi.core.MCAABB;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCEntityDimensions;
import dev.mlua.mc.impl.core.MCAABBImpl;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        return inner.width;
    }

    @Override
    public float height() {
        return inner.height;
    }

    @Override
    public MCAABB makeBoundingBox(MCVec3 pos) {
        AABB bb = new AABB(
            pos.x() - inner.width / 2.0, pos.y(), pos.z() - inner.width / 2.0,
            pos.x() + inner.width / 2.0, pos.y() + inner.height, pos.z() + inner.width / 2.0
        );
        return MCAABBImpl.fromReal(bb);
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
