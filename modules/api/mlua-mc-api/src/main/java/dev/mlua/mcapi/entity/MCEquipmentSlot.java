package dev.mlua.mcapi.entity;

public enum MCEquipmentSlot {
    MAINHAND(0, "mainhand"),
    OFFHAND(1, "offhand"),
    FEET(2, "feet"),
    LEGS(3, "legs"),
    CHEST(4, "chest"),
    HEAD(5, "head");

    private final int index;
    private final String name;

    MCEquipmentSlot(int index, String name) {
        this.index = index;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }
}
