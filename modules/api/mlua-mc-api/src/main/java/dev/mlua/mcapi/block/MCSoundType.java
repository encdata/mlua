package dev.mlua.mcapi.block;

public interface MCSoundType {
    float getVolume();
    float getPitch();
    Object getBreakSound();
    Object getStepSound();
    Object getPlaceSound();
    Object getHitSound();
    Object getFallSound();
}
