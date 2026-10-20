package dev.mlua.modules.players;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

/** Exposes the connecting player to service lookups made during PlayerAdded listeners. */
public final class MLuaPlayerJoinScope {
    private static final InheritableThreadLocal<ServerPlayer> CURRENT = new InheritableThreadLocal<>();

    private MLuaPlayerJoinScope() { }

    public static ServerPlayer current() {
        return CURRENT.get();
    }

    public static void run(ServerPlayer player, Runnable action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        ServerPlayer previous = CURRENT.get();
        CURRENT.set(player);
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
