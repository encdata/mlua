package dev.mlua.mc.impl.forge;

import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.network.MLuaAnimationSyncPacket;
import dev.mlua.minecraft.network.MLuaClientCommandPacket;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.minecraft.network.MLuaNetworkPacket;
import dev.mlua.minecraft.network.MLuaNetworkTransportAdapter;
import dev.mlua.minecraft.network.MLuaNetworkValue;
import dev.mlua.minecraft.network.MLuaReloadPacket;
import dev.mlua.minecraft.network.MLuaNetworkSerializer;
import dev.mlua.runtime.data.MLuaDataSyncPacket;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MLuaForgeNetworkTransportAdapter implements MLuaNetworkTransportAdapter {
   private SimpleChannel channel;
   private MLuaNetworkManager manager;

   @Override
   public Transport transport() {
      return Transport.FORGE;
   }

   @Override
   public void init(MLuaNetworkManager manager, String protocolVersion) {
      this.manager = manager;
      this.channel = MLuaForgeNetworkAdapter.createChannel(MLuaResourceLocations.build("mlua", "main"), protocolVersion);
      MLuaForgeNetworkAdapter.registerC2SPacket(this.channel, MLuaNetworkPacket.class, 0, MLuaNetworkSerializer::encode, MLuaNetworkSerializer::decode, this::handleServerRemote);
      MLuaForgeNetworkAdapter.registerS2CPacket(this.channel, MLuaNetworkPacket.class, 1, MLuaNetworkSerializer::encode, MLuaNetworkSerializer::decode, this::handleClientRemote);
      MLuaForgeNetworkAdapter.registerS2CPacket(this.channel, MLuaReloadPacket.class, 2, MLuaReloadPacket::encode, MLuaReloadPacket::new, this::handleClientReload);
      MLuaForgeNetworkAdapter.registerS2CPacket(this.channel, MLuaClientCommandPacket.class, 3, MLuaClientCommandPacket::encode, MLuaClientCommandPacket::new, this::handleClientCommand);
      MLuaForgeNetworkAdapter.registerS2CPacket(this.channel, MLuaAnimationSyncPacket.class, 4, MLuaAnimationSyncPacket::encode, MLuaAnimationSyncPacket::new, this::handleClientAnimationSync);
      MLuaForgeNetworkAdapter.registerS2CPacket(this.channel, MLuaDataSyncPacket.class, 5, MLuaDataSyncPacket::encode, MLuaDataSyncPacket::new, this::handleClientDataSync);
   }

   @Override
   public boolean hasServerMLua() {
      Connection connection = this.manager.resolveClientConnection();
      return connection != null && this.channel != null && this.channel.isRemotePresent(connection);
   }

   @Override
   public boolean hasClientMLua(ServerPlayer player) {
      Connection connection = this.manager.resolveServerPlayerConnection(player);
      return connection != null && this.channel != null && this.channel.isRemotePresent(connection);
   }

   @Override
   public void sendRemoteToServer(String remoteName, List<MLuaNetworkValue> arguments) {
      this.channel.sendToServer(new MLuaNetworkPacket(remoteName, arguments));
   }

   @Override
   public void sendRemoteToClient(ServerPlayer player, String remoteName, List<MLuaNetworkValue> arguments) {
      this.channel.send(PacketDistributor.PLAYER.with(() -> player), new MLuaNetworkPacket(remoteName, arguments));
   }

   @Override
   public void sendReloadToClient(ServerPlayer player) {
      this.channel.send(PacketDistributor.PLAYER.with(() -> player), new MLuaReloadPacket());
   }

   @Override
   public void sendClientCommandToClient(ServerPlayer player, String commandName, List<MLuaNetworkValue> arguments) {
      this.channel.send(PacketDistributor.PLAYER.with(() -> player), new MLuaClientCommandPacket(commandName, arguments));
   }

   @Override
   public void sendAnimationSyncToClient(ServerPlayer player, MLuaAnimationSyncPacket packet) {
      this.channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
   }

   @Override
   public void sendDataSyncToClient(ServerPlayer player, MLuaDataSyncPacket packet) {
      this.channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
   }

   private void handleServerRemote(MLuaNetworkPacket packet, java.util.function.Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> {
         ServerPlayer sender = context.getSender();
         if (sender == null) {
            MLuaLogger.LOGGER.warn("MLua Network packet '{}' had no server sender", packet.remoteName());
            return;
         }
         this.manager.receiveRemoteFromClient(sender, packet.remoteName(), packet.arguments());
      });
      context.setPacketHandled(true);
   }

   private void handleClientRemote(MLuaNetworkPacket packet, java.util.function.Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> this.manager.receiveRemoteFromServer(packet.remoteName(), packet.arguments()));
      context.setPacketHandled(true);
   }

   private void handleClientReload(MLuaReloadPacket packet, java.util.function.Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(this.manager::receiveClientReload);
      context.setPacketHandled(true);
   }

   private void handleClientCommand(MLuaClientCommandPacket packet, java.util.function.Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> this.manager.receiveClientCommand(packet.commandName(), packet.arguments()));
      context.setPacketHandled(true);
   }

   private void handleClientAnimationSync(MLuaAnimationSyncPacket packet, java.util.function.Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> this.manager.receiveClientAnimationSync(packet));
      context.setPacketHandled(true);
   }

   private void handleClientDataSync(MLuaDataSyncPacket packet, java.util.function.Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> this.manager.receiveClientDataSync(packet));
      context.setPacketHandled(true);
   }
}
