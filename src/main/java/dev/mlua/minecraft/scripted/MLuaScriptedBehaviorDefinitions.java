package dev.mlua.minecraft.scripted;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntimeSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.luaj.vm2.LuaValue;

/** Owns registered behavior callback sets and their reload-safe replacement semantics. */
final class MLuaScriptedBehaviorDefinitions {
   private static final ConcurrentMap<ScopedKey, MLuaBehaviorRegistry.BehaviorSet> DEFINITIONS =
      new ConcurrentHashMap<>();
   private static final Scope GLOBAL = new Scope(null, Side.BOTH);

   private MLuaScriptedBehaviorDefinitions() { }

   static void registerBehavior(String domain, String key, String event, LuaValue behavior) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank() || event == null || event.isBlank()) {
         throw new IllegalArgumentException("Scripted behavior domain, key, and member must be non-empty");
      }
      validateName(event);
      MLuaBehaviorRegistry.validateBehaviorKey(key);
      if (behavior != null && !behavior.isnil() && !behavior.isfunction()) {
         throw new IllegalArgumentException("Scripted behavior callback must be a Lua function or nil");
      }
      ScopedKey scopedKey = new ScopedKey(domain, key, GLOBAL);
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         MLuaBehaviorRegistry.BehaviorSet current = DEFINITIONS.get(scopedKey);
         Map<String, LuaValue> updated = current == null
            ? new HashMap<>() : new HashMap<>(current.callbacks());
         if (behavior == null || behavior.isnil()) updated.remove(event);
         else updated.put(event, behavior);
         if (updated.size() > MLuaBehaviorRegistry.MAX_CALLBACKS_PER_DEFINITION) {
            throw new IllegalStateException("Scripted behavior callback limit reached ("
               + MLuaBehaviorRegistry.MAX_CALLBACKS_PER_DEFINITION + " per definition)");
         }
         if (updated.isEmpty()) DEFINITIONS.remove(scopedKey);
         else DEFINITIONS.put(scopedKey, new MLuaBehaviorRegistry.BehaviorSet(updated,
            current == null ? null : current.context(), domain, key));
      }
      if (behavior != null && !behavior.isnil()) {
         MLuaLogger.LOGGER.debug("Registered {} behavior '{}' for {}", domain, event, key);
      }
   }

   /** Atomically replaces callbacks for one id within its runtime session and side. */
   static Map<String, LuaValue> registerBehaviors(String domain, String key,
         Map<String, LuaValue> behaviors, MLuaContext context) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank()) {
         throw new IllegalArgumentException("Scripted behavior domain and key must be non-empty");
      }
      Map<String, LuaValue> definition = new HashMap<>();
      if (behaviors != null) {
         behaviors.forEach((event, behavior) -> {
            if (behavior == null || behavior.isnil()) return;
            if (event == null || event.isBlank()) throw new IllegalArgumentException("Scripted behavior name must be non-empty");
            validateName(event);
            if ("entity".equals(domain) && "AI".equals(event) && behavior.istable()) {
               definition.put(event, MLuaBehaviorRegistry.snapshotEntityAI(behavior.checktable()));
            } else if (behavior.isfunction()) {
               definition.put(event, behavior);
            } else {
               throw new IllegalArgumentException("Scripted behavior callback '" + event
                  + "' must be a Lua function or nil"
                  + ("entity".equals(domain) && "AI".equals(event) ? ", or an AI state definition table" : ""));
            }
         });
      }
      MLuaBehaviorRegistry.validateBehaviorKey(key);
      if (definition.size() > MLuaBehaviorRegistry.MAX_CALLBACKS_PER_DEFINITION) {
         throw new IllegalArgumentException("Scripted behavior callback limit reached ("
            + MLuaBehaviorRegistry.MAX_CALLBACKS_PER_DEFINITION + " per definition)");
      }
      Scope scope = scope(context);
      ScopedKey scopedKey = new ScopedKey(domain, key, scope);
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         MLuaBehaviorRegistry.BehaviorSet current = DEFINITIONS.get(scopedKey);
         if (!definition.isEmpty()) ensureCapacity(scopedKey, context);
         if (definition.isEmpty()) DEFINITIONS.remove(scopedKey);
         else DEFINITIONS.put(scopedKey,
            new MLuaBehaviorRegistry.BehaviorSet(definition, context, domain, key));
      }
      return Map.copyOf(definition);
   }

   static MLuaBehaviorRegistry.BehaviorSet definition(String domain, String key) {
      MLuaBehaviorRegistry.BehaviorSet global = DEFINITIONS.get(new ScopedKey(domain, key, GLOBAL));
      if (global != null) return global;
      MLuaBehaviorRegistry.BehaviorSet latest = null;
      for (Map.Entry<ScopedKey, MLuaBehaviorRegistry.BehaviorSet> entry : DEFINITIONS.entrySet()) {
         if (entry.getKey().domain().equals(domain) && entry.getKey().key().equals(key)) latest = entry.getValue();
      }
      return latest;
   }

   static MLuaBehaviorRegistry.BehaviorSet definition(String domain, String key,
                                                       MLuaRuntimeSession session, Side side) {
      MLuaBehaviorRegistry.BehaviorSet scoped = DEFINITIONS.get(
         new ScopedKey(domain, key, new Scope(session, side)));
      return scoped != null ? scoped : DEFINITIONS.get(new ScopedKey(domain, key, GLOBAL));
   }

   static MLuaBehaviorRegistry.BehaviorSet definition(String domain, String key, MLuaContext context) {
      return definition(domain, key, scope(context).session(), scope(context).side());
   }

   static boolean restore(String domain, String key, Map<String, LuaValue> expected,
         MLuaBehaviorRegistry.Snapshot previous, MLuaContext expectedContext) {
      if (domain == null || key == null) return false;
      ScopedKey scopedKey = new ScopedKey(domain, key, scope(expectedContext));
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         DEFINITIONS.compute(scopedKey, (ignored, current) -> {
            Map<String, LuaValue> actual = current == null ? Map.of() : current.callbacks();
            if (!sameBehaviors(actual, expected == null ? Map.of() : expected)
                  || current != null && current.context() != expectedContext) return current;
            restored.set(true);
            return previous == null || previous.callbacks().isEmpty() ? null
               : new MLuaBehaviorRegistry.BehaviorSet(previous.callbacks(), previous.context(), domain, key);
         });
      }
      return restored.get();
   }

   static void clear(String domain, String key) {
      DEFINITIONS.keySet().removeIf(scopedKey -> scopedKey.domain().equals(domain) && scopedKey.key().equals(key));
   }

   static void clearOwnerPrefix(String prefix) {
      DEFINITIONS.keySet().removeIf(scopedKey -> scopedKey.key().startsWith(prefix));
   }

   static void clearOwner(String owner, MLuaRuntimeSession session, Side side) {
      DEFINITIONS.entrySet().removeIf(entry -> owner.equals(MLuaScriptedOwnerPolicy.ownerOf(
         entry.getValue().context(), entry.getKey().key()))
         && MLuaScriptedOwnerPolicy.inScope(entry.getValue().context(), session, side));
   }

   static int count() { return DEFINITIONS.size(); }

   static int countForOwner(String owner) {
      int count = 0;
      for (Map.Entry<ScopedKey, MLuaBehaviorRegistry.BehaviorSet> entry : DEFINITIONS.entrySet()) {
         if (owner.equals(MLuaScriptedOwnerPolicy.ownerOf(entry.getValue().context(), entry.getKey().key()))) count++;
      }
      return count;
   }

   private static Scope scope(MLuaContext context) {
      return context == null ? GLOBAL : new Scope(context.session(), context.side());
   }

   private static boolean sameBehaviors(Map<String, LuaValue> left, Map<String, LuaValue> right) {
      if (left.size() != right.size()) return false;
      for (Map.Entry<String, LuaValue> entry : left.entrySet()) {
         if (right.get(entry.getKey()) != entry.getValue()) return false;
      }
      return true;
   }

   private static void validateName(String name) {
      if (name.length() > MLuaBehaviorRegistry.MAX_BEHAVIOR_NAME_LENGTH) {
         throw new IllegalArgumentException("Scripted behavior names cannot exceed "
            + MLuaBehaviorRegistry.MAX_BEHAVIOR_NAME_LENGTH + " characters");
      }
   }

   private static void ensureCapacity(ScopedKey key, MLuaContext context) {
      if (DEFINITIONS.containsKey(key)) return;
      if (DEFINITIONS.size() >= MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS) {
         throw new IllegalStateException("Scripted behavior definition limit reached ("
            + MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS + " total)");
      }
      long ownerCount = DEFINITIONS.entrySet().stream()
         .filter(entry -> MLuaScriptedOwnerPolicy.sameOwner(entry.getValue().context(),
            entry.getKey().key(), context, key.key())).count();
      if (ownerCount >= MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted behavior definition limit reached ("
            + MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS_PER_ADDON + ")");
      }
   }

   private record Scope(MLuaRuntimeSession session, Side side) { }
   private record ScopedKey(String domain, String key, Scope scope) { }
}
