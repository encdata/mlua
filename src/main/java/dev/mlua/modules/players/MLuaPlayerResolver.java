package dev.mlua.modules.players;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Shared Lua-to-Minecraft player resolution rules for player-facing services. */
public final class MLuaPlayerResolver {
    private MLuaPlayerResolver() { }

    public static Player resolve(LuaValue value, MLuaService.Context context, String serviceName) {
        if (value != null && value.istable()) {
            return (Player) MLuaEntityWrapper.requireLuaInstance(value, context.registration(), "Player", "Player");
        }
        if (value != null && value.isstring()) {
            String identifier = value.tojstring();
            if (context.server() != null) {
                ServerPlayer byName = context.server().getPlayerList().getPlayerByName(identifier);
                if (byName != null) return byName;
                if (identifier.length() == 36) {
                    UUID uuid = MLuaLuaArguments.uuid(identifier, serviceName, "player");
                    ServerPlayer byUuid = context.server().getPlayerList().getPlayer(uuid);
                    if (byUuid != null) return byUuid;
                }
            }
            throw new LuaError("[MLua] " + serviceName + " found no connected player named or identified by '"
                + identifier + "'.");
        }
        if ((value == null || value.isnil()) && context.side() == Side.CLIENT) {
            Player local = localPlayer();
            if (local != null) return local;
        }
        throw new LuaError("[MLua] " + serviceName + " expects a Player instance, player name, or player UUID"
            + (context.side() == Side.CLIENT ? "; pass nil to use the local player." : "."));
    }

    public static ServerPlayer resolveServerPlayer(LuaValue value, MLuaService.Context context, String serviceName) {
        Player player = resolve(value, context, serviceName);
        if (player instanceof ServerPlayer serverPlayer) return serverPlayer;
        throw new LuaError("[MLua] " + serviceName + " requires a server player; received "
            + player.getClass().getSimpleName() + ".");
    }

    private static Player localPlayer() {
        try {
            Object player = MLuaClientPlayerAccess.localPlayer();
            return player instanceof Player local ? local : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
