package dev.mlua.mc.impl.forge;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MLuaForgeNetworkAdapter {
    private static final String DEFAULT_PROTOCOL_VERSION = "1";

    private MLuaForgeNetworkAdapter() {
    }

    public static SimpleChannel createChannel(ResourceLocation name) {
        return createChannel(name, DEFAULT_PROTOCOL_VERSION);
    }

    public static SimpleChannel createChannel(ResourceLocation name, String protocolVersion) {
        return NetworkRegistry.newSimpleChannel(
            name,
            () -> protocolVersion,
            version -> version.equals(protocolVersion) || NetworkRegistry.ACCEPTVANILLA.equals(version),
            version -> version.equals(protocolVersion) || NetworkRegistry.ACCEPTVANILLA.equals(version)
        );
    }

    public static SimpleChannel createChannel(ResourceLocation name, String protocolVersion,
                                              Predicate<String> clientAcceptedVersions,
                                              Predicate<String> serverAcceptedVersions) {
        return NetworkRegistry.newSimpleChannel(name, () -> protocolVersion, clientAcceptedVersions, serverAcceptedVersions);
    }

    public static <T> void registerC2SPacket(SimpleChannel channel, Class<T> packetClass, int id,
                                             BiConsumer<T, FriendlyByteBuf> encoder,
                                             Function<FriendlyByteBuf, T> decoder,
                                             BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        channel.registerMessage(id, packetClass, encoder, decoder, handler, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static <T> void registerS2CPacket(SimpleChannel channel, Class<T> packetClass, int id,
                                             BiConsumer<T, FriendlyByteBuf> encoder,
                                             Function<FriendlyByteBuf, T> decoder,
                                             BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        channel.registerMessage(id, packetClass, encoder, decoder, handler, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static <T> void registerPacket(SimpleChannel channel, Class<T> packetClass, int id,
                                          BiConsumer<T, FriendlyByteBuf> encoder,
                                          Function<FriendlyByteBuf, T> decoder,
                                          BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        channel.registerMessage(id, packetClass, encoder, decoder, handler, Optional.empty());
    }

    public static Predicate<String> acceptMissingOr(String protocolVersion) {
        return NetworkRegistry.acceptMissingOr(protocolVersion);
    }
}
