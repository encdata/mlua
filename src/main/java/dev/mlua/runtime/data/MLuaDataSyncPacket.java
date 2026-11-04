package dev.mlua.runtime.data;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record MLuaDataSyncPacket(MLuaDataSyncPacket.Scope scope, UUID targetId, CompoundTag data) {
   public MLuaDataSyncPacket(FriendlyByteBuf buf) {
      this(Scope.values()[buf.readVarInt()], buf.readBoolean() ? buf.readUUID() : null, readTag(buf));
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.scope.ordinal());
      buf.writeBoolean(this.targetId != null);
      if (this.targetId != null) {
         buf.writeUUID(this.targetId);
      }

      writeTag(buf, this.data);
   }

   private static CompoundTag readTag(FriendlyByteBuf buf) {
      CompoundTag tag = buf.readNbt();
      return tag != null ? tag : new CompoundTag();
   }

   private static void writeTag(FriendlyByteBuf buf, CompoundTag tag) {
      buf.writeNbt(tag == null ? new CompoundTag() : tag);
   }

   public enum Scope {
      WORLD,
      PLAYER,
      ENTITY
   }
}
