package dev.mlua.mcapi.network;

import dev.mlua.mcapi.MCApiRuntime;

public interface MCFriendlyByteBuf {
    int readInt();
    long readLong();
    double readDouble();
    String readUtf();
    String readUtf(int maxSize);
    void writeInt(int value);
    void writeLong(long value);
    void writeDouble(double value);
    void writeUtf(String value);
    byte readByte();
    void writeByte(int value);
    boolean readBoolean();
    void writeBoolean(boolean value);
    byte[] readByteArray();
    void writeBytes(byte[] bytes);

    static MCFriendlyByteBuf wrappedBuffer(byte[] data) {
        return (MCFriendlyByteBuf) MCApiRuntime.invokeStaticSingleArg(
            "dev.mlua.mc.impl.network.MCFriendlyByteBufImpl",
            "wrappedBuffer",
            data
        );
    }
}
