package dev.mlua.mc.impl.forge;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

public final class MLuaForgeNetworkAdapter {
    private static final String DEFAULT_PROTOCOL_VERSION = "1";

    private MLuaForgeNetworkAdapter() {
    }

    public static SimpleChannel createChannel(ResourceLocation name) {
        return createChannel(name, DEFAULT_PROTOCOL_VERSION);
    }

    public static SimpleChannel createChannel(ResourceLocation name, String protocolVersion) {
        int version = Integer.parseInt(protocolVersion);
        return ChannelBuilder.named(name)
            .networkProtocolVersion(version)
            .optional()
            .simpleChannel();
    }

    public static SimpleChannel createChannel(ResourceLocation name, String protocolVersion,
                                              Predicate<String> clientAcceptedVersions,
                                              Predicate<String> serverAcceptedVersions) {
        int version = Integer.parseInt(protocolVersion);
        return ChannelBuilder.named(name)
            .networkProtocolVersion(version)
            .clientAcceptedVersions((status, remoteVersion) -> clientAcceptedVersions.test(Integer.toString(remoteVersion)))
            .serverAcceptedVersions((status, remoteVersion) -> serverAcceptedVersions.test(Integer.toString(remoteVersion)))
            .simpleChannel();
    }

    public static <T> void registerC2SPacket(SimpleChannel channel, Class<T> packetClass, int id,
                                             BiConsumer<T, FriendlyByteBuf> encoder,
                                             Function<FriendlyByteBuf, T> decoder,
                                             BiConsumer<T, Supplier<?>> handler) {
        channel.messageBuilder(packetClass, id)
            .encoder(encoder)
            .decoder(decoder)
            .consumer((packet, context) -> handler.accept(packet, () -> new LegacyContext(context)))
            .direction(PacketFlow.SERVERBOUND)
            .add();
    }

    public static <T> void registerS2CPacket(SimpleChannel channel, Class<T> packetClass, int id,
                                             BiConsumer<T, FriendlyByteBuf> encoder,
                                             Function<FriendlyByteBuf, T> decoder,
                                             BiConsumer<T, Supplier<?>> handler) {
        channel.messageBuilder(packetClass, id)
            .encoder(encoder)
            .decoder(decoder)
            .consumer((packet, context) -> handler.accept(packet, () -> new LegacyContext(context)))
            .direction(PacketFlow.CLIENTBOUND)
            .add();
    }

    public static <T> void registerPacket(SimpleChannel channel, Class<T> packetClass, int id,
                                          BiConsumer<T, FriendlyByteBuf> encoder,
                                          Function<FriendlyByteBuf, T> decoder,
                                          BiConsumer<T, Supplier<?>> handler) {
        channel.messageBuilder(packetClass, id)
            .encoder(encoder)
            .decoder(decoder)
            .consumer((packet, context) -> handler.accept(packet, () -> new LegacyContext(context)))
            .add();
    }

    public static Predicate<String> acceptMissingOr(String protocolVersion) {
        return version -> version == null || Objects.equals(version, protocolVersion);
    }

    public enum LegacyDirection {
        PLAY_TO_SERVER,
        PLAY_TO_CLIENT
    }

    public static final class LegacyContext {
        private final CustomPayloadEvent.Context inner;

        private LegacyContext(CustomPayloadEvent.Context inner) {
            this.inner = inner;
        }

        public java.util.concurrent.CompletableFuture<Void> enqueueWork(Runnable task) {
            return inner.enqueueWork(task);
        }

        public void setPacketHandled(boolean handled) {
            inner.setPacketHandled(handled);
        }

        public ServerPlayer getSender() {
            return inner.getSender();
        }

        public LegacyDirection getDirection() {
            return inner.isServerSide() ? LegacyDirection.PLAY_TO_SERVER : LegacyDirection.PLAY_TO_CLIENT;
        }
    }
}
