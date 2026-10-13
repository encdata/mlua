package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.item.MCTier;

public class MCTierImpl implements MCTier {
    private final int level;
    private final int uses;
    private final float speed;
    private final float damage;
    private final int enchantmentValue;
    public MCTierImpl(int level, int uses, float speed, float damage, int enchantmentValue) { this.level = level; this.uses = uses; this.speed = speed; this.damage = damage; this.enchantmentValue = enchantmentValue; }
    @Override public int getLevel() { return level; }
    @Override public int getUses() { return uses; }
    @Override public float getSpeed() { return speed; }
    @Override public float getAttackDamageBonus() { return damage; }
    @Override public int getEnchantmentValue() { return enchantmentValue; }
    @Override public Object getTag() { return null; }
}
