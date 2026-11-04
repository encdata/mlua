package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.item.MCRarity;
import net.minecraft.world.item.Rarity;

public final class MCRarityImpl {
    private MCRarityImpl() {}

    public static MCRarity fromReal(Rarity rarity) {
        if (rarity == null) return MCRarity.COMMON;
        return switch (rarity) {
            case COMMON -> MCRarity.COMMON;
            case UNCOMMON -> MCRarity.UNCOMMON;
            case RARE -> MCRarity.RARE;
            case EPIC -> MCRarity.EPIC;
        };
    }

    public static Rarity toReal(MCRarity rarity) {
        if (rarity == null) return Rarity.COMMON;
        return switch (rarity) {
            case COMMON -> Rarity.COMMON;
            case UNCOMMON -> Rarity.UNCOMMON;
            case RARE -> Rarity.RARE;
            case EPIC -> Rarity.EPIC;
        };
    }
}
