package dev.mlua.mcapi.nbt;

public interface MCByteTag extends MCNumericTag {
    static MCByteTag valueOf(byte value) {
        return new MCByteTag() {
            @Override
            public double getAsDouble() {
                return value;
            }

            @Override
            public int getAsInt() {
                return value;
            }

            @Override
            public long getAsLong() {
                return value;
            }

            @Override
            public String getAsString() {
                return Byte.toString(value);
            }
        };
    }
}
