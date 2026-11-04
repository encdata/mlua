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
        if (inner == net.minecraft.world.item.Tiers.WOOD || inner == net.minecraft.world.item.Tiers.GOLD) return 0;
        if (inner == net.minecraft.world.item.Tiers.STONE) return 1;
        if (inner == net.minecraft.world.item.Tiers.IRON) return 2;
        if (inner == net.minecraft.world.item.Tiers.DIAMOND) return 3;
        if (inner == net.minecraft.world.item.Tiers.NETHERITE) return 4;
        return MCTier.UNKNOWN_LEVEL;
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
        return inner.getIncorrectBlocksForDrops();
    }
}
