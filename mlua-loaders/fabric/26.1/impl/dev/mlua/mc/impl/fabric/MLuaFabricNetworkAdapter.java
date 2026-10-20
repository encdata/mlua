package dev.mlua.mc.impl.fabric;

import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Fabric 26.1 custom-payload registration and send helpers. */
public final class MLuaFabricNetworkAdapter {
    private static final Map<Identifier, CustomPacketPayload.Type<ByteBufPayload>> TYPES = new ConcurrentHashMap<>();

    private MLuaFabricNetworkAdapter() { }

    public static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static void registerC2S(Identifier channel,
                                   ServerPlayNetworking.PlayPayloadHandler<ByteBufPayload> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type(channel, true), handler);
    }

    public static void registerS2C(Identifier channel,
                                   ClientPlayNetworking.PlayPayloadHandler<ByteBufPayload> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type(channel, true), handler);
    }

    public static void sendToServer(Identifier channel, FriendlyByteBuf buffer) {
        ClientPlayNetworking.send(new ByteBufPayload(type(channel, false), consume(buffer)));
    }

    public static void sendToClient(Identifier channel, FriendlyByteBuf buffer, ServerPlayer player) {
        ServerPlayNetworking.send(player, new ByteBufPayload(type(channel, false), consume(buffer)));
    }

    public static void sendToAll(Identifier channel, FriendlyByteBuf buffer, Iterable<ServerPlayer> players) {
        ByteBufPayload payload = new ByteBufPayload(type(channel, false), consume(buffer));
        for (ServerPlayer player : players) ServerPlayNetworking.send(player, payload);
    }

    public static boolean canSendToClient(ServerPlayer player, Identifier channel) {
        return ServerPlayNetworking.canSend(player, channel);
    }

    public static FriendlyByteBuf createBuf(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            return buffer;
        } catch (RuntimeException | Error failure) {
            buffer.release();
            throw failure;
        }
    }

    private static byte[] consume(FriendlyByteBuf buffer) {
        try {
            return copyReadableBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    /** Copies and advances the codec buffer; Minecraft retains ownership of its reference count. */
    private static byte[] copyReadableBytes(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    private static CustomPacketPayload.Type<ByteBufPayload> type(Identifier id, boolean register) {
        return TYPES.computeIfAbsent(id, key -> {
            CustomPacketPayload.Type<ByteBufPayload> type = new CustomPacketPayload.Type<>(key);
            if (register) {
                StreamCodec<RegistryFriendlyByteBuf, ByteBufPayload> codec = StreamCodec.of(
                    (buffer, payload) -> buffer.writeBytes(payload.bytes()),
                    buffer -> new ByteBufPayload(type, copyReadableBytes(buffer)));
                PayloadTypeRegistry.serverboundPlay().register(type, codec);
                PayloadTypeRegistry.clientboundPlay().register(type, codec);
            }
            return type;
        });
    }

    public record ByteBufPayload(CustomPacketPayload.Type<ByteBufPayload> resolvedType, byte[] bytes)
        implements CustomPacketPayload {
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return this.resolvedType;
        }
    }
}
