package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricNetworkBridge;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;

/** Server-only networking wiring; client networking classes stay client-side. */
public final class FabricServerNetworkWiring {
    private FabricServerNetworkWiring() {}

    public static void wire(FabricNetworkBridge bridge, MinecraftServer server) {
        String modId = bridge.getModId();
        if (modId == null) return;
        var channelId = MLuaResourceLocations.build(modId, "main_network");
        bridge.setSendToClient((packet, playerId) -> {
            FriendlyByteBuf buf = requireBuffer(packet);
            var player = server.getPlayerList().getPlayerByName(playerId);
            if (player != null) MLuaFabricNetworkAdapter.sendToClient(channelId, buf, player);
        });
        bridge.setSendToAll(packet -> {
            MLuaFabricNetworkAdapter.sendToAll(channelId, requireBuffer(packet), server.getPlayerList().getPlayers());
        });
        bridge.setRemotePresence(playerId -> {
            if (playerId == null || playerId.isBlank()) return false;
            var player = server.getPlayerList().getPlayerByName(playerId);
            return player != null && MLuaFabricNetworkAdapter.canSendToClient(player, channelId);
        });
    }

    private static FriendlyByteBuf requireBuffer(Object packet) {
        if (packet instanceof FriendlyByteBuf buffer) return buffer;
        throw new IllegalArgumentException("MLua network bridge expects a FriendlyByteBuf packet");
    }
}
