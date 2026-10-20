package dev.mlua.minecraft.network;

import net.minecraft.world.entity.Entity;

import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaThreadHandoff;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.minecraft.command.MLuaCommandRegistry;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.runtime.data.MLuaDataSyncPacket;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaError;

public final class MLuaNetworkManager {
   private static final String PROTOCOL_VERSION = "2";
   private static final String[] TRANSPORT_ADAPTER_CLASSES = {
      "dev.mlua.mc.impl.forge.MLuaForgeNetworkTransportAdapter",
      "dev.mlua.mc.impl.fabric.MLuaFabricNetworkTransportAdapter"
   };
   private static final Pattern REMOTE_NAME = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
   private static final MLuaNetworkManager INSTANCE = new MLuaNetworkManager();
   private final Map<String, MLuaRemoteEvent> remotes = new ConcurrentHashMap();
   private final Set<String> missingRemoteWarnings = ConcurrentHashMap.newKeySet();
   private volatile boolean initialized;
   private MLuaNetworkTransportAdapter transportAdapter;
   private MLuaNetworkTransportAdapter.Transport transport;

   public static MLuaNetworkManager get() {
      return INSTANCE;
   }

   public synchronized void init() {
      if (this.initialized) {
         return;
      }

      ClassLoader[] candidateLoaders = {
         Thread.currentThread().getContextClassLoader(),
         MLuaNetworkManager.class.getClassLoader(),
         ClassLoader.getSystemClassLoader()
      };

      for (String className : TRANSPORT_ADAPTER_CLASSES) {
         for (ClassLoader loader : candidateLoaders) {
            if (loader == null) {
               continue;
            }

            try {
               Class<?> adapterClass = Class.forName(className, true, loader);
               Object instance = adapterClass.getDeclaredConstructor().newInstance();
               if (!(instance instanceof MLuaNetworkTransportAdapter adapter)) {
                  continue;
               }

               adapter.init(this, PROTOCOL_VERSION);
               this.transportAdapter = adapter;
               this.transport = adapter.transport();
               this.initialized = true;
               return;
            } catch (ClassNotFoundException ignored) {
            } catch (ReflectiveOperationException | LinkageError exception) {
               MLuaLogger.LOGGER.warn("[MLua] Failed to initialize network transport adapter {}", className, exception);
               this.initialized = true;
               return;
            }
         }
      }

      MLuaLogger.LOGGER.warn("[MLua] No supported networking transport adapter detected.");
      this.initialized = true;
   }

   public MLuaRemoteEvent getRemote(String name) {
      this.validateRemoteName(name);
      return this.remotes.computeIfAbsent(name, MLuaRemoteEvent::new);
   }

   public boolean hasServerMLua() {
      return this.transportAdapter != null && this.transportAdapter.hasServerMLua();
   }

   public boolean hasClientMLua(ServerPlayer player) {
      return this.transportAdapter != null && this.transportAdapter.hasClientMLua(player);
   }

   public boolean sendToServer(String remoteName, List<MLuaNetworkValue> arguments) {
      return this.sendToServer(remoteName, arguments, false);
   }

   public boolean sendToServer(String remoteName, List<MLuaNetworkValue> arguments, boolean optional) {
      this.validateRemoteName(remoteName);
      if (!this.hasServerMLua()) {
         this.handleMissingRemote(optional, "server:" + remoteName, "[MLua] Skipped remote '" + remoteName + "' because the server does not have MLua.");
         return false;
      }

      try {
         this.transportAdapter.sendRemoteToServer(remoteName, arguments);
         return true;
      } catch (Exception e) {
         this.handleSendFailure(optional, "server:" + remoteName, "[MLua] Could not send '" + remoteName + "' to server", e);
         return false;
      }
   }

   public boolean sendToClient(ServerPlayer player, String remoteName, List<MLuaNetworkValue> arguments) {
      return this.sendToClient(player, remoteName, arguments, false);
   }

