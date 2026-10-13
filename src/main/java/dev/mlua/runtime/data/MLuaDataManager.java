package dev.mlua.runtime.data;

import dev.mlua.minecraft.binding.MLuaNbtWrapper;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

public final class MLuaDataManager {
   private static final String WORLD_DATA_ID = "mlua_world_data";
   private static final String PLAYER_SYNC_KEY = "MLuaSyncedData";
   private static final String ENTITY_SYNC_KEY = "MLuaSyncedData";
   private static final CompoundTag CLIENT_WORLD_SYNC = new CompoundTag();
   private static final Map<UUID, CompoundTag> CLIENT_PLAYER_SYNC = new ConcurrentHashMap<>();
   private static final Map<UUID, CompoundTag> CLIENT_ENTITY_SYNC = new ConcurrentHashMap<>();

   private MLuaDataManager() {
   }

   public static LuaTable worldPersistentData(Level level) {
      ServerLevel serverLevel = requireServerLevel(level, "World persistent data");
      return MLuaNbtWrapper.table(() -> worldData(serverLevel).persistentData(), tag -> {
         replace(worldData(serverLevel).persistentData(), tag);
         worldData(serverLevel).setDirty();
      });
   }

   public static LuaTable worldSyncedData(Level level) {
      if (level instanceof ServerLevel serverLevel) {
         return MLuaNbtWrapper.table(() -> worldData(serverLevel).syncedData(), tag -> {
            replace(worldData(serverLevel).syncedData(), tag);
            worldData(serverLevel).setDirty();
            syncWorld(serverLevel);
         });
      }

      return readOnlyProxy(() -> CLIENT_WORLD_SYNC, "World synced data is server-authoritative");
   }

   public static LuaTable playerPersistentData(Player player, boolean writable) {
      if (writable) {
         requireEntityServerLevel(player, "Player persistent data");
         return MLuaNbtWrapper.table(() -> playerPersistentTag(player), tag -> {
            setPlayerPersistentTag(player, tag);
         });
      }

      return readOnlyProxy(CompoundTag::new, "Player persistent data is not synced to clients");
   }

   public static LuaTable playerSyncedData(Player player) {
      if (player instanceof ServerPlayer serverPlayer) {
         ServerLevel level = requireEntityServerLevel(serverPlayer, "Player synced data");
         return MLuaNbtWrapper.table(() -> bucket(worldData(level).playerSyncedData(), serverPlayer.getUUID()), tag -> {
            setBucket(worldData(level).playerSyncedData(), serverPlayer.getUUID(), tag);
            worldData(level).setDirty();
            syncPlayer(serverPlayer);
         });
      }

      return readOnlyProxy(() -> CLIENT_PLAYER_SYNC.computeIfAbsent(player.getUUID(), ignored -> new CompoundTag()), "Player synced data is server-authoritative");
   }

   public static LuaTable entityPersistentData(Entity entity, boolean writable) {
      if (writable) {
         requireEntityServerLevel(entity, "Entity persistent data");
         return MLuaNbtWrapper.table(() -> entityPersistentTag(entity), tag -> {
            setEntityPersistentTag(entity, tag);
         });
      }

      return readOnlyProxy(CompoundTag::new, "Entity persistent data is not synced to clients");
   }

   public static LuaTable entitySyncedData(Entity entity) {
      if (!entity.level().isClientSide) {
         ServerLevel level = requireEntityServerLevel(entity, "Entity synced data");
         return MLuaNbtWrapper.table(() -> bucket(worldData(level).entitySyncedData(), entity.getUUID()), tag -> {
            setBucket(worldData(level).entitySyncedData(), entity.getUUID(), tag);
            worldData(level).setDirty();
            syncEntity(entity);
         });
      }

      return readOnlyProxy(() -> CLIENT_ENTITY_SYNC.computeIfAbsent(entity.getUUID(), ignored -> new CompoundTag()), "Entity synced data is server-authoritative");
   }

   public static void handleClientSync(MLuaDataSyncPacket packet) {
      CompoundTag snapshot = packet.data() == null ? new CompoundTag() : packet.data().copy();
      switch (packet.scope()) {
         case WORLD -> {
            replace(CLIENT_WORLD_SYNC, snapshot);
         }
         case PLAYER -> {
            if (packet.targetId() != null) {
               CLIENT_PLAYER_SYNC.put(packet.targetId(), snapshot);
            }
         }
         case ENTITY -> {
            if (packet.targetId() != null) {
               CLIENT_ENTITY_SYNC.put(packet.targetId(), snapshot);
            }
         }
      }
   }

   public static void syncWorld(ServerLevel level) {
      MLuaNetworkManager.get().sendDataSyncToAll(new MLuaDataSyncPacket(MLuaDataSyncPacket.Scope.WORLD, null, worldData(level).syncedData().copy()), level.getServer());
   }

   public static void syncWorldToPlayer(ServerPlayer player, ServerLevel level) {
      MLuaNetworkManager.get().sendDataSyncToPlayer(player, new MLuaDataSyncPacket(MLuaDataSyncPacket.Scope.WORLD, null, worldData(level).syncedData().copy()));
   }

