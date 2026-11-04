package dev.mlua.modules.players.classes;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.client.MLuaClientThread;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import static dev.mlua.modules.players.classes.MLuaPlayerWrapper.varArg;

/** Owns actions issued by the local client player through Minecraft's client game mode. */
final class MLuaLocalPlayerActionBindings {
    private MLuaLocalPlayerActionBindings() { }

    static void bind(LuaTable table, LocalPlayer player) {
        MLuaClassMethods.setInstanceMethod(table, "Jump", MLuaClassMethods.zeroArg(() ->
            run(player, "Player:Jump", () ->
                player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.42, 0.0)))),
            "Requests a jump from the local player using Minecraft's normal movement rules.");
        MLuaClassMethods.setInstanceMethod(table, "SetSprinting", varArg(args -> {
            boolean sprinting = args.checkboolean(2);
            return run(player, "Player:SetSprinting", () -> player.setSprinting(sprinting));
        }), "Sets the local player's sprint state; Minecraft and the server still apply their normal movement rules.");
        MLuaClassMethods.setInstanceMethod(table, "Turn", varArg(args -> {
            MLuaLocalPlayerBindings.requireActiveLocalPlayer(player, "Player:Turn");
            LuaValue delta = args.arg(2);
            double mouseX;
            double mouseY;
            if (MLuaVector.isVector2(delta)) {
                double[] components = MLuaVector.components2(delta);
                mouseX = components[0];
                mouseY = components[1];
            } else if (args.narg() >= 3) {
                mouseX = args.checkdouble(2);
                mouseY = args.checkdouble(3);
            } else {
                throw new LuaError("[MLua] Player:Turn expects a Vector2 mouse delta or mouseDeltaX, mouseDeltaY.");
            }
            if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY)
                    || Math.abs(mouseX) > 10_000 || Math.abs(mouseY) > 10_000) {
                throw new LuaError("[MLua] Player:Turn mouse deltas must be finite and within -10000..10000.");
            }
            double turnX = mouseX;
            double turnY = mouseY;
            return run(player, "Player:Turn", () -> player.turn(turnX, turnY));
        }), "Applies raw mouse-turn deltas to the local player view using Minecraft's normal turn method.");
        MLuaClassMethods.setInstanceMethod(table, "SwingArm", varArg(args -> {
            InteractionHand hand = localHand(args.arg(2));
            return run(player, "Player:SwingArm", () -> player.swing(hand));
        }), "Plays the local player's arm swing for the selected hand.");
        MLuaClassMethods.setInstanceMethod(table, "AttackEntity", varArg(args -> {
            Entity target = localEntityArgument(args.arg(2), "Player:AttackEntity");
            return LuaValue.valueOf(dev.mlua.minecraft.client.MLuaClientEntityActions.attack(player, target));
        }), "Requests a normal Minecraft attack against an entity and returns true when submitted. The game mode and server validate the hit.");
        MLuaClassMethods.setInstanceMethod(table, "UseItem", varArg(args -> {
            InteractionHand hand = localHand(args.arg(2));
            return MLuaClientThread.callOnClientThread(() -> {
                MLuaLocalPlayerBindings.requireActiveLocalPlayer(player, "Player:UseItem");
                MLuaMinecraftClient.instance().gameMode.useItem(player, hand);
                return LuaValue.NONE;
            });
        }), "Requests the normal item-use action for the selected hand.");
    }

    private static InteractionHand localHand(LuaValue value) {
        if (value.isnil()) return InteractionHand.MAIN_HAND;
        if (!value.isstring()) throw new LuaError("[MLua] Hand must be 'main' or 'off'.");
        return switch (value.checkjstring().toLowerCase(Locale.ROOT)) {
            case "main", "main_hand" -> InteractionHand.MAIN_HAND;
            case "off", "off_hand" -> InteractionHand.OFF_HAND;
            default -> throw new LuaError("[MLua] Hand must be 'main' or 'off'.");
        };
    }

    static LuaValue run(LocalPlayer player, String operation, Runnable action) {
        return MLuaClientThread.callOnClientThread(() -> {
            MLuaLocalPlayerBindings.requireActiveLocalPlayer(player, operation);
            action.run();
            return LuaValue.NONE;
        });
    }

    private static Entity localEntityArgument(LuaValue value, String operation) {
        LuaValue raw = value.istable() ? value.checktable().rawget("__entity") : value;
        if (raw.isuserdata(Entity.class)) return (Entity) raw.touserdata(Entity.class);
        throw new LuaError("[MLua] " + operation + " requires a live Entity instance.");
    }
}
