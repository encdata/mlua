package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.item.MCTier;
import net.minecraft.world.item.Tier;

public class MCTierImpl implements MCTier {
    private final Tier inner;

    public MCTierImpl(Tier inner) {
        this.inner = inner;
    }

    public Tier inner() {
        return inner;
    }

    @Override
    public int getLevel() {
        return inner.getLevel();
    }

    @Override
    public int getUses() {
        return inner.getUses();
    }

    @Override
    public float getSpeed() {
        return inner.getSpeed();
    }

    @Override
    public float getAttackDamageBonus() {
        return inner.getAttackDamageBonus();
    }

    @Override
    public int getEnchantmentValue() {
        return inner.getEnchantmentValue();
    }

    @Override
    public Object getTag() {
        return null;
    }
}
