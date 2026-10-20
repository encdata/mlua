package dev.mlua.mcapi.item;

public interface MCTier {
    /** Returned by {@link #getLevel()} when the current Minecraft tier has no harvest level. */
    int UNKNOWN_LEVEL = -1;

    /** Returns the legacy harvest level, or {@link #UNKNOWN_LEVEL} when the game version does not define one. */
    int getLevel();
    int getUses();
    float getSpeed();
    float getAttackDamageBonus();
    int getEnchantmentValue();
    Object getTag();
}
