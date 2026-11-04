package dev.mlua.mc.impl.entity.attributes;

import dev.mlua.mcapi.entity.attributes.MCAttribute;
import dev.mlua.mcapi.entity.attributes.MCAttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

public class MCAttributeInstanceImpl implements MCAttributeInstance {
    private final AttributeInstance inner;

    public MCAttributeInstanceImpl(AttributeInstance inner) {
        this.inner = inner;
    }

    public static MCAttributeInstanceImpl fromReal(AttributeInstance inner) {
        return inner == null ? null : new MCAttributeInstanceImpl(inner);
    }

    public AttributeInstance inner() {
        return inner;
    }

    @Override
    public MCAttribute getAttribute() {
        return MCAttributeImpl.fromReal(inner.getAttribute());
    }

    @Override
    public double getBaseValue() {
        return inner.getBaseValue();
    }

    @Override
    public void setBaseValue(double value) {
        inner.setBaseValue(value);
    }

    @Override
    public double getValue() {
        return inner.getValue();
    }

    @Override
    public String toString() {
        return "MCAttributeInstanceImpl{attr=" + getAttribute() + ", value=" + getValue() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCAttributeInstanceImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
