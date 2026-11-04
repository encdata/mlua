package dev.mlua.mc.impl.world;

import dev.mlua.mcapi.world.MCDifficulty;
import net.minecraft.world.Difficulty;

public final class MCDifficultyImpl {
    private MCDifficultyImpl() {}

    public static MCDifficulty fromReal(Difficulty diff) {
        if (diff == null) return MCDifficulty.PEACEFUL;
        return switch (diff) {
            case PEACEFUL -> MCDifficulty.PEACEFUL;
            case EASY -> MCDifficulty.EASY;
            case NORMAL -> MCDifficulty.NORMAL;
            case HARD -> MCDifficulty.HARD;
            default -> MCDifficulty.PEACEFUL;
        };
    }

    public static Difficulty toReal(MCDifficulty diff) {
        if (diff == null) return Difficulty.PEACEFUL;
        return switch (diff) {
            case PEACEFUL -> Difficulty.PEACEFUL;
            case EASY -> Difficulty.EASY;
            case NORMAL -> Difficulty.NORMAL;
            case HARD -> Difficulty.HARD;
        };
    }
}
