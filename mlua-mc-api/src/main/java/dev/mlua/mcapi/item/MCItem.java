package dev.mlua.mcapi.item;

import dev.mlua.mcapi.core.MCComponent;

public interface MCItem {
    int getMaxStackSize();
    int getMaxDamage();
    boolean isDamageable(MCItemStack stack);
    MCCreativeModeTab getCreativeTab();
    String getDescriptionId();
    MCItemStack getDefaultInstance();
    boolean isEnabledForEnchantment();
}
