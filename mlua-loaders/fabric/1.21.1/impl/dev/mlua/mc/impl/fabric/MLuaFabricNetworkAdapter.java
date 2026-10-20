package dev.mlua.mc.impl.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MLuaFabricNetworkAdapter {
    private static final Map<ResourceLocation, CustomPacketPayload.Type<ByteBufPayload>> CHANNEL_TYPES = new ConcurrentHashMap<>();

    private MLuaFabricNetworkAdapter() {}

    public static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static void registerC2S(ResourceLocation channel, ServerPlayNetworking.PlayPayloadHandler<ByteBufPayload> handler) {
        CustomPacketPayload.Type<ByteBufPayload> type = getOrCreateType(channel, true);
        ServerPlayNetworking.registerGlobalReceiver(type, handler);
    }

    public static void registerS2C(ResourceLocation channel, ClientPlayNetworking.PlayPayloadHandler<ByteBufPayload> handler) {
        CustomPacketPayload.Type<ByteBufPayload> type = getOrCreateType(channel, true);
        ClientPlayNetworking.registerGlobalReceiver(type, handler);
    }

    public static void sendToServer(ResourceLocation channel, FriendlyByteBuf buf) {
        CustomPacketPayload.Type<ByteBufPayload> type = getOrCreateType(channel, false);
        ClientPlayNetworking.send(new ByteBufPayload(type, readBytes(buf)));
    }

    public static void sendToClient(ResourceLocation channel, FriendlyByteBuf buf, ServerPlayer player) {
        CustomPacketPayload.Type<ByteBufPayload> type = getOrCreateType(channel, false);
        ServerPlayNetworking.send(player, new ByteBufPayload(type, readBytes(buf)));
    }

    public static void sendToAll(ResourceLocation channel, FriendlyByteBuf buf, Iterable<ServerPlayer> players) {
        CustomPacketPayload.Type<ByteBufPayload> type = getOrCreateType(channel, false);
        ByteBufPayload payload = new ByteBufPayload(type, readBytes(buf));
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static boolean canSendToClient(ServerPlayer player, ResourceLocation channel) {
        return ServerPlayNetworking.canSend(player, channel);
    }

    public static FriendlyByteBuf createBuf(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writer.accept(buf);
        return buf;
    }

    private static byte[] readBytes(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }

    private static CustomPacketPayload.Type<ByteBufPayload> getOrCreateType(ResourceLocation channel, boolean registerPayload) {
        return CHANNEL_TYPES.computeIfAbsent(channel, ch -> {
            CustomPacketPayload.Type<ByteBufPayload> type = new CustomPacketPayload.Type<>(ch);
            if (registerPayload) {
                StreamCodec<FriendlyByteBuf, ByteBufPayload> codec = StreamCodec.of(
                    (b, payload) -> b.writeBytes(payload.bytes()),
                    b -> {
                        byte[] data = new byte[b.readableBytes()];
                        b.readBytes(data);
                        CustomPacketPayload.Type<ByteBufPayload> t = CHANNEL_TYPES.get(ch);
                        return new ByteBufPayload(t, data);
                    }
                );
                PayloadTypeRegistry.playC2S().register(type, codec);
                PayloadTypeRegistry.playS2C().register(type, codec);
            }
            return type;
        });
    }

    public record ByteBufPayload(
        CustomPacketPayload.Type<ByteBufPayload> resolvedType,
        byte[] bytes
    ) implements CustomPacketPayload {
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return resolvedType;
        }
    }
}
