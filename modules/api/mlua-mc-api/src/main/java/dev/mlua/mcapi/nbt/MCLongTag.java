package dev.mlua.mcapi.nbt;

public interface MCLongTag extends MCNumericTag {
    static MCLongTag valueOf(long value) {
        return new MCLongTag() {
            @Override
            public double getAsDouble() {
                return value;
            }

            @Override
            public int getAsInt() {
                return (int) value;
            }

            @Override
            public long getAsLong() {
                return value;
            }

            @Override
            public String getAsString() {
                return Long.toString(value);
            }
        };
    }
}
