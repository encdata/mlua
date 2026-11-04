package dev.mlua.mcapi.nbt;

public interface MCIntTag extends MCNumericTag {
    static MCIntTag valueOf(int value) {
        return new MCIntTag() {
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
                return Integer.toString(value);
            }
        };
    }
}
