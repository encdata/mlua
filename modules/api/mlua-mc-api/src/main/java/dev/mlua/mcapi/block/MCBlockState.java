package dev.mlua.mcapi.block;

public interface MCBlockState {
    MCBlock getBlock();
    <T extends Comparable<T>> T getValue(MCProperty<T> property);
    <T extends Comparable<T>> MCBlockState setValue(MCProperty<T> property, T value);
    boolean isAir();
    boolean isSolid();
    MCSoundType getSoundType();
    boolean hasProperty(MCProperty<?> property);
}
