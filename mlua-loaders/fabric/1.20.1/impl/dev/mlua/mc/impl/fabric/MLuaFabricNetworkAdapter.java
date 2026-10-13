package dev.mlua.mc.impl.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Consumer;

public final class MLuaFabricNetworkAdapter {
    private MLuaFabricNetworkAdapter() {
    }

    public static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public static void registerC2S(ResourceLocation channel, ServerPlayNetworking.PlayChannelHandler handler) {
        ServerPlayNetworking.registerGlobalReceiver(channel, handler);
    }

    public static void registerS2C(ResourceLocation channel, ClientPlayNetworking.PlayChannelHandler handler) {
        ClientPlayNetworking.registerGlobalReceiver(channel, handler);
    }

    public static void sendToServer(ResourceLocation channel, FriendlyByteBuf buf) {
        ClientPlayNetworking.send(channel, buf);
    }

    public static void sendToClient(ResourceLocation channel, FriendlyByteBuf buf, ServerPlayer player) {
        ServerPlayNetworking.send(player, channel, buf);
    }

    public static void sendToAll(ResourceLocation channel, FriendlyByteBuf buf, Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player, channel, buf);
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
}
