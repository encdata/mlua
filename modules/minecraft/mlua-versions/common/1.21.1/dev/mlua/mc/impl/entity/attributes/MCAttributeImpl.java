package dev.mlua.mc.impl.entity.attributes;

import dev.mlua.mcapi.entity.attributes.MCAttribute;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

public class MCAttributeImpl implements MCAttribute {
    private final Holder<Attribute> inner;

    public MCAttributeImpl(Holder<Attribute> inner) {
        this.inner = inner;
    }

    public static MCAttributeImpl fromReal(Holder<Attribute> inner) {
        return inner == null ? null : new MCAttributeImpl(inner);
    }

    public Holder<Attribute> inner() {
        return inner;
    }

    @Override
    public String getDescriptionId() {
        return inner.value().getDescriptionId();
    }

    @Override
    public double getDefaultValue() {
        return inner.value().getDefaultValue();
    }

    @Override
    public String toString() {
        return "MCAttributeImpl{" + getDescriptionId() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCAttributeImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
