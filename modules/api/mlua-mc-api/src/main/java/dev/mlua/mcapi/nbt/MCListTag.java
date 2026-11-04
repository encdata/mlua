package dev.mlua.mcapi.nbt;

import java.util.ArrayList;
import java.util.List;

public interface MCListTag extends MCTag {
    int size();

    String getString(int index);

    default String getStringOr(int index, String fallback) {
        if (index < 0 || index >= size()) return fallback;
        return getString(index);
    }

    MCTag get(int index);

    void add(MCTag tag);

    void clear();

    static MCListTag create() {
        return new MCListTag() {
            private final List<MCTag> list = new ArrayList<>();

            @Override
            public int size() {
                return list.size();
            }

            @Override
            public String getString(int index) {
                MCTag tag = list.get(index);
                return tag instanceof MCStringTag s ? s.getAsString() : "";
            }

            @Override
            public MCTag get(int index) {
                return list.get(index);
            }

            @Override
            public void add(MCTag tag) {
                list.add(tag);
            }

            @Override
            public void clear() {
                list.clear();
            }

            @Override
            public String getAsString() {
                return list.toString();
            }
        };
    }
}
