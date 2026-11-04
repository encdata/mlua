package dev.mlua.minecraft.network;

import net.minecraft.network.FriendlyByteBuf;

public record MLuaReloadPacket() {
   public MLuaReloadPacket(FriendlyByteBuf buf) {
      this();
   }

   public void encode(FriendlyByteBuf buf) {
   }
}
