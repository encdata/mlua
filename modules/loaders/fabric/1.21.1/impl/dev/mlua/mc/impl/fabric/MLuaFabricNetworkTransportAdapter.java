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
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;

public final class MLuaFabricNetworkTransportAdapter implements MLuaNetworkTransportAdapter {
   private final Set<UUID> mluaClients = ConcurrentHashMap.newKeySet();
   private volatile boolean serverHasMLua;
   private int handshakeRetryTicks;
   private ResourceLocation channelId;

   @Override
   public Transport transport() {
      return Transport.FABRIC;
   }

   @Override
   public void init(MLuaNetworkManager manager, String protocolVersion) {
      this.channelId = MLuaResourceLocations.build("mlua", "main_network");
      MLuaFabricNetworkAdapter.registerC2S(this.channelId, (payload, context) -> {
         this.mluaClients.add(context.player().getUUID());
         byte[] bytes = payload.bytes();
         boolean handshake = bytes.length > 0 && bytes[0] == MLuaNetworkWireCodec.TYPE_HANDSHAKE;
         FriendlyByteBuf buf = this.wrap(bytes);
         try {
            manager.handleServerPacket(context.server(), context.player(), buf);
         } finally {
            buf.release();
         }
         if (handshake) context.server().execute(() -> this.sendHandshakeToClient(context.player()));
      });
      MLuaFabricNetworkEvents.registerServer(
         this::sendHandshakeToClient,
         player -> this.mluaClients.remove(player.getUUID())
      );
      if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
         MLuaFabricNetworkAdapter.registerS2C(this.channelId, (payload, context) -> {
            this.serverHasMLua = true;
            FriendlyByteBuf buf = this.wrap(payload.bytes());
            try {
               manager.handleClientPacket(context.client(), buf);
            } finally {
               buf.release();
            }
         });
         MLuaFabricNetworkEvents.registerClient(
            () -> {
               this.serverHasMLua = false;
               this.handshakeRetryTicks = 0;
               this.sendHandshakeToServer();
            },
            () -> {
               this.serverHasMLua = false;
               this.handshakeRetryTicks = 0;
            },
            this::retryServerHandshake
         );
      }
   }

   private void retryServerHandshake() {
      if (this.serverHasMLua || !MLuaFabricNetworkAdapter.canSendToServer(this.channelId)) {
         this.handshakeRetryTicks = 0;
         return;
      }
      if (++this.handshakeRetryTicks >= 20) {
         this.handshakeRetryTicks = 0;
         this.sendHandshakeToServer();
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
         if (!MLuaFabricNetworkAdapter.canSendToServer(this.channelId)) return;
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

   private FriendlyByteBuf wrap(byte[] bytes) {
      return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
   }
}
