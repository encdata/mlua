package dev.mlua.mc.impl.effect;

import dev.mlua.mcapi.effect.MCMobEffect;
import dev.mlua.mcapi.effect.MCMobEffectInstance;
import net.minecraft.world.effect.MobEffectInstance;

public class MCMobEffectInstanceImpl implements MCMobEffectInstance {
    private final MobEffectInstance inner;

    public MCMobEffectInstanceImpl(MobEffectInstance inner) {
        this.inner = inner;
    }

    public MobEffectInstance inner() {
        return inner;
    }

    @Override
    public MCMobEffect getEffect() {
        return new MCMobEffectImpl(inner.getEffect());
    }

    @Override
    public int getAmplifier() {
        return inner.getAmplifier();
    }

    @Override
    public int getDuration() {
        return inner.getDuration();
    }

    @Override
    public boolean isAmbient() {
        return inner.isAmbient();
    }

    @Override
    public boolean showParticles() {
        return inner.isVisible();
    }

    @Override
    public boolean showIcon() {
        return inner.showIcon();
    }
}
