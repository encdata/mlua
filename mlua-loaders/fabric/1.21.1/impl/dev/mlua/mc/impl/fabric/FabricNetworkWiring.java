package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricNetworkBridge;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class FabricNetworkWiring {
    private FabricNetworkWiring() {}

    public static void wireClient(FabricNetworkBridge bridge) {
        String modId = bridge.getModId();
        if (modId == null) return;
        ResourceLocation channelId = MLuaResourceLocations.build(modId, "main_network");
        CustomPacketPayload.Type<ChannelPayload> channelType = ensureRegistered(channelId);

        bridge.setSendToServer(packet -> {
            if (packet instanceof FriendlyByteBuf buf) {
                ClientPlayNetworking.send(new ChannelPayload(channelType, readBytes(buf)));
            }
        });
        bridge.setRemotePresence(ignored -> ClientPlayNetworking.canSend(channelId));
    }

    public static void wireServer(FabricNetworkBridge bridge, MinecraftServer server) {
        String modId = bridge.getModId();
        if (modId == null) return;
        ResourceLocation channelId = MLuaResourceLocations.build(modId, "main_network");
        CustomPacketPayload.Type<ChannelPayload> channelType = ensureRegistered(channelId);

        bridge.setSendToClient((packet, playerId) -> {
            if (packet instanceof FriendlyByteBuf buf) {
                var player = server.getPlayerList().getPlayerByName(playerId);
                if (player != null) {
                    ServerPlayNetworking.send(player, new ChannelPayload(channelType, readBytes(buf)));
                }
            }
        });

        bridge.setSendToAll(packet -> {
            if (packet instanceof FriendlyByteBuf buf) {
                ChannelPayload payload = new ChannelPayload(channelType, readBytes(buf));
                for (var player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, payload);
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

    private static byte[] readBytes(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }

    private static CustomPacketPayload.Type<ChannelPayload> ensureRegistered(ResourceLocation id) {
        return PayloadRegistry.getOrCreate(id);
    }

    public record ChannelPayload(
        CustomPacketPayload.Type<ChannelPayload> resolvedType,
        byte[] bytes
    ) implements CustomPacketPayload {
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return resolvedType;
        }
    }

    private static final class PayloadRegistry {
        private static final java.util.Map<ResourceLocation, CustomPacketPayload.Type<ChannelPayload>> TYPES =
            new java.util.concurrent.ConcurrentHashMap<>();

        static CustomPacketPayload.Type<ChannelPayload> getOrCreate(ResourceLocation id) {
            return TYPES.computeIfAbsent(id, ch -> {
                CustomPacketPayload.Type<ChannelPayload> type = new CustomPacketPayload.Type<>(ch);
                StreamCodec<FriendlyByteBuf, ChannelPayload> codec = StreamCodec.of(
                    (b, payload) -> b.writeBytes(payload.bytes()),
                    b -> {
                        byte[] data = new byte[b.readableBytes()];
                        b.readBytes(data);
                        CustomPacketPayload.Type<ChannelPayload> t = TYPES.get(ch);
                        return new ChannelPayload(t, data);
                    }
                );
                PayloadTypeRegistry.playC2S().register(type, codec);
                PayloadTypeRegistry.playS2C().register(type, codec);
                return type;
            });
        }
    }
}
