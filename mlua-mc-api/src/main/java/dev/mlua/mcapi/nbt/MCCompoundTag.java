package dev.mlua.mcapi.nbt;

import java.util.Set;

public interface MCCompoundTag extends MCTag {
    Set<String> getAllKeys();

    MCTag get(String key);

    String getString(String key);

    void putString(String key, String value);

    void remove(String key);

    boolean contains(String key);

    boolean contains(String key, int type);

    MCCompoundTag getCompound(String key);

    MCListTag getList(String key, int type);

    int getInt(String key);

    long getLong(String key);

    double getDouble(String key);

    void putInt(String key, int value);

    void putLong(String key, long value);

    void putDouble(String key, double value);

    void put(String key, MCTag tag);

    static MCCompoundTag create() {
        return new MCCompoundTag() {
            private final java.util.Map<String, MCTag> map = new java.util.LinkedHashMap<>();

            @Override
            public Set<String> getAllKeys() {
                return map.keySet();
            }

            @Override
            public MCTag get(String key) {
                return map.get(key);
            }

            @Override
            public String getString(String key) {
                MCTag tag = map.get(key);
                return tag instanceof MCStringTag s ? s.getAsString() : "";
            }

            @Override
            public void putString(String key, String value) {
                map.put(key, MCStringTag.valueOf(value));
            }

            @Override
            public void remove(String key) {
                map.remove(key);
            }

            @Override
            public boolean contains(String key) {
                return map.containsKey(key);
            }

            @Override
            public boolean contains(String key, int type) {
                MCTag tag = map.get(key);
                if (tag == null) return false;
                return switch (type) {
                    case 1 -> tag instanceof MCByteTag;
                    case 3 -> tag instanceof MCIntTag;
                    case 4 -> tag instanceof MCLongTag;
                    case 6 -> tag instanceof MCDoubleTag;
                    case 8 -> tag instanceof MCStringTag;
                    case 9 -> tag instanceof MCListTag;
                    case 10 -> tag instanceof MCCompoundTag;
                    default -> true;
                };
            }

            @Override
            public MCCompoundTag getCompound(String key) {
                MCTag tag = map.get(key);
                return tag instanceof MCCompoundTag c ? c : null;
            }

            @Override
            public MCListTag getList(String key, int type) {
                MCTag tag = map.get(key);
                return tag instanceof MCListTag l ? l : null;
            }

            @Override
            public int getInt(String key) {
                MCTag tag = map.get(key);
                return tag instanceof MCNumericTag n ? n.getAsInt() : 0;
            }

            @Override
            public long getLong(String key) {
                MCTag tag = map.get(key);
                return tag instanceof MCNumericTag n ? n.getAsLong() : 0L;
            }

            @Override
            public double getDouble(String key) {
                MCTag tag = map.get(key);
                return tag instanceof MCNumericTag n ? n.getAsDouble() : 0.0;
            }

            @Override
            public void putInt(String key, int value) {
                map.put(key, new MCIntTag() {
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
                });
            }

            @Override
            public void putLong(String key, long value) {
                map.put(key, MCLongTag.valueOf(value));
            }

            @Override
            public void putDouble(String key, double value) {
                map.put(key, MCDoubleTag.valueOf(value));
            }

            @Override
            public void put(String key, MCTag tag) {
                map.put(key, tag);
            }

            @Override
            public String getAsString() {
                return map.toString();
            }
        };
    }
}
