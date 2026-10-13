package dev.mlua.mc.impl.item.enchantment;

import dev.mlua.mcapi.item.enchantment.MCEnchantment;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
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
        return inner.description().getString();
    }

    @Override
    public int getMaxLevel() {
        return inner.getMaxLevel();
    }

    @Override
    public int getMinLevel() {
        return inner.getMinLevel();
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
        Holder<Enchantment> holder = holder();
        return holder != null && holder.is(EnchantmentTags.TREASURE);
    }

    @Override
    public boolean isCurse() {
        Holder<Enchantment> holder = holder();
        return holder != null && holder.is(EnchantmentTags.CURSE);
    }

    @Override
    public boolean canApply(Object itemStack) {
        if (itemStack instanceof net.minecraft.world.item.ItemStack stack) {
            return inner.canEnchant(stack);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public Holder<Enchantment> holder() {
        Registry<Enchantment> registry = (Registry<Enchantment>) BuiltInRegistries.REGISTRY
            .get(Registries.ENCHANTMENT.location());
        if (registry == null) return null;
        var key = registry.getKey(inner);
        return key == null ? null : registry.getHolder(key).orElse(null);
    }
}
