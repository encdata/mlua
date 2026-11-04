package dev.mlua.mc.impl.util;

import dev.mlua.mcapi.util.MCRandomSource;

public class MCRandomSourceImpl implements MCRandomSource {
    private final net.minecraft.util.RandomSource inner;

    public MCRandomSourceImpl(net.minecraft.util.RandomSource inner) {
        this.inner = inner;
    }

    public static MCRandomSourceImpl fromReal(net.minecraft.util.RandomSource inner) {
        return inner == null ? null : new MCRandomSourceImpl(inner);
    }

    public net.minecraft.util.RandomSource inner() {
        return inner;
    }

    @Override
    public int nextInt(int bound) {
        return inner.nextInt(bound);
    }

    @Override
    public double nextDouble() {
        return inner.nextDouble();
    }

    @Override
    public float nextFloat() {
        return inner.nextFloat();
    }

    @Override
    public long nextLong() {
        return inner.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return inner.nextBoolean();
    }

    public static MCRandomSource create(long seed) {
        return new MCRandomSourceImpl(net.minecraft.util.RandomSource.create(seed));
    }
}
