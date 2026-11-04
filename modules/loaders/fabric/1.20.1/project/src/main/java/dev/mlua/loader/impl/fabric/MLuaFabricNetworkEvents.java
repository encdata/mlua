package dev.mlua.loader.impl.fabric;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class MLuaFabricNetworkEvents {
    private MLuaFabricNetworkEvents() {
    }

    public static void register(
        Consumer<ServerPlayer> serverJoin,
        Consumer<ServerPlayer> serverDisconnect,
        Runnable clientJoin,
        Runnable clientDisconnect
    ) {
        registerServer(serverJoin, serverDisconnect);
        registerClient(clientJoin, clientDisconnect);
    }

    public static void registerServer(Consumer<ServerPlayer> serverJoin, Consumer<ServerPlayer> serverDisconnect) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> serverJoin.accept(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> serverDisconnect.accept(handler.getPlayer()));
    }

    public static void registerClient(Runnable clientJoin, Runnable clientDisconnect) {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clientJoin.run());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clientDisconnect.run());
    }
}
