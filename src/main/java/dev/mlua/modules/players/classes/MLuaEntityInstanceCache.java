package dev.mlua.modules.players.classes;

import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.MLuaClientModelManager;
import dev.mlua.runtime.client.MLuaClientParticleManager;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaClientSoundManager;
import dev.mlua.runtime.entity.MLuaEntityComponentManager;
import dev.mlua.module.MLuaRegistration;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaTable;

/** Owns identity caches and teardown for Lua entity and player instances. */
final class MLuaEntityInstanceCache {
   private static final MLuaEntityWrapperCache LEGACY_CACHE = new MLuaEntityWrapperCache();
   private static final Map<MLuaRegistration, MLuaEntityWrapperCache> ENTITY_CACHES =
         Collections.synchronizedMap(new WeakHashMap<>());

   private MLuaEntityInstanceCache() {
   }

   public static void clearCache() {
      LEGACY_CACHE.clear();
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.values().forEach(MLuaEntityWrapperCache::clear); }
   }

   public static void clearCache(MLuaRegistration registration) {
      if (registration == null) return;
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.remove(registration); }
   }

   public static void clearSideCache(dev.mlua.common.enums.Side side) {
      if (side == null) return;
      LEGACY_CACHE.removeSide(side);
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.values().forEach(cache -> cache.removeSide(side)); }
   }

   public static void removeFromCache(int entityId) {
      LEGACY_CACHE.removeEntityId(entityId);
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.values().forEach(cache -> cache.removeEntityId(entityId)); }
      MLuaAnimationController.removeEntity(entityId);
   }

   public static void removeFromCache(Entity entity) {
      if (entity == null) return;
      removeFromCache(MLuaEntityScope.of(entity));
   }

   /** Removes all Lua state attached to one exact entity identity after its world unloads it. */
   public static void removeFromCache(MLuaEntityScope scope) {
      if (scope == null) return;
      MLuaEntityComponentManager.clearEntity(scope);
      MLuaEntityWrapperCache.Key key = MLuaEntityWrapperCache.key(scope);
      LEGACY_CACHE.remove(key);
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.values().forEach(cache -> cache.remove(key)); }
      MLuaAnimationController.removeEntity(scope);
      if (scope.side() == dev.mlua.common.enums.Side.CLIENT) {
         MLuaClientRenderManager.resetEntityModel(scope);
         MLuaClientModelManager.clearEntityForAllOwners(scope);
         MLuaClientSoundManager.removeFollowingEntity(scope);
         MLuaClientParticleManager.removeFollowingEntity(scope);
      }
   }

   public static void cleanCache() {
      LEGACY_CACHE.clean();
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.values().forEach(MLuaEntityWrapperCache::clean); }
   }

   public static int cacheSize() {
      synchronized (ENTITY_CACHES) {
         return LEGACY_CACHE.size() + ENTITY_CACHES.values().stream().mapToInt(MLuaEntityWrapperCache::size).sum();
      }
   }

   static MLuaEntityWrapperCache cacheFor(MLuaRegistration registration) {
      if (registration == null) return LEGACY_CACHE;
      synchronized (ENTITY_CACHES) {
         return ENTITY_CACHES.computeIfAbsent(registration, ignored -> new MLuaEntityWrapperCache());
      }
   }

   public static LuaTable cachedPlayerTable(Entity player, MLuaRegistration registration) {
      return cacheFor(registration).getPlayer(MLuaEntityWrapperCache.key(player));
   }

   public static void cachePlayerTable(Entity player, MLuaRegistration registration, LuaTable table) {
      cacheFor(registration).putPlayer(MLuaEntityWrapperCache.key(player), table);
   }


}