   public static void syncPlayer(ServerPlayer player) {
      ServerLevel level = requireEntityServerLevel(player, "Player synced data");
      MLuaNetworkManager.get().sendDataSyncToAll(new MLuaDataSyncPacket(MLuaDataSyncPacket.Scope.PLAYER, player.getUUID(), bucket(worldData(level).playerSyncedData(), player.getUUID()).copy()), level.getServer());
   }

   public static void syncPlayerTo(ServerPlayer recipient, ServerPlayer target) {
      ServerLevel level = requireEntityServerLevel(target, "Player synced data");
      MLuaNetworkManager.get().sendDataSyncToPlayer(recipient, new MLuaDataSyncPacket(MLuaDataSyncPacket.Scope.PLAYER, target.getUUID(), bucket(worldData(level).playerSyncedData(), target.getUUID()).copy()));
   }

   public static void syncEntity(Entity entity) {
      ServerLevel level = requireEntityServerLevel(entity, "Entity synced data");
      MLuaNetworkManager.get().sendDataSyncToAll(new MLuaDataSyncPacket(MLuaDataSyncPacket.Scope.ENTITY, entity.getUUID(), bucket(worldData(level).entitySyncedData(), entity.getUUID()).copy()), level.getServer());
   }

   public static void syncEntityTo(ServerPlayer recipient, Entity entity) {
      ServerLevel level = requireEntityServerLevel(entity, "Entity synced data");
      MLuaNetworkManager.get().sendDataSyncToPlayer(recipient, new MLuaDataSyncPacket(MLuaDataSyncPacket.Scope.ENTITY, entity.getUUID(), bucket(worldData(level).entitySyncedData(), entity.getUUID()).copy()));
   }

   public static void syncInitialState(ServerPlayer player) {
      MinecraftServer server = player.level().getServer();
      if (server == null) {
         return;
      }

      syncWorldToPlayer(player, server.overworld());
      for (ServerPlayer other : server.getPlayerList().getPlayers()) {
         syncPlayerTo(player, other);
      }

      for (ServerLevel level : server.getAllLevels()) {
         for (Entity entity : level.getAllEntities()) {
            syncEntityTo(player, entity);
         }
      }
   }

   public static void clearClientCache() {
      for (String key : new ArrayList<>(CLIENT_WORLD_SYNC.getAllKeys())) {
         CLIENT_WORLD_SYNC.remove(key);
      }
      CLIENT_PLAYER_SYNC.clear();
      CLIENT_ENTITY_SYNC.clear();
   }

   public static CompoundTag playerPersistentTag(Player player) {
      ServerLevel level = requireEntityServerLevel(player, "Player persistent data");
      return bucket(worldData(level).playerPersistentData(), player.getUUID());
   }

   public static void setPlayerPersistentTag(Player player, CompoundTag tag) {
      ServerLevel level = requireEntityServerLevel(player, "Player persistent data");
      setBucket(worldData(level).playerPersistentData(), player.getUUID(), tag);
      worldData(level).setDirty();
   }

   public static CompoundTag entityPersistentTag(Entity entity) {
      ServerLevel level = requireEntityServerLevel(entity, "Entity persistent data");
      return bucket(worldData(level).entityPersistentData(), entity.getUUID());
   }

   public static void setEntityPersistentTag(Entity entity, CompoundTag tag) {
      ServerLevel level = requireEntityServerLevel(entity, "Entity persistent data");
      setBucket(worldData(level).entityPersistentData(), entity.getUUID(), tag);
      worldData(level).setDirty();
   }

   private static MLuaWorldSavedData worldData(ServerLevel level) {
      //--version 1.21.1
      return level.getDataStorage().computeIfAbsent(MLuaWorldSavedData.factory(), WORLD_DATA_ID);
      //--endversion
      //--version 1.20.1
      return level.getDataStorage().computeIfAbsent(MLuaWorldSavedData::load, MLuaWorldSavedData::new, WORLD_DATA_ID);
      //--endversion
   }

   private static void replace(CompoundTag target, CompoundTag source) {
      for (String key : new ArrayList<>(target.getAllKeys())) {
         target.remove(key);
      }
      for (String key : source.getAllKeys()) {
         target.put(key, source.get(key).copy());
      }
   }

   private static LuaTable readOnlyProxy(java.util.function.Supplier<CompoundTag> getter, String message) {
      return MLuaNbtWrapper.table(getter, ignored -> {
         throw new LuaError("[MLua] " + message);
      });
   }

   private static ServerLevel requireServerLevel(Level level, String label) {
      if (level instanceof ServerLevel serverLevel) {
         return serverLevel;
      }

      throw new LuaError("[MLua] " + label + " is only available on the server");
   }

   private static ServerLevel requireEntityServerLevel(Entity entity, String label) {
      return requireServerLevel(entity.level(), label);
   }

   private static CompoundTag bucket(CompoundTag root, UUID id) {
      String key = id.toString();
      if (!root.contains(key, 10)) {
         root.put(key, new CompoundTag());
      }

      return root.getCompound(key);
   }

   private static void setBucket(CompoundTag root, UUID id, CompoundTag tag) {
      root.put(id.toString(), tag.copy());
   }
}
