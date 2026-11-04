package dev.mlua.minecraft.scripted;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.MLuaRuntime;
import net.minecraft.world.level.Level;

/** Owns per-entity AI scheduling intervals registered alongside scripted AI callbacks. */
final class MLuaScriptedEntityAISettings {
   private static final Scope GLOBAL = new Scope(null, Side.BOTH);
   private static final ConcurrentMap<ScopedKey, OwnedInterval> INTERVALS = new ConcurrentHashMap<>();

   private MLuaScriptedEntityAISettings() { }

   static void registerInterval(String key, int interval) {
      registerInterval(key, interval, null);
   }

   static Object registerInterval(String key, int interval, MLuaContext context) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         MLuaBehaviorRegistry.validateBehaviorKey(key);
         ScopedKey scopedKey = new ScopedKey(key, scope(context));
         ensureCapacity(scopedKey, context);
         OwnedInterval installed = new OwnedInterval(Math.max(1, Math.min(120, interval)), context);
         INTERVALS.put(scopedKey, installed);
         return installed;
      }
   }

   static int interval(String key) {
      return interval(key, null);
   }
   static int interval(String key, Level level) {
      OwnedInterval interval = level == null ? preferred(key) : INTERVALS.get(
         new ScopedKey(key, new Scope(MLuaRuntime.sessionForLevel(level),
            level.isClientSide() ? Side.CLIENT : Side.SERVER)));
      if (interval == null && level != null) interval = INTERVALS.get(new ScopedKey(key, GLOBAL));
      return interval == null ? 5 : interval.ticks();
   }
   static Integer registeredInterval(String key) {
      return registeredInterval(key, null);
   }
   static Integer registeredInterval(String key, MLuaContext context) {
      OwnedInterval interval = context == null ? INTERVALS.get(new ScopedKey(key, GLOBAL))
         : INTERVALS.get(new ScopedKey(key, scope(context)));
      return interval == null ? null : interval.ticks();
   }
   static Object registration(String key, MLuaContext context) {
      return INTERVALS.get(new ScopedKey(key, scope(context)));
   }
   static MLuaContext context(String key) {
      OwnedInterval interval = preferred(key);
      return interval == null ? null : interval.context();
   }
   static MLuaContext context(String key, MLuaContext scopeContext) {
      OwnedInterval interval = INTERVALS.get(new ScopedKey(key, scope(scopeContext)));
      return interval == null ? null : interval.context();
   }
   static int count() { return INTERVALS.size(); }

   static int countForOwner(String owner) {
      return (int) INTERVALS.entrySet().stream().filter(entry ->
         owner.equals(MLuaScriptedOwnerPolicy.ownerOf(entry.getValue().context(), entry.getKey().key()))).count();
   }

   static void clear(String key) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         INTERVALS.keySet().removeIf(scopedKey -> scopedKey.key().equals(key));
      }
   }

   static void clearOwner(String owner) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         INTERVALS.entrySet().removeIf(entry -> owner.equals(MLuaScriptedOwnerPolicy.ownerOf(
            entry.getValue().context(), entry.getKey().key())));
      }
   }

   static void clearOwner(String owner, MLuaRuntimeSession session, Side side) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         INTERVALS.entrySet().removeIf(entry -> owner.equals(MLuaScriptedOwnerPolicy.ownerOf(
            entry.getValue().context(), entry.getKey().key()))
            && MLuaScriptedOwnerPolicy.inScope(entry.getValue().context(), session, side));
      }
   }

   static boolean restore(String key, int expected, Integer previous) {
      OwnedInterval current = preferred(key);
      return restore(key, expected, previous, current == null ? null : current.context(), null);
   }

   static boolean restore(String key, int expected, Integer previous,
                          MLuaContext expectedContext, MLuaContext previousContext) {
      if (key == null) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         INTERVALS.compute(new ScopedKey(key, scope(expectedContext)), (ignored, current) -> {
            if (current == null || current.ticks() != expected || current.context() != expectedContext) return current;
            restored.set(true);
            return previous == null ? null : new OwnedInterval(previous, previousContext);
         });
      }
      return restored.get();
   }

   static boolean restore(String key, Object expectedRegistration, Object previousRegistration,
                          MLuaContext expectedContext) {
      if (key == null || expectedRegistration == null) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         INTERVALS.compute(new ScopedKey(key, scope(expectedContext)), (ignored, current) -> {
            if (current != expectedRegistration) return current;
            restored.set(true);
            return previousRegistration instanceof OwnedInterval previous ? previous : null;
         });
      }
      return restored.get();
   }

   private record OwnedInterval(int ticks, MLuaContext context) { }

   private static Scope scope(MLuaContext context) {
      return context == null ? GLOBAL : new Scope(context.session(), context.side());
   }

   private static OwnedInterval preferred(String key) {
      OwnedInterval global = INTERVALS.get(new ScopedKey(key, GLOBAL));
      if (global != null) return global;
      OwnedInterval only = null;
      for (var entry : INTERVALS.entrySet()) if (entry.getKey().key().equals(key)) {
         if (only != null) return null;
         only = entry.getValue();
      }
      return only;
   }

   private static void ensureCapacity(ScopedKey scopedKey, MLuaContext context) {
      if (INTERVALS.containsKey(scopedKey)) return;
      if (INTERVALS.size() >= MLuaBehaviorRegistry.MAX_ENTITY_AI_CONFIGS) {
         throw new IllegalStateException("Scripted definition limit reached ("
            + MLuaBehaviorRegistry.MAX_ENTITY_AI_CONFIGS + " total)");
      }
      long ownerCount = INTERVALS.entrySet().stream().filter(entry ->
         MLuaScriptedOwnerPolicy.sameOwner(entry.getValue().context(), entry.getKey().key(),
            context, scopedKey.key())).count();
      if (ownerCount >= MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted definition limit reached ("
            + MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS_PER_ADDON + ")");
      }
   }

   private record Scope(MLuaRuntimeSession session, Side side) { }
   private record ScopedKey(String key, Scope scope) { }
}
