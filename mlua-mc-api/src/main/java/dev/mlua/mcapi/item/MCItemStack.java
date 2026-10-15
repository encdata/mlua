package dev.mlua.mcapi.item;

import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.nbt.MCCompoundTag;
import dev.mlua.mcapi.item.enchantment.MCEnchantment;

public interface MCItemStack {
    MCItemStack EMPTY = new MCItemStack() {
        private UnsupportedOperationException immutable() {
            return new UnsupportedOperationException("MCItemStack.EMPTY is immutable");
        }

        @Override public MCItem getItem() { return null; }
        @Override public int getCount() { return 0; }
        @Override public void setCount(int count) { throw immutable(); }
        @Override public int getMaxStackSize() { return 0; }
        @Override public int getMaxDamage() { return 0; }
        @Override public int getDamageValue() { return 0; }
        @Override public void setDamageValue(int damage) { throw immutable(); }
        @Override public boolean isEmpty() { return true; }
        @Override public boolean isDamageableItem() { return false; }
        @Override public boolean hasTag() { return false; }
        @Override public MCCompoundTag getTag() { return null; }
        @Override public MCCompoundTag getOrCreateTag() { throw immutable(); }
        @Override public void setTag(MCCompoundTag tag) { throw immutable(); }
        @Override public MCComponent getHoverName() { return MCComponent.literal(""); }
        @Override public void setHoverName(MCComponent name) { throw immutable(); }
        @Override public MCComponent getDisplayName() { return MCComponent.literal(""); }
        @Override public MCItemStack copy() { return EMPTY; }
        @Override public MCCompoundTag getOrCreateTagElement(String key) { throw immutable(); }
        @Override public int getEnchantmentLevel(MCEnchantment enchantment) { return 0; }
    };

    MCItem getItem();
    int getCount();
    void setCount(int count);
    int getMaxStackSize();
    int getMaxDamage();
    int getDamageValue();
    void setDamageValue(int damage);
    boolean isEmpty();
    boolean isDamageableItem();
    boolean hasTag();
    MCCompoundTag getTag();
    MCCompoundTag getOrCreateTag();
    void setTag(MCCompoundTag tag);
    MCComponent getHoverName();
    void setHoverName(MCComponent name);
    MCComponent getDisplayName();
    MCItemStack copy();
    MCCompoundTag getOrCreateTagElement(String key);

    /** Returns whether both stacks contain the same item and item data, ignoring their counts. */
    boolean canCombine(MCItemStack other);

    /** Returns the level of an enchantment on this stack, or zero when it is absent. */
    default int getEnchantmentLevel(MCEnchantment enchantment) {
        throw new UnsupportedOperationException("Enchantment lookup is not implemented by this item stack adapter");
    }

    static boolean hasEnchantments(MCItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("Enchantments", 9);
    }

    static int getEnchantmentLevel(MCItemStack stack, Object enchantment) {
        if (stack == null) throw new NullPointerException("stack");
        if (!(enchantment instanceof MCEnchantment typed)) {
            throw new IllegalArgumentException("Enchantment lookup requires an MCEnchantment instance");
        }
        return stack.getEnchantmentLevel(typed);
    }
}
