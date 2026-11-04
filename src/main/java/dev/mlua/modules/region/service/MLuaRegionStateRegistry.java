package dev.mlua.modules.region.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Owns scheduler-scoped region definitions, membership, signals, and cleanup. */
final class MLuaRegionStateRegistry {
    private MLuaRegionStateRegistry() { }

   static record RegionKey(String ownerModId, Side side, String name) {
   }

   static final class RegionDef {
      final RegionKey key;
      final boolean spherical;
      final double centerX, centerY, centerZ;
      final double radius;
      final double minX, minY, minZ, maxX, maxY, maxZ;
      final AABB bounds;

      RegionDef(RegionKey key, double centerX, double centerY, double centerZ, double radius) {
         this.key = key;
         this.spherical = true;
         this.centerX = centerX; this.centerY = centerY; this.centerZ = centerZ;
         this.radius = radius;
         this.minX = this.minY = this.minZ = this.maxX = this.maxY = this.maxZ = 0;
         this.bounds = new AABB(centerX - radius, centerY - radius, centerZ - radius,
                                centerX + radius, centerY + radius, centerZ + radius);
      }

      RegionDef(RegionKey key, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
         this.key = key;
         this.spherical = false;
         this.centerX = this.centerY = this.centerZ = this.radius = 0;
         this.minX = minX; this.minY = minY; this.minZ = minZ;
         this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
         this.bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
      }

      boolean contains(Vec3 pos) {
         if (spherical) {
            double dx = pos.x - centerX;
            double dy = pos.y - centerY;
            double dz = pos.z - centerZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            double radiusSquared = radius * radius;
            if (Double.isFinite(distanceSquared) && Double.isFinite(radiusSquared)
                  && !(radius > 0 && radiusSquared == 0)
                  && !(distanceSquared == 0 && (dx != 0 || dy != 0 || dz != 0))) {
               return distanceSquared <= radiusSquared;
            }
            // Squared distances overflow for otherwise valid, extremely large regions.
            return Math.hypot(Math.hypot(dx, dy), dz) <= radius;
         }
         return pos.x >= minX && pos.x <= maxX &&
                pos.y >= minY && pos.y <= maxY &&
                pos.z >= minZ && pos.z <= maxZ;
      }
   }

   static final class RegionState {
      final Map<RegionKey, RegionDef> regions = new ConcurrentHashMap<>();
      final Map<RegionKey, MLuaRegistration> registrations = new ConcurrentHashMap<>();
      final Map<RegionKey, Set<MLuaEntityScope>> entities = new ConcurrentHashMap<>();
      final Map<RegionKey, MLuaSignal> onEnter = new ConcurrentHashMap<>();
      final Map<RegionKey, MLuaSignal> onExit = new ConcurrentHashMap<>();
      final Map<RegionKey, MLuaSignal> onWithin = new ConcurrentHashMap<>();

      void clear() {
         for (RegionKey key : List.copyOf(this.regions.keySet())) MLuaRegionStateRegistry.removeRegion(this, key);
         MLuaRegionStateRegistry.destroySignals(this, key -> true);
      }

      void clearSide(Side side) {
         for (RegionKey key : List.copyOf(this.regions.keySet())) {
            if (key.side() == side) MLuaRegionStateRegistry.removeRegion(this, key);
         }
         MLuaRegionStateRegistry.destroySignals(this, key -> key.side() == side);
      }

      void clearOwned(Side side, String ownerModId) {
         for (RegionKey key : List.copyOf(this.regions.keySet())) {
            if (ownerModId.equals(key.ownerModId()) && (side == Side.BOTH || key.side() == side)) {
               MLuaRegionStateRegistry.removeRegion(this, key);
            }
         }
         MLuaRegionStateRegistry.destroySignals(this, key -> ownerModId.equals(key.ownerModId())
               && (side == Side.BOTH || key.side() == side));
      }

   }

   private static final Map<MLuaTaskSchedulerView, RegionState> STATES = new WeakHashMap<>();

   static void destroyAll() {
      synchronized (STATES) {
         STATES.values().forEach(RegionState::clear);
      }
   }

   static void destroySide(Side side) {
      if (side == null || side == Side.BOTH) return;
      synchronized (STATES) { STATES.values().forEach(state -> state.clearSide(side)); }
   }

   static void destroyOwned(Side side, String ownerModId) {
      if (side == null || ownerModId == null) return;
      synchronized (STATES) { STATES.values().forEach(state -> state.clearOwned(side, ownerModId)); }
   }

   static void clearForScheduler(MLuaTaskSchedulerView scheduler, Side side) {
      if (scheduler == null) return;
      synchronized (STATES) {
         RegionState state = STATES.get(scheduler);
         if (state == null) return;
         if (side == null || side == Side.BOTH) {
            state.clear();
         } else {
            state.clearSide(side);
         }
      }
   }

   static void destroyOwned(MLuaTaskSchedulerView scheduler, Side side, String ownerModId) {
      if (scheduler == null || side == null || ownerModId == null) return;
      synchronized (STATES) {
         RegionState state = STATES.get(scheduler);
         if (state == null) return;
         state.clearOwned(side, ownerModId);
      }
   }

   static void destroySignals(RegionState state, Predicate<RegionKey> shouldDestroy) {
      for (Map<RegionKey, MLuaSignal> signals : List.of(state.onEnter, state.onExit, state.onWithin)) {
         for (Map.Entry<RegionKey, MLuaSignal> entry : List.copyOf(signals.entrySet())) {
            if (shouldDestroy.test(entry.getKey()) && signals.remove(entry.getKey(), entry.getValue())) entry.getValue().Destroy();
         }
      }
   }

   static RegionState stateFor(MLuaTaskSchedulerView scheduler) {
      synchronized (STATES) { return STATES.computeIfAbsent(scheduler, ignored -> new RegionState()); }
   }

   static List<RegionState> statesSnapshot() {
      synchronized (STATES) { return List.copyOf(STATES.values()); }
   }

   static void removeRegion(RegionState state, RegionKey key) {
      state.regions.remove(key);
      state.registrations.remove(key);
      state.entities.remove(key);
      MLuaSignal s = state.onEnter.remove(key);
      if (s != null) s.Destroy();
      s = state.onExit.remove(key);
      if (s != null) s.Destroy();
      s = state.onWithin.remove(key);
      if (s != null) s.Destroy();
   }

}
