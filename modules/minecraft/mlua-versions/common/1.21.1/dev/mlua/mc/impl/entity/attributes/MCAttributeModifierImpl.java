package dev.mlua.mc.impl.entity.attributes;

import dev.mlua.mcapi.entity.attributes.MCAttributeModifier;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class MCAttributeModifierImpl implements MCAttributeModifier {
    private final AttributeModifier inner;

    public MCAttributeModifierImpl(AttributeModifier inner) {
        this.inner = inner;
    }

    public static MCAttributeModifierImpl fromReal(AttributeModifier inner) {
        return inner == null ? null : new MCAttributeModifierImpl(inner);
    }

    public AttributeModifier inner() {
        return inner;
    }

    @Override
    public UUID getId() {
        return UUID.nameUUIDFromBytes(inner.id().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public String getName() {
        return inner.id().toString();
    }

    @Override
    public double getAmount() {
        return inner.amount();
    }

    @Override
    public int getOperation() {
        return inner.operation().ordinal();
    }

    @Override
    public String toString() {
        return "MCAttributeModifierImpl{name=" + getName() + ", amount=" + getAmount() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCAttributeModifierImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