   public boolean sendToClient(ServerPlayer player, String remoteName, List<MLuaNetworkValue> arguments, boolean optional) {
      this.validateRemoteName(remoteName);
      if (!this.hasClientMLua(player)) {
         String playerName = player != null ? player.getGameProfile().getName() : "<null>";
         String playerId = player != null ? String.valueOf(player.getUUID()) : "unknown";
         this.handleMissingRemote(optional, "client:" + playerId + ":" + remoteName, "[MLua] Skipped remote '" + remoteName + "' for player '" + playerName + "' because that client does not have MLua.");
         return false;
      }

      try {
         this.transportAdapter.sendRemoteToClient(player, remoteName, arguments);
         return true;
      } catch (Exception e) {
         String playerName = player != null ? player.getGameProfile().getName() : "<null>";
         String playerId = player != null ? String.valueOf(player.getUUID()) : "unknown";
         this.handleSendFailure(optional, "client:" + playerId + ":" + remoteName, "[MLua] Could not send '" + remoteName + "' to client '" + playerName + "'", e);
         return false;
      }
   }

   public boolean sendToAllClients(String remoteName, List<MLuaNetworkValue> arguments) {
      return this.sendToAllClients(remoteName, arguments, false);
   }

   public boolean sendToAllClients(String remoteName, List<MLuaNetworkValue> arguments, boolean optional) {
      return this.sendToAllClients(MLuaRuntime.get().server(), remoteName, arguments, optional);
   }

