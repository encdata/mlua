package dev.mlua.mc.impl.entity;

import dev.mlua.mcapi.entity.MCEntityType;
import net.minecraft.world.entity.EntityType;

public class MCEntityTypeImpl implements MCEntityType {
    private final EntityType<?> inner;

    public MCEntityTypeImpl(EntityType<?> inner) {
        this.inner = inner;
    }

    public static MCEntityTypeImpl fromReal(EntityType<?> inner) {
        return inner == null ? null : new MCEntityTypeImpl(inner);
    }

    public EntityType<?> inner() {
        return inner;
    }

    @Override
    public String getDescriptionId() {
        return inner.getDescriptionId();
    }

    @Override
    public String toString() {
        return "MCEntityTypeImpl{" + getDescriptionId() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCEntityTypeImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
