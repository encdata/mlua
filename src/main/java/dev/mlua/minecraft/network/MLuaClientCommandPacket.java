package dev.mlua.minecraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

public record MLuaClientCommandPacket(String commandName, List<MLuaNetworkValue> arguments) {
   private static final int MAX_ARGUMENTS = 64;

   public MLuaClientCommandPacket(FriendlyByteBuf buf) {
      this(buf.readUtf(128), readArguments(buf));
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeUtf(this.commandName, 128);
      buf.writeVarInt(this.arguments.size());

      for (MLuaNetworkValue argument : this.arguments) {
         MLuaNetworkSerializer.writePacketValue(argument, buf);
      }
   }

   private static List<MLuaNetworkValue> readArguments(FriendlyByteBuf buf) {
      int count = buf.readVarInt();
      if (count < 0 || count > MAX_ARGUMENTS) {
         throw new IllegalArgumentException("[MLua] Invalid network argument count: " + count);
      }

      List<MLuaNetworkValue> arguments = new ArrayList(count);
      int[] nodes = MLuaNetworkSerializer.newReadBudget();

      for (int i = 0; i < count; ++i) {
         arguments.add(MLuaNetworkSerializer.readPacketValue(buf, nodes));
      }

      return arguments;
   }
}
