package dev.mlua.mcapi.item;

public final class MCTiers {
    public static final MCTier WOOD = new MCTier() {
        @Override public int getLevel() { return 0; }
        @Override public int getUses() { return 59; }
        @Override public float getSpeed() { return 2.0f; }
        @Override public float getAttackDamageBonus() { return 0.0f; }
        @Override public int getEnchantmentValue() { return 15; }
        @Override public Object getTag() { return null; }
    };

    public static final MCTier STONE = new MCTier() {
        @Override public int getLevel() { return 1; }
        @Override public int getUses() { return 131; }
        @Override public float getSpeed() { return 4.0f; }
        @Override public float getAttackDamageBonus() { return 1.0f; }
        @Override public int getEnchantmentValue() { return 5; }
        @Override public Object getTag() { return null; }
    };

    public static final MCTier IRON = new MCTier() {
        @Override public int getLevel() { return 2; }
        @Override public int getUses() { return 250; }
        @Override public float getSpeed() { return 6.0f; }
        @Override public float getAttackDamageBonus() { return 2.0f; }
        @Override public int getEnchantmentValue() { return 14; }
        @Override public Object getTag() { return null; }
    };

    public static final MCTier GOLD = new MCTier() {
        @Override public int getLevel() { return 0; }
        @Override public int getUses() { return 32; }
        @Override public float getSpeed() { return 12.0f; }
        @Override public float getAttackDamageBonus() { return 0.0f; }
        @Override public int getEnchantmentValue() { return 22; }
        @Override public Object getTag() { return null; }
    };

    public static final MCTier DIAMOND = new MCTier() {
        @Override public int getLevel() { return 3; }
        @Override public int getUses() { return 1561; }
        @Override public float getSpeed() { return 8.0f; }
        @Override public float getAttackDamageBonus() { return 3.0f; }
        @Override public int getEnchantmentValue() { return 10; }
        @Override public Object getTag() { return null; }
    };

    public static final MCTier NETHERITE = new MCTier() {
        @Override public int getLevel() { return 4; }
        @Override public int getUses() { return 2031; }
        @Override public float getSpeed() { return 9.0f; }
        @Override public float getAttackDamageBonus() { return 4.0f; }
        @Override public int getEnchantmentValue() { return 15; }
        @Override public Object getTag() { return null; }
    };

    private MCTiers() {}
}
