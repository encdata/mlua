package dev.mlua.mc.impl.entity.attributes;

import dev.mlua.mcapi.entity.attributes.MCAttribute;
import dev.mlua.mcapi.entity.attributes.MCAttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

public class MCAttributeSupplierImpl implements MCAttributeSupplier {
    private final AttributeSupplier inner;

    public MCAttributeSupplierImpl(AttributeSupplier inner) {
        this.inner = inner;
    }

    public static MCAttributeSupplierImpl fromReal(AttributeSupplier inner) {
        return inner == null ? null : new MCAttributeSupplierImpl(inner);
    }

    @Override
    public double get(MCAttribute attribute) {
        if (!(attribute instanceof MCAttributeImpl impl)) {
            throw new IllegalArgumentException("Attribute must use the active Minecraft version adapter");
        }
        return inner.getValue(impl.inner());
    }

    @Override
    public String toString() {
        return "MCAttributeSupplierImpl{}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCAttributeSupplierImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
