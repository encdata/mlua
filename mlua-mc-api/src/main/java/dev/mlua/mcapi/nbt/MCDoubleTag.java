package dev.mlua.mcapi.nbt;

public interface MCDoubleTag extends MCNumericTag {
    static MCDoubleTag valueOf(double value) {
        return new MCDoubleTag() {
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
                return (long) value;
            }

            @Override
            public String getAsString() {
                return Double.toString(value);
            }
        };
    }
}
