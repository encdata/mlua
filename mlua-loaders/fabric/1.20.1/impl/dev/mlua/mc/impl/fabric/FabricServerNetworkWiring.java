package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricNetworkBridge;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Server-only networking wiring; client networking classes stay client-side. */
public final class FabricServerNetworkWiring {
    private FabricServerNetworkWiring() {}

    public static void wire(FabricNetworkBridge bridge, MinecraftServer server) {
        String modId = bridge.getModId();
        if (modId == null) return;
        ResourceLocation channelId = MLuaResourceLocations.build(modId, "main_network");
        bridge.setSendToClient((packet, playerId) -> {
            if (packet instanceof FriendlyByteBuf buf) {
                var player = server.getPlayerList().getPlayerByName(playerId);
                if (player != null) MLuaFabricNetworkAdapter.sendToClient(channelId, buf, player);
            }
        });
        bridge.setSendToAll(packet -> {
            if (packet instanceof FriendlyByteBuf buf) {
                MLuaFabricNetworkAdapter.sendToAll(channelId, buf, server.getPlayerList().getPlayers());
            }
        });
        bridge.setRemotePresence(playerId -> {
            if (playerId == null || playerId.isBlank()) return false;
            var player = server.getPlayerList().getPlayerByName(playerId);
            return player != null && MLuaFabricNetworkAdapter.canSendToClient(player, channelId);
        });
    }
}
