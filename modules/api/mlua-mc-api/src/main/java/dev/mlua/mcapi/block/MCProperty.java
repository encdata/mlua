package dev.mlua.mcapi.block;

import java.util.Collection;

public interface MCProperty<T extends Comparable<T>> {
    String getName();
    Class<T> getValueClass();
    Collection<T> getPossibleValues();
    String getName(T value);
    T getValue(String name);
}
