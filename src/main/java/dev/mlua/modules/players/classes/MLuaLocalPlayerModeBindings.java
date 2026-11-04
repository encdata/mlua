package dev.mlua.modules.players.classes;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static dev.mlua.modules.players.classes.MLuaPlayerWrapper.luaFn;
import static dev.mlua.modules.players.classes.MLuaPlayerWrapper.varArg;

/** Owns client-local game-mode overrides, server mode lookup, and flight abilities. */
final class MLuaLocalPlayerModeBindings {
    private MLuaLocalPlayerModeBindings() { }

    static void bind(LuaTable table, LocalPlayer player) {
        MLuaClassMethods.setInstanceMethod(table, "IsCreative", luaFn(() -> {
            GameType mode = localClientGameMode(player);
            return LuaValue.valueOf(mode == null ? player.isCreative() : mode == GameType.CREATIVE);
        }));
        MLuaClassMethods.setInstanceMethod(table, "IsSpectator", luaFn(() -> {
            GameType mode = localClientGameMode(player);
            return LuaValue.valueOf(mode == null ? player.isSpectator() : mode == GameType.SPECTATOR);
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetGameMode", luaFn(() -> {
            GameType mode = localClientGameMode(player);
            return mode == null ? LuaValue.valueOf("unknown") : MLuaMinecraftEnums.gameModeOf(mode);
        }), "Returns the game mode this client currently assigns to its local player. After SetLocalGameMode(), it can differ from GetServerGameMode(); this is client-side state, not the server's authoritative mode.");
        MLuaClassMethods.setInstanceMethod(table, "GetServerGameMode", luaFn(() -> clientGameMode(player)),
            "Returns the game mode reported by the server, independently of the local client's selected mode.");
        MLuaClassMethods.setInstanceMethod(table, "SetGameMode", varArg(args -> {
            throw new LuaError("[MLua] Player:SetGameMode is server-side only. Use Player:SetLocalGameMode() to make the local client treat its player as another game mode.");
        }), "Changes this player's game mode. This method is server-side only; local client scripts can use SetLocalGameMode for a client-only override.");
        MLuaClassMethods.setInstanceMethod(table, "SetLocalGameMode", varArg(args -> {
            GameType mode = MLuaMinecraftEnums.resolveGameMode(args.arg(2));
            return MLuaLocalPlayerActionBindings.run(player, "Player:SetLocalGameMode", () ->
                MLuaMinecraftClient.instance().gameMode.setLocalMode(mode));
        }), "Changes the game mode this client assigns to its local player, making the client behave as though the player were in that mode. This is a local client-side override: it does not change the server's mode or grant server permissions, and the server may overwrite it.");
        MLuaClassMethods.setInstanceMethod(table, "ResetLocalGameMode", varArg(args ->
            MLuaLocalPlayerActionBindings.run(player, "Player:ResetLocalGameMode", () -> {
                GameType serverMode = serverGameType(player);
                if (serverMode == null) {
                    throw new LuaError("[MLua] Player:ResetLocalGameMode cannot run because the server-reported game mode is unavailable.");
                }
                MLuaMinecraftClient.instance().gameMode.setLocalMode(serverMode);
            })), "Restores the local client's game mode to the latest mode reported by the server. This does not change server state.");
        MLuaClassMethods.setInstanceMethod(table, "CanFly", luaFn(() -> {
            MLuaLocalPlayerBindings.requireActiveLocalPlayer(player, "Player:CanFly");
            return LuaValue.valueOf(player.getAbilities().mayfly);
        }));
        MLuaClassMethods.setInstanceMethod(table, "SetCanFly", varArg(args -> {
            boolean allowed = args.checkboolean(2);
            return MLuaLocalPlayerActionBindings.run(player, "Player:SetCanFly", () -> {
                player.getAbilities().mayfly = allowed;
                if (!allowed) player.getAbilities().flying = false;
            });
        }), "Changes the local may-fly ability flag. In multiplayer, server ability updates can replace this value.");
        MLuaClassMethods.setInstanceMethod(table, "IsFlying", luaFn(() -> {
            MLuaLocalPlayerBindings.requireActiveLocalPlayer(player, "Player:IsFlying");
            return LuaValue.valueOf(player.getAbilities().flying);
        }));
        MLuaClassMethods.setInstanceMethod(table, "SetFlying", varArg(args -> {
            boolean flying = args.checkboolean(2);
            return MLuaLocalPlayerActionBindings.run(player, "Player:SetFlying", () -> {
                if (flying && !player.getAbilities().mayfly) {
                    throw new LuaError("[MLua] Player:SetFlying(true) requires Player:SetCanFly(true) or a game mode that allows flight.");
                }
                player.getAbilities().flying = flying;
            });
        }), "Changes the local flying flag. SetCanFly(true) first when the current game mode does not allow flight; a server may replace both flags.");
    }

    static LuaValue clientGameMode(Player player) {
        GameType mode = serverGameType(player);
        return mode == null ? LuaValue.valueOf("unknown") : MLuaMinecraftEnums.gameModeOf(mode);
    }

    private static GameType localClientGameMode(LocalPlayer player) {
        Minecraft minecraft = MLuaMinecraftClient.instance();
        if (minecraft.player != player || minecraft.level == null) return null;
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        if (gameMode == null) return null;
        try {
            return gameMode.getPlayerMode();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static GameType serverGameType(Player player) {
        Minecraft minecraft = MLuaMinecraftClient.instance();
        if (minecraft.getConnection() == null) return null;
        var playerInfo = minecraft.getConnection().getPlayerInfo(player.getUUID());
        return playerInfo == null ? null : playerInfo.getGameMode();
    }
}
