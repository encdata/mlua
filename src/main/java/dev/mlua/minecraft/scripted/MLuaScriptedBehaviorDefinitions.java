package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.luaj.vm2.LuaValue;

/** Owns registered behavior callback sets and their reload-safe replacement semantics. */
final class MLuaScriptedBehaviorDefinitions {
   private static final ConcurrentMap<String, ConcurrentMap<String, MLuaBehaviorRegistry.BehaviorSet>> DEFINITIONS =
      new ConcurrentHashMap<>();

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
      if (behavior == null || behavior.isnil()) {
         synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
            DEFINITIONS.computeIfPresent(domain, (ignoredDomain, definitions) -> {
               definitions.computeIfPresent(key, (ignored, current) -> {
                  Map<String, LuaValue> updated = new HashMap<>(current.callbacks());
                  updated.remove(event);
                  return updated.isEmpty() ? null : new MLuaBehaviorRegistry.BehaviorSet(
                     updated, current.context(), domain, key);
               });
               return definitions.isEmpty() ? null : definitions;
            });
         }
         return;
      }

      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         ConcurrentMap<String, MLuaBehaviorRegistry.BehaviorSet> definitions = DEFINITIONS.get(domain);
         MLuaBehaviorRegistry.BehaviorSet current = definitions == null ? null : definitions.get(key);
         Map<String, LuaValue> updated = current == null
            ? new HashMap<>() : new HashMap<>(current.callbacks());
         updated.put(event, behavior);
         ensureCapacity(domain, key, current == null ? null : current.context());
         if (updated.size() > MLuaBehaviorRegistry.MAX_CALLBACKS_PER_DEFINITION) {
            throw new IllegalStateException("Scripted behavior callback limit reached ("
               + MLuaBehaviorRegistry.MAX_CALLBACKS_PER_DEFINITION + " per definition)");
         }
         DEFINITIONS.compute(domain, (ignoredDomain, currentDefinitions) -> {
            ConcurrentMap<String, MLuaBehaviorRegistry.BehaviorSet> result = currentDefinitions == null
               ? new ConcurrentHashMap<>() : currentDefinitions;
            result.put(key, new MLuaBehaviorRegistry.BehaviorSet(
               updated, current == null ? null : current.context(), domain, key));
            return result;
         });
      }
      MLuaLogger.LOGGER.debug("Registered {} behavior '{}' for {}", domain, event, key);
   }

   /** Atomically replaces all callbacks registered for one content definition. */
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
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         if (!definition.isEmpty()) ensureCapacity(domain, key, context);
         DEFINITIONS.compute(domain, (ignoredDomain, values) -> {
            if (values == null && definition.isEmpty()) return null;
            ConcurrentMap<String, MLuaBehaviorRegistry.BehaviorSet> result = values == null
               ? new ConcurrentHashMap<>() : values;
            if (definition.isEmpty()) result.remove(key);
            else result.put(key, new MLuaBehaviorRegistry.BehaviorSet(definition, context, domain, key));
            return result.isEmpty() ? null : result;
         });
      }
      return Map.copyOf(definition);
   }

   static MLuaBehaviorRegistry.BehaviorSet definition(String domain, String key) {
      ConcurrentMap<String, MLuaBehaviorRegistry.BehaviorSet> definitions = DEFINITIONS.get(domain);
      return definitions == null ? null : definitions.get(key);
   }

   static boolean restore(String domain, String key, Map<String, LuaValue> expected,
         MLuaBehaviorRegistry.Snapshot previous, MLuaContext expectedContext) {
      if (domain == null || key == null) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         DEFINITIONS.compute(domain, (ignored, definitions) -> {
            ConcurrentMap<String, MLuaBehaviorRegistry.BehaviorSet> values = definitions == null
               ? new ConcurrentHashMap<>() : definitions;
            values.compute(key, (ignoredKey, current) -> {
               Map<String, LuaValue> actual = current == null ? Map.of() : current.callbacks();
               if (!sameBehaviors(actual, expected == null ? Map.of() : expected)
                     || current != null && current.context() != expectedContext) return current;
               restored.set(true);
               return previous == null || previous.callbacks().isEmpty() ? null
                  : new MLuaBehaviorRegistry.BehaviorSet(previous.callbacks(), previous.context(), domain, key);
            });
            return values.isEmpty() ? null : values;
         });
      }
      return restored.get();
   }

   static void clear(String domain, String key) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         DEFINITIONS.computeIfPresent(domain, (ignoredDomain, definitions) -> {
            definitions.remove(key);
            return definitions.isEmpty() ? null : definitions;
         });
      }
   }

   static void clearOwnerPrefix(String prefix) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         DEFINITIONS.forEach((domain, ignored) -> DEFINITIONS.computeIfPresent(domain,
            (domainKey, definitions) -> {
               definitions.keySet().removeIf(key -> key.startsWith(prefix));
               return definitions.isEmpty() ? null : definitions;
            }));
      }
   }

   static int count() {
      int count = 0;
      for (Map<String, MLuaBehaviorRegistry.BehaviorSet> values : DEFINITIONS.values()) count += values.size();
      return count;
   }

   static int countForOwner(String owner) {
      int count = 0;
      for (Map<String, MLuaBehaviorRegistry.BehaviorSet> values : DEFINITIONS.values()) {
         for (Map.Entry<String, MLuaBehaviorRegistry.BehaviorSet> entry : values.entrySet()) {
            if (owner.equals(MLuaScriptedOwnerPolicy.ownerOf(entry.getValue().context(), entry.getKey()))) count++;
         }
      }
      return count;
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

   private static void ensureCapacity(String domain, String key, MLuaContext context) {
      int total = 0;
      int ownerCount = 0;
      for (Map.Entry<String, ConcurrentMap<String, MLuaBehaviorRegistry.BehaviorSet>> domainEntry : DEFINITIONS.entrySet()) {
         for (Map.Entry<String, MLuaBehaviorRegistry.BehaviorSet> entry : domainEntry.getValue().entrySet()) {
            if (domainEntry.getKey().equals(domain) && entry.getKey().equals(key)) continue;
            total++;
            if (MLuaScriptedOwnerPolicy.sameOwner(entry.getValue().context(), context, entry.getKey())) ownerCount++;
         }
      }
      if (total >= MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS) {
         throw new IllegalStateException("Scripted behavior definition limit reached ("
            + MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS + " total)");
      }
      if (ownerCount >= MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted behavior definition limit reached ("
            + MLuaBehaviorRegistry.MAX_BEHAVIOR_DEFINITIONS_PER_ADDON + ")");
      }
   }

}
