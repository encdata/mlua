package dev.mlua.mc.impl.forge;

import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.network.MLuaAnimationSyncPacket;
import dev.mlua.minecraft.network.MLuaClientCommandPacket;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.minecraft.network.MLuaNetworkPacket;
import dev.mlua.minecraft.network.MLuaNetworkSerializer;
import dev.mlua.minecraft.network.MLuaNetworkTransportAdapter;
import dev.mlua.minecraft.network.MLuaNetworkValue;
import dev.mlua.minecraft.network.MLuaReloadPacket;
import dev.mlua.runtime.data.MLuaDataSyncPacket;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

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
      MLuaForgeNetworkAdapter.registerPacket(this.channel, MLuaNetworkPacket.class, 0, MLuaNetworkSerializer::encode, MLuaNetworkSerializer::decode, this::handleRemote);
      MLuaForgeNetworkAdapter.registerS2CPacket(this.channel, MLuaReloadPacket.class, 1, MLuaReloadPacket::encode, MLuaReloadPacket::new, this::handleClientReload);
      MLuaForgeNetworkAdapter.registerS2CPacket(this.channel, MLuaClientCommandPacket.class, 2, MLuaClientCommandPacket::encode, MLuaClientCommandPacket::new, this::handleClientCommand);
      MLuaForgeNetworkAdapter.registerS2CPacket(this.channel, MLuaAnimationSyncPacket.class, 3, MLuaAnimationSyncPacket::encode, MLuaAnimationSyncPacket::new, this::handleClientAnimationSync);
      MLuaForgeNetworkAdapter.registerS2CPacket(this.channel, MLuaDataSyncPacket.class, 4, MLuaDataSyncPacket::encode, MLuaDataSyncPacket::new, this::handleClientDataSync);
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
      this.channel.send(new MLuaNetworkPacket(remoteName, arguments), PacketDistributor.SERVER.noArg());
   }

   @Override
   public void sendRemoteToClient(ServerPlayer player, String remoteName, List<MLuaNetworkValue> arguments) {
      this.channel.send(new MLuaNetworkPacket(remoteName, arguments), PacketDistributor.PLAYER.with(player));
   }

   @Override
   public void sendReloadToClient(ServerPlayer player) {
      this.channel.send(new MLuaReloadPacket(), PacketDistributor.PLAYER.with(player));
   }

   @Override
   public void sendClientCommandToClient(ServerPlayer player, String commandName, List<MLuaNetworkValue> arguments) {
      this.channel.send(new MLuaClientCommandPacket(commandName, arguments), PacketDistributor.PLAYER.with(player));
   }

   @Override
   public void sendAnimationSyncToClient(ServerPlayer player, MLuaAnimationSyncPacket packet) {
      this.channel.send(packet, PacketDistributor.PLAYER.with(player));
   }

   @Override
   public void sendDataSyncToClient(ServerPlayer player, MLuaDataSyncPacket packet) {
      this.channel.send(packet, PacketDistributor.PLAYER.with(player));
   }

   private void handleRemote(MLuaNetworkPacket packet, java.util.function.Supplier<?> supplier) {
      MLuaForgeNetworkAdapter.LegacyContext context = (MLuaForgeNetworkAdapter.LegacyContext) supplier.get();
      context.enqueueWork(() -> {
         if (context.getDirection() == MLuaForgeNetworkAdapter.LegacyDirection.PLAY_TO_SERVER) {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
               MLuaLogger.LOGGER.warn("MLua Network packet '{}' had no server sender", packet.remoteName());
               return;
            }

            this.manager.receiveRemoteFromClient(sender, packet.remoteName(), packet.arguments());
            return;
         }

         this.manager.receiveRemoteFromServer(packet.remoteName(), packet.arguments());
      });
      context.setPacketHandled(true);
   }

   private void handleClientReload(MLuaReloadPacket packet, java.util.function.Supplier<?> supplier) {
      MLuaForgeNetworkAdapter.LegacyContext context = (MLuaForgeNetworkAdapter.LegacyContext) supplier.get();
      context.enqueueWork(this.manager::receiveClientReload);
      context.setPacketHandled(true);
   }

   private void handleClientCommand(MLuaClientCommandPacket packet, java.util.function.Supplier<?> supplier) {
      MLuaForgeNetworkAdapter.LegacyContext context = (MLuaForgeNetworkAdapter.LegacyContext) supplier.get();
      context.enqueueWork(() -> this.manager.receiveClientCommand(packet.commandName(), packet.arguments()));
      context.setPacketHandled(true);
   }

   private void handleClientAnimationSync(MLuaAnimationSyncPacket packet, java.util.function.Supplier<?> supplier) {
      MLuaForgeNetworkAdapter.LegacyContext context = (MLuaForgeNetworkAdapter.LegacyContext) supplier.get();
      context.enqueueWork(() -> this.manager.receiveClientAnimationSync(packet));
      context.setPacketHandled(true);
   }

   private void handleClientDataSync(MLuaDataSyncPacket packet, java.util.function.Supplier<?> supplier) {
      MLuaForgeNetworkAdapter.LegacyContext context = (MLuaForgeNetworkAdapter.LegacyContext) supplier.get();
      context.enqueueWork(() -> this.manager.receiveClientDataSync(packet));
      context.setPacketHandled(true);
   }
}
