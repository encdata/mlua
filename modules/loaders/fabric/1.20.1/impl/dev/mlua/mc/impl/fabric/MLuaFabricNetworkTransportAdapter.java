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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;

public final class MLuaFabricNetworkTransportAdapter implements MLuaNetworkTransportAdapter {
   private final Set<UUID> mluaClients = ConcurrentHashMap.newKeySet();
   private volatile boolean serverHasMLua;
   private ResourceLocation channelId;

   @Override
   public Transport transport() {
      return Transport.FABRIC;
   }

   @Override
   public void init(MLuaNetworkManager manager, String protocolVersion) {
      this.channelId = MLuaResourceLocations.build("mlua", "main_network");
      if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
         MLuaFabricNetworkAdapter.registerC2S(this.channelId, (server, player, handler, buf, responseSender) -> {
            this.mluaClients.add(player.getUUID());
            manager.handleServerPacket(server, player, buf);
         });
         MLuaFabricNetworkEvents.registerServer(
            this::sendHandshakeToClient,
            player -> this.mluaClients.remove(player.getUUID())
         );
      } else {
         MLuaFabricNetworkAdapter.registerS2C(this.channelId, (client, handler, buf, responseSender) -> {
            this.serverHasMLua = true;
            manager.handleClientPacket(client, buf);
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
      MLuaFabricNetworkAdapter.sendToServer(this.channelId, MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_NETWORK, (buf) -> {
         buf.writeUtf(remoteName, 128);
         MLuaNetworkWireCodec.writeArgumentList(buf, arguments);
      }));
   }

   @Override
   public void sendRemoteToClient(ServerPlayer player, String remoteName, List<MLuaNetworkValue> arguments) {
      MLuaFabricNetworkAdapter.sendToClient(this.channelId, MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_NETWORK, (buf) -> {
         buf.writeUtf(remoteName, 128);
         MLuaNetworkWireCodec.writeArgumentList(buf, arguments);
      }), player);
   }

   @Override
   public void sendReloadToClient(ServerPlayer player) {
      MLuaFabricNetworkAdapter.sendToClient(this.channelId, MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_RELOAD, (buf) -> {
      }), player);
   }

   @Override
   public void sendClientCommandToClient(ServerPlayer player, String commandName, List<MLuaNetworkValue> arguments) {
      MLuaFabricNetworkAdapter.sendToClient(this.channelId, MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_CLIENT_COMMAND, (buf) -> {
         buf.writeUtf(commandName, 128);
         MLuaNetworkWireCodec.writeArgumentList(buf, arguments);
      }), player);
   }

   @Override
   public void sendAnimationSyncToClient(ServerPlayer player, MLuaAnimationSyncPacket packet) {
      MLuaFabricNetworkAdapter.sendToClient(this.channelId, MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_ANIMATION_SYNC, packet::encode), player);
   }

   @Override
   public void sendDataSyncToClient(ServerPlayer player, MLuaDataSyncPacket packet) {
      MLuaFabricNetworkAdapter.sendToClient(this.channelId, MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_DATA_SYNC, packet::encode), player);
   }

   private void sendHandshakeToServer() {
      try {
         MLuaFabricNetworkAdapter.sendToServer(this.channelId, MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_HANDSHAKE, (buf) -> {
         }));
      } catch (RuntimeException exception) {
         MLuaLogger.LOGGER.debug("[MLua] Could not send Fabric client handshake yet", exception);
      }
   }

   private void sendHandshakeToClient(ServerPlayer player) {
      try {
         MLuaFabricNetworkAdapter.sendToClient(this.channelId, MLuaNetworkWireCodec.createPayload(MLuaNetworkWireCodec.TYPE_HANDSHAKE, (buf) -> {
         }), player);
      } catch (RuntimeException exception) {
         MLuaLogger.LOGGER.debug("[MLua] Could not send Fabric server handshake to {}", player.getGameProfile().getName(), exception);
      }
   }
}
