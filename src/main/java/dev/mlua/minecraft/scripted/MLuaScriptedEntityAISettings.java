package dev.mlua.minecraft.scripted;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Owns per-entity AI scheduling intervals registered alongside scripted AI callbacks. */
final class MLuaScriptedEntityAISettings {
   private static final ConcurrentMap<String, Integer> INTERVALS = new ConcurrentHashMap<>();

   private MLuaScriptedEntityAISettings() { }

   static void registerInterval(String key, int interval) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         MLuaBehaviorRegistry.validateBehaviorKey(key);
         ensureCapacity(key);
         INTERVALS.put(key, Math.max(1, Math.min(120, interval)));
      }
   }

   static int interval(String key) { return INTERVALS.getOrDefault(key, 5); }
   static Integer registeredInterval(String key) { return INTERVALS.get(key); }
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

   static boolean restore(String key, int expected, Integer previous) {
      if (key == null) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         INTERVALS.compute(key, (ignored, current) -> {
            if (current == null || current != expected) return current;
            restored.set(true);
            return previous;
         });
      }
      return restored.get();
   }

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
