package dev.mlua.mcapi.nbt;

public interface MCFloatTag extends MCNumericTag {

    float floatValue();

    static MCFloatTag valueOf(float value) {
        return new MCFloatTag() {
            @Override
            public float floatValue() {
                return value;
            }

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
                return Float.toString(value);
            }
        };
    }

}
