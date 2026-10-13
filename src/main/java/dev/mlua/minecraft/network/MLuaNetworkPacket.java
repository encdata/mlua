package dev.mlua.minecraft.network;

import java.util.List;

public record MLuaNetworkPacket(String remoteName, List<MLuaNetworkValue> arguments) {
   public MLuaNetworkPacket(String remoteName, List<MLuaNetworkValue> arguments) {
      this.remoteName = remoteName;
      this.arguments = arguments;
   }

   public String remoteName() {
      return this.remoteName;
   }

   public List<MLuaNetworkValue> arguments() {
      return this.arguments;
   }
}
