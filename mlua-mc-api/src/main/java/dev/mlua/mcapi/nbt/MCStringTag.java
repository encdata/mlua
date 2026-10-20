package dev.mlua.mcapi.nbt;

public interface MCStringTag extends MCTag {
    static MCStringTag valueOf(String value) {
        return new MCStringTag() {
            @Override
            public String getAsString() {
                return value;
            }
        };
    }
}
