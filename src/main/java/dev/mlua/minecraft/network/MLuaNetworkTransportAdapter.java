package dev.mlua.minecraft.network;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;

public interface MLuaNetworkTransportAdapter {
   Transport transport();

   void init(MLuaNetworkManager manager, String protocolVersion);

   boolean hasServerMLua();

   boolean hasClientMLua(ServerPlayer player);

   void sendRemoteToServer(String remoteName, List<MLuaNetworkValue> arguments) throws Exception;

   void sendRemoteToClient(ServerPlayer player, String remoteName, List<MLuaNetworkValue> arguments) throws Exception;

   void sendReloadToClient(ServerPlayer player) throws Exception;

   void sendClientCommandToClient(ServerPlayer player, String commandName, List<MLuaNetworkValue> arguments) throws Exception;

   void sendAnimationSyncToClient(ServerPlayer player, MLuaAnimationSyncPacket packet) throws Exception;

   void sendDataSyncToClient(ServerPlayer player, dev.mlua.runtime.data.MLuaDataSyncPacket packet) throws Exception;

   enum Transport {
      FORGE,
      FABRIC
   }
}
