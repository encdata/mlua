package dev.mlua.mc.impl.nbt;

import dev.mlua.mcapi.nbt.MCListTag;
import dev.mlua.mcapi.nbt.MCTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class MCListTagImpl implements MCListTag {
    private final ListTag inner;

    public MCListTagImpl(ListTag inner) {
        this.inner = inner;
    }

    public static MCListTagImpl fromReal(ListTag inner) {
        return inner == null ? null : new MCListTagImpl(inner);
    }

    public ListTag inner() {
        return inner;
    }

    @Override
    public int size() {
        return inner.size();
    }

    @Override
    public String getString(int index) {
        return inner.getString(index);
    }

    @Override
    public MCTag get(int index) {
        Tag tag = inner.get(index);
        return tag == null ? null : MCCompoundTagImpl.wrap(tag);
    }

    @Override
    public void add(MCTag tag) {
        inner.add(MCCompoundTagImpl.unwrap(tag));
    }

    @Override
    public void clear() {
        inner.clear();
    }

    @Override
    public String getAsString() {
        return inner.getAsString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCListTagImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }

    @Override
    public String toString() {
        return inner.getAsString();
    }
}
