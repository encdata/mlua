package dev.mlua.minecraft.network;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;

public final class MLuaNetworkWireCodec {
   public static final int MAX_ARGUMENTS = 64;
   public static final int TYPE_NETWORK = 0;
   public static final int TYPE_RELOAD = 1;
   public static final int TYPE_CLIENT_COMMAND = 2;
   public static final int TYPE_ANIMATION_SYNC = 3;
   public static final int TYPE_HANDSHAKE = 4;
   public static final int TYPE_DATA_SYNC = 5;

   private MLuaNetworkWireCodec() {
   }

   public static FriendlyByteBuf createPayload(int type, Consumer<FriendlyByteBuf> writer) {
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      boolean success = false;

      try {
         buf.writeByte(type);
         writer.accept(buf);
         success = true;
         return buf;
      } finally {
         if (!success) {
            buf.release();
         }
      }
   }

   public static void writeArgumentList(FriendlyByteBuf buf, List<MLuaNetworkValue> arguments) {
      buf.writeVarInt(arguments.size());
      for (MLuaNetworkValue value : arguments) {
         MLuaNetworkSerializer.writePacketValue(value, buf);
      }
   }

   public static List<MLuaNetworkValue> readArgumentList(FriendlyByteBuf buf) {
      int count = buf.readVarInt();
      if (count < 0 || count > MAX_ARGUMENTS) {
         throw new IllegalArgumentException("[MLua] Invalid network argument count: " + count);
      }

      List<MLuaNetworkValue> arguments = new ArrayList(count);
      for (int i = 0; i < count; ++i) {
         arguments.add(MLuaNetworkSerializer.readPacketValue(buf));
      }
      return arguments;
   }
}
