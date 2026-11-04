package dev.mlua.mc.impl.network;

import dev.mlua.mcapi.network.MCFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public class MCFriendlyByteBufImpl implements MCFriendlyByteBuf {
    private final FriendlyByteBuf inner;

    public MCFriendlyByteBufImpl(FriendlyByteBuf inner) {
        this.inner = inner;
    }

    public static MCFriendlyByteBufImpl fromReal(FriendlyByteBuf inner) {
        return inner == null ? null : new MCFriendlyByteBufImpl(inner);
    }

    public FriendlyByteBuf inner() {
        return inner;
    }

    @Override
    public int readInt() {
        return inner.readInt();
    }

    @Override
    public long readLong() {
        return inner.readLong();
    }

    @Override
    public double readDouble() {
        return inner.readDouble();
    }

    @Override
    public String readUtf() {
        return inner.readUtf();
    }

    @Override
    public String readUtf(int maxSize) {
        return inner.readUtf(maxSize);
    }

    @Override
    public void writeInt(int value) {
        inner.writeInt(value);
    }

    @Override
    public void writeLong(long value) {
        inner.writeLong(value);
    }

    @Override
    public void writeDouble(double value) {
        inner.writeDouble(value);
    }

    @Override
    public void writeUtf(String value) {
        inner.writeUtf(value);
    }

    @Override
    public byte readByte() {
        return inner.readByte();
    }

    @Override
    public void writeByte(int value) {
        inner.writeByte(value);
    }

    @Override
    public boolean readBoolean() {
        return inner.readBoolean();
    }

    @Override
    public void writeBoolean(boolean value) {
        inner.writeBoolean(value);
    }

    @Override
    public byte[] readByteArray() {
        return inner.readByteArray();
    }

    @Override
    public void writeBytes(byte[] bytes) {
        inner.writeBytes(bytes);
    }

    public static MCFriendlyByteBuf wrappedBuffer(byte[] data) {
        return new MCFriendlyByteBufImpl(new FriendlyByteBuf(Unpooled.wrappedBuffer(data)));
    }
}
