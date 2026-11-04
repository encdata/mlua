package dev.mlua.mc.impl.entity.attributes;

import dev.mlua.mcapi.entity.attributes.MCAttribute;
import dev.mlua.mcapi.entity.attributes.MCAttributeInstance;
import dev.mlua.mcapi.entity.attributes.MCAttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.ArrayList;
import java.util.Collection;

public class MCAttributeMapImpl implements MCAttributeMap {
    private final AttributeMap inner;

    public MCAttributeMapImpl(AttributeMap inner) {
        this.inner = inner;
    }

    public static MCAttributeMapImpl fromReal(AttributeMap inner) {
        return inner == null ? null : new MCAttributeMapImpl(inner);
    }

    @Override
    public MCAttributeInstance getInstance(MCAttribute attribute) {
        if (!(attribute instanceof MCAttributeImpl impl)) {
            throw new IllegalArgumentException("Attribute must use the active Minecraft version adapter");
        }
        AttributeInstance instance = inner.getInstance(impl.inner());
        return instance == null ? null : MCAttributeInstanceImpl.fromReal(instance);
    }

    @Override
    public Collection<MCAttributeInstance> getSyncableAttributes() {
        Collection<AttributeInstance> syncable = inner.getSyncableAttributes();
        java.util.List<MCAttributeInstance> result = new ArrayList<>(syncable.size());
        for (AttributeInstance instance : syncable) {
            result.add(MCAttributeInstanceImpl.fromReal(instance));
        }
        return result;
    }

    @Override
    public String toString() {
        return "MCAttributeMapImpl{}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCAttributeMapImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
