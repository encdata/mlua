package dev.mlua.mc.impl.effect;

import dev.mlua.mcapi.effect.MCMobEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

public class MCMobEffectImpl implements MCMobEffect {
    private final Holder<MobEffect> inner;

    public MCMobEffectImpl(Holder<MobEffect> inner) {
        this.inner = inner;
    }

    public Holder<MobEffect> inner() {
        return inner;
    }

    @Override
    public String getDescriptionId() {
        return inner.value().getDescriptionId();
    }

    @Override
    public int getColor() {
        return inner.value().getColor();
    }

    @Override
    public boolean isBeneficial() {
        return inner.value().isBeneficial();
    }
}
