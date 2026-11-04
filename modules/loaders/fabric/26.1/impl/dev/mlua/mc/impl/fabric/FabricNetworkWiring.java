package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricNetworkBridge;
import dev.mlua.minecraft.MLuaResourceLocations;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fabric 26.1 custom-payload registration for MLua's shared network bridge. */
public final class FabricNetworkWiring {
    private FabricNetworkWiring() { }

    public static void wireClient(FabricNetworkBridge bridge) {
        String modId = bridge.getModId();
        if (modId == null) return;
        Identifier id = MLuaResourceLocations.build(modId, "main_network");
        CustomPacketPayload.Type<ChannelPayload> type = ensureRegistered(id);
        bridge.setSendToServer(packet -> {
            if (packet instanceof FriendlyByteBuf buffer) ClientPlayNetworking.send(new ChannelPayload(type, read(buffer)));
        });
        bridge.setRemotePresence(ignored -> ClientPlayNetworking.canSend(id));
    }

    public static void wireServer(FabricNetworkBridge bridge, MinecraftServer server) {
        String modId = bridge.getModId();
        if (modId == null) return;
        Identifier id = MLuaResourceLocations.build(modId, "main_network");
        CustomPacketPayload.Type<ChannelPayload> type = ensureRegistered(id);
        bridge.setSendToClient((packet, playerId) -> {
            if (packet instanceof FriendlyByteBuf buffer) {
                var player = server.getPlayerList().getPlayerByName(playerId);
                if (player != null) ServerPlayNetworking.send(player, new ChannelPayload(type, read(buffer)));
            }
        });
        bridge.setSendToAll(packet -> {
            if (packet instanceof FriendlyByteBuf buffer) {
                ChannelPayload payload = new ChannelPayload(type, read(buffer));
                for (var player : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(player, payload);
            }
        });
        bridge.setRemotePresence(playerId -> {
            if (playerId == null || playerId.isBlank()) return false;
            var player = server.getPlayerList().getPlayerByName(playerId);
            return player != null && ServerPlayNetworking.canSend(player, id);
        });
    }

    private static byte[] read(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    private static CustomPacketPayload.Type<ChannelPayload> ensureRegistered(Identifier id) {
        return Payloads.TYPES.computeIfAbsent(id, key -> {
            CustomPacketPayload.Type<ChannelPayload> type = new CustomPacketPayload.Type<>(key);
            StreamCodec<RegistryFriendlyByteBuf, ChannelPayload> codec = StreamCodec.of(
                (buffer, payload) -> buffer.writeBytes(payload.bytes()),
                buffer -> {
                    byte[] bytes = new byte[buffer.readableBytes()];
                    buffer.readBytes(bytes);
                    return new ChannelPayload(type, bytes);
                });
            PayloadTypeRegistry.serverboundPlay().register(type, codec);
            PayloadTypeRegistry.clientboundPlay().register(type, codec);
            return type;
        });
    }

    public record ChannelPayload(CustomPacketPayload.Type<ChannelPayload> resolvedType, byte[] bytes)
        implements CustomPacketPayload {
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return resolvedType; }
    }

    private static final class Payloads {
        private static final Map<Identifier, CustomPacketPayload.Type<ChannelPayload>> TYPES = new ConcurrentHashMap<>();
    }
}
