package dev.mlua.mc.impl.command;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;

/** Adapts MLua's numeric permission levels to Minecraft 26.1 permission checks. */
public final class MLuaPermissionAdapter {
    private MLuaPermissionAdapter() {}

    public static boolean hasPermission(Object source, int level) {
        PermissionCheck check = switch (Math.max(0, Math.min(level, 4))) {
            case 0 -> Commands.LEVEL_ALL;
            case 1 -> Commands.LEVEL_MODERATORS;
            case 2 -> Commands.LEVEL_GAMEMASTERS;
            case 3 -> Commands.LEVEL_ADMINS;
            default -> Commands.LEVEL_OWNERS;
        };
        if (source instanceof CommandSourceStack commandSource) {
            return Commands.hasPermission(check).test(commandSource);
        }
        if (source instanceof ServerPlayer player) {
            return Commands.hasPermission(check).test(player::permissions);
        }
        return false;
    }
}
