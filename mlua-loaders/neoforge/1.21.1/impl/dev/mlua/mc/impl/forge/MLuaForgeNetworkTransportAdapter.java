package dev.mlua.mc.impl.forge;

import dev.mlua.minecraft.network.MLuaAnimationSyncPacket;
import dev.mlua.minecraft.network.MLuaClientCommandPacket;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.minecraft.network.MLuaNetworkTransportAdapter;
import dev.mlua.minecraft.network.MLuaNetworkValue;
import dev.mlua.minecraft.network.MLuaReloadPacket;
import dev.mlua.runtime.data.MLuaDataSyncPacket;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MLuaForgeNetworkTransportAdapter implements MLuaNetworkTransportAdapter {
   private MLuaNetworkManager manager;

   @Override
   public Transport transport() {
      return Transport.FORGE;
   }

   @Override
   public void init(MLuaNetworkManager manager, String protocolVersion) {
      this.manager = manager;
   }

   @Override
   public boolean hasServerMLua() {
      Connection connection = this.manager.resolveClientConnection();
      return MLuaForgeNetworkAdapter.hasChannel(connection);
   }

   @Override
   public boolean hasClientMLua(ServerPlayer player) {
      Connection connection = this.manager.resolveServerPlayerConnection(player);
      return MLuaForgeNetworkAdapter.hasChannel(connection);
   }

   @Override
   public void sendRemoteToServer(String remoteName, List<MLuaNetworkValue> arguments) {
      PacketDistributor.sendToServer(new MLuaForgeNetworkAdapter.RemotePayload(remoteName, arguments));
   }

   @Override
   public void sendRemoteToClient(ServerPlayer player, String remoteName, List<MLuaNetworkValue> arguments) {
      PacketDistributor.sendToPlayer(player, new MLuaForgeNetworkAdapter.RemotePayload(remoteName, arguments));
   }

   @Override
   public void sendReloadToClient(ServerPlayer player) {
      PacketDistributor.sendToPlayer(player, new MLuaForgeNetworkAdapter.ReloadPayload(new MLuaReloadPacket()));
   }

   @Override
   public void sendClientCommandToClient(ServerPlayer player, String commandName, List<MLuaNetworkValue> arguments) {
      PacketDistributor.sendToPlayer(player, new MLuaForgeNetworkAdapter.ClientCommandPayload(new MLuaClientCommandPacket(commandName, arguments)));
   }

   @Override
   public void sendAnimationSyncToClient(ServerPlayer player, MLuaAnimationSyncPacket packet) {
      PacketDistributor.sendToPlayer(player, new MLuaForgeNetworkAdapter.AnimationSyncPayload(packet));
   }

   @Override
   public void sendDataSyncToClient(ServerPlayer player, MLuaDataSyncPacket packet) {
      PacketDistributor.sendToPlayer(player, new MLuaForgeNetworkAdapter.DataSyncPayload(packet));
   }
}
