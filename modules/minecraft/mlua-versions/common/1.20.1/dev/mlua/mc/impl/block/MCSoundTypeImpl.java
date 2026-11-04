package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCSoundType;
import net.minecraft.world.level.block.SoundType;

public class MCSoundTypeImpl implements MCSoundType {
    private final SoundType inner;

    public MCSoundTypeImpl(SoundType inner) {
        this.inner = inner;
    }

    public static MCSoundTypeImpl fromReal(SoundType inner) {
        return inner == null ? null : new MCSoundTypeImpl(inner);
    }

    public SoundType inner() {
        return inner;
    }

    @Override
    public float getVolume() {
        return inner.getVolume();
    }

    @Override
    public float getPitch() {
        return inner.getPitch();
    }

    @Override
    public Object getBreakSound() {
        return inner.getBreakSound();
    }

    @Override
    public Object getStepSound() {
        return inner.getStepSound();
    }

    @Override
    public Object getPlaceSound() {
        return inner.getPlaceSound();
    }

    @Override
    public Object getHitSound() {
        return inner.getHitSound();
    }

    @Override
    public Object getFallSound() {
        return inner.getFallSound();
    }
}
