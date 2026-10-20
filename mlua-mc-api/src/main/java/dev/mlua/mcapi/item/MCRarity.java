package dev.mlua.mcapi.item;

import java.awt.Color;

public enum MCRarity {
    COMMON("Common", new Color(255, 255, 255)),
    UNCOMMON("Uncommon", new Color(85, 255, 85)),
    RARE("Rare", new Color(85, 255, 255)),
    EPIC("Epic", new Color(255, 85, 255));

    private final String name;
    private final Color color;

    MCRarity(String name, Color color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public Color getColor() {
        return color;
    }
}
