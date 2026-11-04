package dev.mlua.mcapi.item.enchantment;

public interface MCEnchantment {
    String getDescriptionId();
    int getMaxLevel();
    int getMinLevel();
    int getMinCost(int level);
    int getMaxCost(int level);
    boolean isTreasureOnly();
    boolean isCurse();
    boolean canApply(Object itemStack);
}
