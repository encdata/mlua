package dev.mlua.runtime.data;

import net.minecraft.nbt.CompoundTag;
//--version 1.21.1
import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixTypes;
//--endversion
import net.minecraft.world.level.saveddata.SavedData;

public final class MLuaWorldSavedData extends SavedData {
   private final CompoundTag persistentData;
   private final CompoundTag syncedData;
   private final CompoundTag playerPersistentData;
   private final CompoundTag playerSyncedData;
   private final CompoundTag entityPersistentData;
   private final CompoundTag entitySyncedData;

   public MLuaWorldSavedData() {
      this(new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag());
   }

   private MLuaWorldSavedData(CompoundTag persistentData, CompoundTag syncedData, CompoundTag playerPersistentData, CompoundTag playerSyncedData, CompoundTag entityPersistentData, CompoundTag entitySyncedData) {
      this.persistentData = persistentData;
      this.syncedData = syncedData;
      this.playerPersistentData = playerPersistentData;
      this.playerSyncedData = playerSyncedData;
      this.entityPersistentData = entityPersistentData;
      this.entitySyncedData = entitySyncedData;
   }

   //--version 1.21.1
   public static MLuaWorldSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
      CompoundTag persistent = tag.contains("PersistentData", 10) ? tag.getCompound("PersistentData") : new CompoundTag();
      CompoundTag synced = tag.contains("SyncedData", 10) ? tag.getCompound("SyncedData") : new CompoundTag();
      CompoundTag playerPersistent = tag.contains("PlayerPersistentData", 10) ? tag.getCompound("PlayerPersistentData") : new CompoundTag();
      CompoundTag playerSynced = tag.contains("PlayerSyncedData", 10) ? tag.getCompound("PlayerSyncedData") : new CompoundTag();
      CompoundTag entityPersistent = tag.contains("EntityPersistentData", 10) ? tag.getCompound("EntityPersistentData") : new CompoundTag();
      CompoundTag entitySynced = tag.contains("EntitySyncedData", 10) ? tag.getCompound("EntitySyncedData") : new CompoundTag();
      return new MLuaWorldSavedData(persistent, synced, playerPersistent, playerSynced, entityPersistent, entitySynced);
   }

   public static SavedData.Factory<MLuaWorldSavedData> factory() {
      return new SavedData.Factory<>(MLuaWorldSavedData::new, MLuaWorldSavedData::load, DataFixTypes.LEVEL);
   }
   //--endversion
   //--version 1.20.1
   public static MLuaWorldSavedData load(CompoundTag tag) {
      CompoundTag persistent = tag.contains("PersistentData", 10) ? tag.getCompound("PersistentData") : new CompoundTag();
      CompoundTag synced = tag.contains("SyncedData", 10) ? tag.getCompound("SyncedData") : new CompoundTag();
      CompoundTag playerPersistent = tag.contains("PlayerPersistentData", 10) ? tag.getCompound("PlayerPersistentData") : new CompoundTag();
      CompoundTag playerSynced = tag.contains("PlayerSyncedData", 10) ? tag.getCompound("PlayerSyncedData") : new CompoundTag();
      CompoundTag entityPersistent = tag.contains("EntityPersistentData", 10) ? tag.getCompound("EntityPersistentData") : new CompoundTag();
      CompoundTag entitySynced = tag.contains("EntitySyncedData", 10) ? tag.getCompound("EntitySyncedData") : new CompoundTag();
      return new MLuaWorldSavedData(persistent, synced, playerPersistent, playerSynced, entityPersistent, entitySynced);
   }
   //--endversion

   public CompoundTag persistentData() {
      return this.persistentData;
   }

   public CompoundTag syncedData() {
      return this.syncedData;
   }

   public CompoundTag playerPersistentData() {
      return this.playerPersistentData;
   }

   public CompoundTag playerSyncedData() {
      return this.playerSyncedData;
   }

   public CompoundTag entityPersistentData() {
      return this.entityPersistentData;
   }

   public CompoundTag entitySyncedData() {
      return this.entitySyncedData;
   }

   //--version 1.21.1
   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
      tag.put("PersistentData", this.persistentData.copy());
      tag.put("SyncedData", this.syncedData.copy());
      tag.put("PlayerPersistentData", this.playerPersistentData.copy());
      tag.put("PlayerSyncedData", this.playerSyncedData.copy());
      tag.put("EntityPersistentData", this.entityPersistentData.copy());
      tag.put("EntitySyncedData", this.entitySyncedData.copy());
      return tag;
   }
   //--endversion
   //--version 1.20.1
   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.put("PersistentData", this.persistentData.copy());
      tag.put("SyncedData", this.syncedData.copy());
      tag.put("PlayerPersistentData", this.playerPersistentData.copy());
      tag.put("PlayerSyncedData", this.playerSyncedData.copy());
      tag.put("EntityPersistentData", this.entityPersistentData.copy());
      tag.put("EntitySyncedData", this.entitySyncedData.copy());
      return tag;
   }
   //--endversion
}
