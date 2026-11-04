package dev.mlua.mc.impl.item.enchantment;

import dev.mlua.mcapi.item.enchantment.MCEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;

public class MCEnchantmentImpl implements MCEnchantment {
    private final Enchantment inner;

    public MCEnchantmentImpl(Enchantment inner) {
        this.inner = inner;
    }

    public Enchantment inner() {
        return inner;
    }

    @Override
    public String getDescriptionId() {
        return inner.getDescriptionId();
    }

    @Override
    public int getMaxLevel() {
        return inner.getMaxLevel();
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return inner.getMinCost(level);
    }

    @Override
    public int getMaxCost(int level) {
        return inner.getMaxCost(level);
    }

    @Override
    public boolean isTreasureOnly() {
        return inner.isTreasureOnly();
    }

    @Override
    public boolean isCurse() {
        return inner.isCurse();
    }

    @Override
    public boolean canApply(Object itemStack) {
        if (itemStack instanceof net.minecraft.world.item.ItemStack stack) {
            return inner.canEnchant(stack);
        }
        return false;
    }
}
