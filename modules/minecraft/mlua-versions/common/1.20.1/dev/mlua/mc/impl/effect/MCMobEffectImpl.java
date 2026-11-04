package dev.mlua.mc.impl.effect;

import dev.mlua.mcapi.effect.MCMobEffect;
import net.minecraft.world.effect.MobEffect;

public class MCMobEffectImpl implements MCMobEffect {
    private final MobEffect inner;

    public MCMobEffectImpl(MobEffect inner) {
        this.inner = inner;
    }

    public MobEffect inner() {
        return inner;
    }

    @Override
    public String getDescriptionId() {
        return inner.getDescriptionId();
    }

    @Override
    public int getColor() {
        return inner.getColor();
    }

    @Override
    public boolean isBeneficial() {
        return inner.isBeneficial();
    }
}
