package dev.mlua.mc.impl.world;

import dev.mlua.mcapi.world.MCGameType;
import net.minecraft.world.level.GameType;

public final class MCGameTypeImpl {
    private MCGameTypeImpl() {}

    public static MCGameType fromReal(GameType type) {
        if (type == null) return MCGameType.SURVIVAL;
        return switch (type) {
            case SURVIVAL -> MCGameType.SURVIVAL;
            case CREATIVE -> MCGameType.CREATIVE;
            case ADVENTURE -> MCGameType.ADVENTURE;
            case SPECTATOR -> MCGameType.SPECTATOR;
            default -> MCGameType.SURVIVAL;
        };
    }

    public static GameType toReal(MCGameType type) {
        if (type == null) return GameType.SURVIVAL;
        return switch (type) {
            case SURVIVAL -> GameType.SURVIVAL;
            case CREATIVE -> GameType.CREATIVE;
            case ADVENTURE -> GameType.ADVENTURE;
            case SPECTATOR -> GameType.SPECTATOR;
        };
    }
}
