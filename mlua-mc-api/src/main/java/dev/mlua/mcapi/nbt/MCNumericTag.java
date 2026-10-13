package dev.mlua.mcapi.nbt;

public interface MCNumericTag extends MCTag {
    double getAsDouble();

    int getAsInt();

    long getAsLong();
}
