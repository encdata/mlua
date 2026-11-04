package dev.mlua.minecraft.client;

import org.luaj.vm2.LuaError;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/** Client-side entity actions that must pass through Minecraft's active game mode. */
public final class MLuaClientEntityActions {
    private MLuaClientEntityActions() { }

    public static boolean attack(Entity target) {
        return dev.mlua.runtime.client.MLuaClientThread.callOnClientThread(() -> {
            Minecraft minecraft = MLuaMinecraftClient.instance();
            LocalPlayer player = requireActivePlayer(minecraft, "Entity:Attack");
            requireTarget(minecraft, player, target, "Entity:Attack");
            minecraft.gameMode.attack(player, target);
            return true;
        });
    }

    public static boolean attack(LocalPlayer player, Entity target) {
        return dev.mlua.runtime.client.MLuaClientThread.callOnClientThread(() -> {
            Minecraft minecraft = MLuaMinecraftClient.instance();
            requireActivePlayer(minecraft, "Player:AttackEntity", player);
            requireTarget(minecraft, player, target, "Player:AttackEntity");
            minecraft.gameMode.attack(player, target);
            return true;
        });
    }

    private static LocalPlayer requireActivePlayer(Minecraft minecraft, String operation) {
        return requireActivePlayer(minecraft, operation, minecraft.player);
    }

    private static LocalPlayer requireActivePlayer(Minecraft minecraft, String operation, LocalPlayer player) {
        if (!dev.mlua.runtime.client.MLuaClientThread.isClientExecutionContext()) {
            throw new LuaError("[MLua] " + operation + " must run on Minecraft's client thread.");
        }
        if (player == null || minecraft.player != player || minecraft.gameMode == null || minecraft.level == null) {
            throw new LuaError("[MLua] " + operation + " requires the active local player in a loaded world.");
        }
        return player;
    }

    private static void requireTarget(Minecraft minecraft, LocalPlayer player, Entity target, String operation) {
        if (target == null || target == player || target.isRemoved()
                || target.level() != player.level() || target.level() != minecraft.level) {
            throw new LuaError("[MLua] " + operation
                + " requires a live target other than the local player in the active world.");
        }
    }
}
