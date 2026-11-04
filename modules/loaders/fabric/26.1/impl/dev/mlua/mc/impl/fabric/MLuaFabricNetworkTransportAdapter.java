package dev.mlua.mc.impl.fabric;

import dev.mlua.core.MLuaLogger;
import dev.mlua.loader.impl.fabric.MLuaFabricNetworkEvents;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.network.MLuaAnimationSyncPacket;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.minecraft.network.MLuaNetworkTransportAdapter;
import dev.mlua.minecraft.network.MLuaNetworkValue;
import dev.mlua.minecraft.network.MLuaNetworkWireCodec;
import dev.mlua.runtime.data.MLuaDataSyncPacket;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Connects MLua's shared packet protocol to Fabric 26.1 custom payloads. */
public final class MLuaFabricNetworkTransportAdapter implements MLuaNetworkTransportAdapter {
    private final Set<UUID> mluaClients = ConcurrentHashMap.newKeySet();
    private volatile boolean serverHasMLua;
    private Identifier channelId;

    @Override
    public Transport transport() {
        return Transport.FABRIC;
    }

    @Override
    public void init(MLuaNetworkManager manager, String protocolVersion) {
        this.channelId = MLuaResourceLocations.build("mlua", "main_network");
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            MLuaFabricNetworkAdapter.registerC2S(this.channelId, (payload, context) -> {
                this.mluaClients.add(context.player().getUUID());
                FriendlyByteBuf buf = wrap(payload.bytes());
                try {
                    manager.handleServerPacket(context.server(), context.player(), buf);
                } finally {
                    buf.release();
                }
            });
            MLuaFabricNetworkEvents.registerServer(
                this::sendHandshakeToClient,
                player -> this.mluaClients.remove(player.getUUID())
            );
        } else {
            MLuaFabricNetworkAdapter.registerS2C(this.channelId, (payload, context) -> {
                this.serverHasMLua = true;
                FriendlyByteBuf buf = wrap(payload.bytes());
                try {
                    manager.handleClientPacket(context.client(), buf);
                } finally {
                    buf.release();
                }
            });
            MLuaFabricNetworkEvents.registerClient(
                () -> {
                    this.serverHasMLua = false;
                    this.sendHandshakeToServer();
                },
                () -> this.serverHasMLua = false
            );
        }
    }

    @Override
    public boolean hasServerMLua() {
        return this.serverHasMLua;
    }

    @Override
    public boolean hasClientMLua(ServerPlayer player) {
        return player != null && this.mluaClients.contains(player.getUUID());
    }

    @Override
    public void sendRemoteToServer(String remoteName, List<MLuaNetworkValue> arguments) {
        MLuaFabricNetworkAdapter.sendToServer(this.channelId,
            MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_NETWORK, buf -> {
                buf.writeUtf(remoteName, 128);
                MLuaNetworkWireCodec.writeArgumentList(buf, arguments);
            }));
    }

    @Override
    public void sendRemoteToClient(ServerPlayer player, String remoteName, List<MLuaNetworkValue> arguments) {
        MLuaFabricNetworkAdapter.sendToClient(this.channelId,
            MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_NETWORK, buf -> {
                buf.writeUtf(remoteName, 128);
                MLuaNetworkWireCodec.writeArgumentList(buf, arguments);
            }), player);
    }

    @Override
    public void sendReloadToClient(ServerPlayer player) {
        MLuaFabricNetworkAdapter.sendToClient(this.channelId,
            MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_RELOAD, buf -> {}), player);
    }

    @Override
    public void sendClientCommandToClient(ServerPlayer player, String commandName,
                                          List<MLuaNetworkValue> arguments) {
        MLuaFabricNetworkAdapter.sendToClient(this.channelId,
            MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_CLIENT_COMMAND, buf -> {
                buf.writeUtf(commandName, 128);
                MLuaNetworkWireCodec.writeArgumentList(buf, arguments);
            }), player);
    }

    @Override
    public void sendAnimationSyncToClient(ServerPlayer player, MLuaAnimationSyncPacket packet) {
        MLuaFabricNetworkAdapter.sendToClient(this.channelId,
            MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_ANIMATION_SYNC, packet::encode), player);
    }

    @Override
    public void sendDataSyncToClient(ServerPlayer player, MLuaDataSyncPacket packet) {
        MLuaFabricNetworkAdapter.sendToClient(this.channelId,
            MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_DATA_SYNC, packet::encode), player);
    }

    private void sendHandshakeToServer() {
        try {
            MLuaFabricNetworkAdapter.sendToServer(this.channelId,
                MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_HANDSHAKE, buf -> {}));
        } catch (RuntimeException exception) {
            MLuaLogger.LOGGER.debug("[MLua] Could not send Fabric client handshake yet", exception);
        }
    }

    private void sendHandshakeToClient(ServerPlayer player) {
        try {
            MLuaFabricNetworkAdapter.sendToClient(this.channelId,
                MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_HANDSHAKE, buf -> {}), player);
        } catch (RuntimeException exception) {
            MLuaLogger.LOGGER.debug("[MLua] Could not send Fabric server handshake to {}",
                player.getName().getString(), exception);
        }
    }

    private static FriendlyByteBuf wrap(byte[] bytes) {
        return new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
    }
}
