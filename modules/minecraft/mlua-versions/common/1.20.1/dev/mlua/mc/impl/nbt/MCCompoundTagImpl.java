package dev.mlua.mc.impl.nbt;

import dev.mlua.mcapi.nbt.MCCompoundTag;
import dev.mlua.mcapi.nbt.MCListTag;
import dev.mlua.mcapi.nbt.MCTag;
import java.util.Set;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public class MCCompoundTagImpl implements MCCompoundTag {
    private final CompoundTag inner;

    public MCCompoundTagImpl(CompoundTag inner) {
        this.inner = inner;
    }

    public static MCCompoundTagImpl fromReal(CompoundTag inner) {
        return inner == null ? null : new MCCompoundTagImpl(inner);
    }

    public CompoundTag inner() {
        return inner;
    }

    @Override
    public Set<String> getAllKeys() {
        return MCNbtAccess.keys(inner);
    }

    @Override
    public MCTag get(String key) {
        Tag tag = inner.get(key);
        return tag == null ? null : wrap(tag);
    }

    @Override
    public String getString(String key) {
        return MCNbtAccess.string(inner, key);
    }

    @Override
    public void putString(String key, String value) {
        inner.putString(key, value);
    }

    @Override
    public void remove(String key) {
        inner.remove(key);
    }

    @Override
    public boolean contains(String key) {
        return inner.contains(key);
    }

    @Override
    public boolean contains(String key, int type) {
        return MCNbtAccess.contains(inner, key, type);
    }

    @Override
    public MCCompoundTag getCompound(String key) {
        CompoundTag tag = MCNbtAccess.compound(inner, key);
        return tag == null ? null : new MCCompoundTagImpl(tag);
    }

    @Override
    public MCListTag getList(String key, int type) {
        ListTag tag = MCNbtAccess.list(inner, key, type);
        return tag == null ? null : new MCListTagImpl(tag);
    }

    @Override
    public int getInt(String key) {
        return MCNbtAccess.integer(inner, key);
    }

    @Override
    public long getLong(String key) {
        return MCNbtAccess.longValue(inner, key);
    }

    @Override
    public double getDouble(String key) {
        return MCNbtAccess.decimal(inner, key);
    }

    @Override
    public void putInt(String key, int value) {
        inner.putInt(key, value);
    }

    @Override
    public void putLong(String key, long value) {
        inner.putLong(key, value);
    }

    @Override
    public void putDouble(String key, double value) {
        inner.putDouble(key, value);
    }

    @Override
    public void put(String key, MCTag tag) {
        inner.put(key, unwrap(tag));
    }

    @Override
    public String getAsString() {
        //--version 26.1
        return inner.toString();
        //--endversion
        //--version 1.20.1,1.21.1
        return inner.getAsString();
        //--endversion
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCCompoundTagImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }

    @Override
    public String toString() {
        //--version 26.1
        return inner.toString();
        //--endversion
        //--version 1.20.1,1.21.1
        return inner.getAsString();
        //--endversion
    }

    static Tag unwrap(MCTag tag) {
        if (tag instanceof MCCompoundTagImpl c) return c.inner();
        if (tag instanceof MCListTagImpl l) return l.inner();
        if (tag instanceof MCByteTagImpl b) return b.inner();
        if (tag instanceof MCDoubleTagImpl d) return d.inner();
        if (tag instanceof MCFloatTagImpl f) return f.inner();
        if (tag instanceof MCIntTagImpl i) return i.inner();
        if (tag instanceof MCLongTagImpl l) return l.inner();
        if (tag instanceof MCStringTagImpl s) return s.inner();
        if (tag instanceof MCNumericTagImpl n) return n.inner();
        throw new IllegalArgumentException("Unknown MCTag type: " + tag.getClass());
    }

    static MCTag wrap(Tag tag) {
        if (tag instanceof CompoundTag c) return new MCCompoundTagImpl(c);
        if (tag instanceof ListTag l) return new MCListTagImpl(l);
        if (tag instanceof ByteTag b) return new MCByteTagImpl(b);
        if (tag instanceof DoubleTag d) return new MCDoubleTagImpl(d);
        if (tag instanceof FloatTag f) return new MCFloatTagImpl(f);
        if (tag instanceof IntTag i) return new MCIntTagImpl(i);
        if (tag instanceof LongTag l) return new MCLongTagImpl(l);
        if (tag instanceof StringTag s) return new MCStringTagImpl(s);
        throw new IllegalArgumentException("Unknown Tag type: " + tag.getClass());
    }
}
