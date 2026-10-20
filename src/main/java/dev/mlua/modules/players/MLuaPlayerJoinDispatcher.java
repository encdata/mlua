package dev.mlua.modules.players;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.modules.players.service.MLuaPlayersService;
import dev.mlua.runtime.data.MLuaDataManager;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Defers join callbacks until login has completed, then dispatches them from the server tick thread. */
final class MLuaPlayerJoinDispatcher {
    private final ConcurrentLinkedQueue<ServerPlayer> pending = new ConcurrentLinkedQueue<>();

    void enqueue(Object player) {
        this.pending.add((ServerPlayer) player);
    }

    void dispatchPending(Object serverObject) {
        if (this.pending.isEmpty()) return;
        MinecraftServer server = (MinecraftServer) serverObject;
        int queuedAtStart = this.pending.size();
        ServerPlayer player;
        while (queuedAtStart-- > 0 && (player = this.pending.poll()) != null) {
            if (player.isRemoved()) continue;
            if (player.level().getServer() != server) {
                this.pending.add(player);
                continue;
            }
            this.dispatch(player);
        }
    }

    void clear() {
        this.pending.clear();
    }

    private void dispatch(ServerPlayer player) {
        var session = MLuaRuntime.sessionForLevel(player.level());
        var registration = MLuaRuntime.registrationForLevel(player.level());
        var playerInstance = MLuaPlayersService.playerTable(player, registration);
        MLuaRuntime ownerRuntime = registration instanceof MLuaRuntime runtime ? runtime : MLuaRuntime.get();

        try {
            MLuaPlayerJoinScope.run(player, () ->
                MLuaPlayersService.PLAYER_ADDED.fireInlineOnSession(session, Side.SERVER, playerInstance));
        } catch (RuntimeException failure) {
            dev.mlua.core.MLuaLogger.LOGGER.error("[MLua] PlayerAdded dispatch failed", failure);
        } finally {
            ownerRuntime.deliverPendingServerDiagnostics(player);
            MLuaDataManager.syncInitialState(player);
        }
    }
}