   public boolean sendToAllClients(MinecraftServer server, String remoteName, List<MLuaNetworkValue> arguments, boolean optional) {
      this.validateRemoteName(remoteName);
      if (server == null) {
         this.handleMissingRemote(optional, "all:" + remoteName, "[MLua] Skipped remote '" + remoteName + "' because no server is active.");
         return false;
      }

      boolean sent = false;
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         sent |= this.sendToClient(player, remoteName, arguments, optional);
      }
      return sent;
   }

   public boolean sendClientReload(ServerPlayer player) {
      try {
         if (!this.hasClientMLua(player)) {
            return false;
         }

         this.transportAdapter.sendReloadToClient(player);
         return true;
      } catch (Exception e) {
         MLuaLogger.LOGGER.warn("[MLua] Could not send reload packet to client (missing mod)");
         return false;
      }
   }

   public boolean sendCommandToClient(ServerPlayer player, String commandName, List<MLuaNetworkValue> arguments) {
      try {
         if (!this.hasClientMLua(player)) {
            return false;
         }

         this.transportAdapter.sendClientCommandToClient(player, commandName, arguments);
         return true;
      } catch (Exception e) {
         MLuaLogger.LOGGER.warn("[MLua] Could not send command callback '{}' to client (missing mod)", commandName);
         return false;
      }
   }

   public boolean sendReloadToAll() {
      return this.sendReloadToAll(MLuaRuntime.get().server());
   }

   public boolean sendReloadToAll(MinecraftServer server) {
      if (server == null) {
         return false;
      }

      boolean sent = false;
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         sent |= this.sendClientReload(player);
      }
      return sent;
   }

   public void sendAnimationSync(MLuaAnimationSyncPacket packet) {
      this.sendAnimationSync(packet, MLuaRuntime.get().server());
   }

   public void sendAnimationSync(MLuaAnimationSyncPacket packet, MinecraftServer server) {
      if (server == null) {
         return;
      }

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (!player.level().dimension().location().equals(packet.dimension())) {
            continue;
         }
         this.sendAnimationSyncToPlayer(player, packet);
      }
   }

   public void sendAnimationSyncToPlayer(ServerPlayer player, MLuaAnimationSyncPacket packet) {
      try {
         if (!this.hasClientMLua(player)) {
            return;
         }

         this.transportAdapter.sendAnimationSyncToClient(player, packet);
      } catch (Exception e) {
         MLuaLogger.LOGGER.warn("[MLua] Could not send animation sync to player", e);
      }
   }

   public boolean sendDataSyncToPlayer(ServerPlayer player, MLuaDataSyncPacket packet) {
      try {
         if (!this.hasClientMLua(player)) {
            return false;
         }

         this.transportAdapter.sendDataSyncToClient(player, packet);
         return true;
      } catch (Exception e) {
         MLuaLogger.LOGGER.warn("[MLua] Could not send synced data to player", e);
         return false;
      }
   }

   public boolean sendDataSyncToAll(MLuaDataSyncPacket packet) {
      return this.sendDataSyncToAll(packet, MLuaRuntime.get().server());
   }

   public boolean sendDataSyncToAll(MLuaDataSyncPacket packet, MinecraftServer server) {
      if (server == null) {
         return false;
      }

      boolean sent = false;
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         sent |= this.sendDataSyncToPlayer(player, packet);
      }

      return sent;
   }

   public void validateRemoteName(String name) {
      if (name == null || !REMOTE_NAME.matcher(name).matches()) {
         throw new LuaError("[MLua] Invalid Network remote name: " + name);
      }
   }

   public void handleServerPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
      int type;
      try {
         type = buf.readByte();
      } catch (Exception e) {
         return;
      }

      switch (type) {
         case MLuaNetworkWireCodec.TYPE_NETWORK -> {
            String remoteName = buf.readUtf(128);
            List<MLuaNetworkValue> arguments = MLuaNetworkWireCodec.readArgumentList(buf);
            MLuaThreadHandoff.execute(server::isSameThread, server,
               () -> this.receiveRemoteFromClient(player, remoteName, arguments));
         }
         case MLuaNetworkWireCodec.TYPE_HANDSHAKE -> MLuaThreadHandoff.execute(server::isSameThread, server,
            () -> MLuaDataManager.syncInitialState(player));
         default -> MLuaLogger.LOGGER.warn("[MLua] Unknown server packet type: {}", type);
      }
   }

   public void handleClientPacket(Minecraft client, FriendlyByteBuf buf) {
      int type;
      try {
         type = buf.readByte();
      } catch (Exception e) {
         return;
      }

      switch (type) {
         case MLuaNetworkWireCodec.TYPE_NETWORK -> {
            String remoteName = buf.readUtf(128);
            List<MLuaNetworkValue> arguments = MLuaNetworkWireCodec.readArgumentList(buf);
            MLuaThreadHandoff.execute(client::isSameThread, client,
               () -> this.receiveRemoteFromServer(remoteName, arguments));
         }
         case MLuaNetworkWireCodec.TYPE_RELOAD -> MLuaThreadHandoff.execute(client::isSameThread, client, this::receiveClientReload);
         case MLuaNetworkWireCodec.TYPE_CLIENT_COMMAND -> {
            String commandName = buf.readUtf(128);
            List<MLuaNetworkValue> arguments = MLuaNetworkWireCodec.readArgumentList(buf);
            MLuaThreadHandoff.execute(client::isSameThread, client,
               () -> this.receiveClientCommand(commandName, arguments));
         }
         case MLuaNetworkWireCodec.TYPE_ANIMATION_SYNC -> {
            byte[] animationData = new byte[buf.readableBytes()];
            buf.readBytes(animationData);
            MLuaThreadHandoff.execute(client::isSameThread, client,
               () -> this.handleClientAnimationSync(animationData));
         }
         case MLuaNetworkWireCodec.TYPE_DATA_SYNC -> {
            byte[] syncData = new byte[buf.readableBytes()];
            buf.readBytes(syncData);
            MLuaThreadHandoff.execute(client::isSameThread, client,
               () -> this.handleClientDataSync(syncData));
         }
         case MLuaNetworkWireCodec.TYPE_HANDSHAKE -> {
         }
         default -> MLuaLogger.LOGGER.warn("[MLua] Unknown client packet type: {}", type);
      }
   }

   public void receiveRemoteFromClient(ServerPlayer sender, String remoteName, List<MLuaNetworkValue> arguments) {
      try {
         this.validateRemoteName(remoteName);
         this.getRemote(remoteName).receiveFromClient(sender, arguments);
      } catch (RuntimeException e) {
         MLuaLogger.LOGGER.error("[MLua] Failed to handle MLua Network packet", e);
      }
   }

   public void receiveRemoteFromServer(String remoteName, List<MLuaNetworkValue> arguments) {
      try {
         this.validateRemoteName(remoteName);
         this.getRemote(remoteName).receiveFromServer(arguments);
      } catch (RuntimeException e) {
         MLuaLogger.LOGGER.error("[MLua] Failed to handle MLua Network packet", e);
      }
   }

   public void receiveClientReload() {
      MLuaRuntime.get().reloadClientOnly();
   }

   public void receiveClientCommand(String commandName, List<MLuaNetworkValue> arguments) {
      MLuaCommandRegistry.get().executeClient(commandName, arguments, MLuaRuntime.get());
   }

   public void receiveClientAnimationSync(MLuaAnimationSyncPacket packet) {
      this.applyAnimationSync(packet);
   }

   public void receiveClientDataSync(MLuaDataSyncPacket packet) {
      MLuaDataManager.handleClientSync(packet);
   }

   private void applyAnimationSync(MLuaAnimationSyncPacket packet) {
      if (Minecraft.getInstance().level == null
         || !Minecraft.getInstance().level.dimension().location().equals(packet.dimension())) return;
      Entity entity = Minecraft.getInstance().level.getEntity(packet.entityId());
      if (entity == null || !entity.getUUID().equals(packet.entityUuid())) return;
      Map<String, MLuaCFrame> poses = new java.util.HashMap<>();
      for (MLuaAnimationSyncPacket.BonePoseEntry entry : packet.poses()) {
         poses.put(entry.boneName(), MLuaCFrame.at(entry.x(), entry.y(), entry.z())
            .mul(MLuaCFrame.angles(entry.xRot(), entry.yRot(), entry.zRot())));
      }
      MLuaBonePose.replaceSyncedPose(MLuaEntityScope.of(entity), poses);
   }

   private void handleClientAnimationSync(byte[] animationData) {
      FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(animationData));

      try {
         this.applyAnimationSync(new MLuaAnimationSyncPacket(payload));
      } catch (RuntimeException e) {
         MLuaLogger.LOGGER.warn("[MLua] Failed to decode client animation sync payload", e);
      } finally {
         ReferenceCountUtil.safeRelease(payload);
      }
   }

   private void handleClientDataSync(byte[] syncData) {
      FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(syncData));

      try {
         MLuaDataManager.handleClientSync(new MLuaDataSyncPacket(payload));
      } catch (RuntimeException e) {
         MLuaLogger.LOGGER.warn("[MLua] Failed to decode client data sync payload", e);
      } finally {
         ReferenceCountUtil.safeRelease(payload);
      }
   }

   private void handleMissingRemote(boolean optional, String key, String message) {
      if (!optional) {
         this.warnMissingRemoteOnce(key, message);
      }
   }

   private void handleSendFailure(boolean optional, String key, String message, Throwable exception) {
      if (!optional && this.missingRemoteWarnings.add(key)) {
         MLuaLogger.LOGGER.warn(message + " ({})", exception.getMessage());
      }
   }

   private void warnMissingRemoteOnce(String key, String message) {
      if (this.missingRemoteWarnings.add(key)) {
         MLuaLogger.LOGGER.warn(message);
      }
   }

   public Connection resolveServerPlayerConnection(ServerPlayer player) {
      if (player == null || player.connection == null) {
         return null;
      }

      Object listener = player.connection;
      try {
         Field field = this.findField(listener.getClass(), "connection");
         if (field != null) {
            Object value = field.get(listener);
            return value instanceof Connection connection ? connection : null;
         }
      } catch (ReflectiveOperationException ignored) {
      }

      try {
         java.lang.reflect.Method method = listener.getClass().getMethod("getConnection");
         Object value = method.invoke(listener);
         return value instanceof Connection connection ? connection : null;
      } catch (ReflectiveOperationException ignored) {
         return null;
      }
   }

   public Connection resolveClientConnection() {
      try {
         ClientPacketListener listener = MLuaMinecraftClient.instance().getConnection();
         if (listener == null) {
            return null;
         }

         try {
            java.lang.reflect.Method method = listener.getClass().getMethod("getConnection");
            Object value = method.invoke(listener);
            return value instanceof Connection connection ? connection : null;
         } catch (ReflectiveOperationException ignored) {
         }

         try {
            Field field = this.findField(listener.getClass(), "connection");
            if (field != null) {
               Object value = field.get(listener);
               return value instanceof Connection connection ? connection : null;
            }
         } catch (ReflectiveOperationException ignored) {
         }

         return null;
      } catch (Throwable ignored) {
         return null;
      }
   }

   private Field findField(Class<?> type, String name) {
      Class<?> current = type;

      while(current != null) {
         try {
            Field field = current.getDeclaredField(name);
            field.setAccessible(true);
            return field;
         } catch (NoSuchFieldException ignored) {
            current = current.getSuperclass();
         }
      }

      return null;
   }
}
