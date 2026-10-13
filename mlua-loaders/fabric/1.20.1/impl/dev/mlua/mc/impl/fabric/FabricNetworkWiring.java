package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricNetworkBridge;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class FabricNetworkWiring {
    private FabricNetworkWiring() {
    }

    public static void wireClient(FabricNetworkBridge bridge) {
        String modId = bridge.getModId();
        if (modId == null) return;
        ResourceLocation channelId = MLuaResourceLocations.build(modId, "main_network");

        bridge.setSendToServer(packet -> {
            if (packet instanceof FriendlyByteBuf buf) {
                ClientPlayNetworking.send(channelId, buf);
            }
        });
        bridge.setRemotePresence(ignored -> ClientPlayNetworking.canSend(channelId));
    }

    public static void wireServer(FabricNetworkBridge bridge, MinecraftServer server) {
        String modId = bridge.getModId();
        if (modId == null) return;
        ResourceLocation channelId = MLuaResourceLocations.build(modId, "main_network");

        bridge.setSendToClient((packet, playerId) -> {
            if (packet instanceof FriendlyByteBuf buf) {
                var player = server.getPlayerList().getPlayerByName(playerId);
                if (player != null) {
                    ServerPlayNetworking.send(player, channelId, buf);
                }
            }
        });

        bridge.setSendToAll(packet -> {
            if (packet instanceof FriendlyByteBuf buf) {
                for (var player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, channelId, buf);
                }
            }
        });
        bridge.setRemotePresence(playerId -> {
            if (playerId == null || playerId.isBlank()) {
                return false;
            }
            var player = server.getPlayerList().getPlayerByName(playerId);
            return player != null && ServerPlayNetworking.canSend(player, channelId);
        });
    }
}
