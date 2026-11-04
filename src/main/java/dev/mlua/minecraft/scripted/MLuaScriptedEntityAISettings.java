package dev.mlua.minecraft.scripted;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntimeSession;

/** Owns per-entity AI scheduling intervals registered alongside scripted AI callbacks. */
final class MLuaScriptedEntityAISettings {
   private static final ConcurrentMap<String, OwnedInterval> INTERVALS = new ConcurrentHashMap<>();

   private MLuaScriptedEntityAISettings() { }

   static void registerInterval(String key, int interval) {
      registerInterval(key, interval, null);
   }

   static void registerInterval(String key, int interval, MLuaContext context) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         MLuaBehaviorRegistry.validateBehaviorKey(key);
         ensureCapacity(key);
         INTERVALS.put(key, new OwnedInterval(Math.max(1, Math.min(120, interval)), context));
      }
   }

   static int interval(String key) {
      OwnedInterval interval = INTERVALS.get(key);
      return interval == null ? 5 : interval.ticks();
   }
   static Integer registeredInterval(String key) {
      OwnedInterval interval = INTERVALS.get(key);
      return interval == null ? null : interval.ticks();
   }
   static MLuaContext context(String key) {
      OwnedInterval interval = INTERVALS.get(key);
      return interval == null ? null : interval.context();
   }
   static int count() { return INTERVALS.size(); }

   static int countForOwner(String owner) {
      String prefix = owner + ":";
      return (int) INTERVALS.keySet().stream().filter(key -> key.startsWith(prefix)).count();
   }

   static void clear(String key) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) { INTERVALS.remove(key); }
   }

   static void clearOwnerPrefix(String prefix) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         INTERVALS.keySet().removeIf(key -> key.startsWith(prefix));
      }
   }

   static void clearOwner(String owner, MLuaRuntimeSession session, Side side) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         INTERVALS.entrySet().removeIf(entry -> owner.equals(MLuaScriptedOwnerPolicy.ownerOf(
            entry.getValue().context(), entry.getKey()))
            && MLuaScriptedOwnerPolicy.inScope(entry.getValue().context(), session, side));
      }
   }

   static boolean restore(String key, int expected, Integer previous) {
      OwnedInterval current = INTERVALS.get(key);
      return restore(key, expected, previous, current == null ? null : current.context(), null);
   }

   static boolean restore(String key, int expected, Integer previous,
                          MLuaContext expectedContext, MLuaContext previousContext) {
      if (key == null) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         INTERVALS.compute(key, (ignored, current) -> {
            if (current == null || current.ticks() != expected || current.context() != expectedContext) return current;
            restored.set(true);
            return previous == null ? null : new OwnedInterval(previous, previousContext);
         });
      }
      return restored.get();
   }

   private record OwnedInterval(int ticks, MLuaContext context) { }

   private static void ensureCapacity(String key) {
      if (INTERVALS.containsKey(key)) return;
      if (INTERVALS.size() >= MLuaBehaviorRegistry.MAX_ENTITY_AI_CONFIGS) {
         throw new IllegalStateException("Scripted definition limit reached ("
            + MLuaBehaviorRegistry.MAX_ENTITY_AI_CONFIGS + " total)");
      }
      int separator = key.indexOf(':');
      String ownerPrefix = separator > 0 ? key.substring(0, separator + 1) : "";
      if (!ownerPrefix.isEmpty() && INTERVALS.keySet().stream().filter(id -> id.startsWith(ownerPrefix)).count()
            >= MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted definition limit reached ("
            + MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS_PER_ADDON + ")");
      }
   }
}
