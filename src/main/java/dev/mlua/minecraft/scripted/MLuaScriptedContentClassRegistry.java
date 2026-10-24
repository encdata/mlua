package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry.ContentClassDefinition;

/** Owns Lua class bindings for scripted items, blocks, and block entities. */
final class MLuaScriptedContentClassRegistry {
   private static final ConcurrentMap<String, ConcurrentMap<String, ContentClassDefinition>> DEFINITIONS =
      new ConcurrentHashMap<>();

   private MLuaScriptedContentClassRegistry() { }

   static void register(String domain, String key, String className, MLuaContext context) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank()) {
         throw new IllegalArgumentException("Scripted content domain and key must be non-empty");
      }
      if (className == null || className.isBlank()) {
         synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
            DEFINITIONS.computeIfPresent(domain, (ignoredDomain, definitions) -> {
               definitions.remove(key);
               return definitions.isEmpty() ? null : definitions;
            });
         }
         return;
      }

      MLuaBehaviorRegistry.validateBehaviorKey(key);
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         ensureCapacity(domain, key, context);
         DEFINITIONS.compute(domain, (ignoredDomain, definitions) -> {
            ConcurrentMap<String, ContentClassDefinition> result = definitions == null
               ? new ConcurrentHashMap<>() : definitions;
            result.put(key, new ContentClassDefinition(className, context));
            return result;
         });
      }
   }

   static ContentClassDefinition get(String domain, String key) {
      ConcurrentMap<String, ContentClassDefinition> definitions = DEFINITIONS.get(domain);
      return definitions == null ? null : definitions.get(key);
   }

   static boolean restore(String domain, String key, ContentClassDefinition expected,
         ContentClassDefinition previous) {
      if (domain == null || key == null) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         DEFINITIONS.compute(domain, (ignored, definitions) -> {
            ConcurrentMap<String, ContentClassDefinition> values = definitions == null
               ? new ConcurrentHashMap<>() : definitions;
            values.compute(key, (ignoredKey, current) -> {
               if (!java.util.Objects.equals(current, expected)) return current;
               restored.set(true);
               return previous;
            });
            return values.isEmpty() ? null : values;
         });
      }
      return restored.get();
   }

   static int count() {
      int count = 0;
      for (Map<String, ContentClassDefinition> definitions : DEFINITIONS.values()) count += definitions.size();
      return count;
   }

   static int countForOwner(String owner) {
      int count = 0;
      for (Map<String, ContentClassDefinition> definitions : DEFINITIONS.values()) {
         for (Map.Entry<String, ContentClassDefinition> definition : definitions.entrySet()) {
            if (owner.equals(MLuaScriptedOwnerPolicy.ownerOf(definition.getValue().context(), definition.getKey()))) count++;
         }
      }
      return count;
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

   private static void ensureCapacity(String domain, String key, MLuaContext context) {
      int total = 0;
      int ownerCount = 0;
      for (Map.Entry<String, ConcurrentMap<String, ContentClassDefinition>> domainEntry : DEFINITIONS.entrySet()) {
         for (Map.Entry<String, ContentClassDefinition> entry : domainEntry.getValue().entrySet()) {
            if (domainEntry.getKey().equals(domain) && entry.getKey().equals(key)) continue;
            total++;
            if (MLuaScriptedOwnerPolicy.sameOwner(entry.getValue().context(), context, entry.getKey())) ownerCount++;
         }
      }
      if (total >= MLuaBehaviorRegistry.MAX_CONTENT_CLASS_MAPPINGS) {
         throw new IllegalStateException("Scripted content class mapping limit reached ("
            + MLuaBehaviorRegistry.MAX_CONTENT_CLASS_MAPPINGS + " total)");
      }
      if (ownerCount >= MLuaBehaviorRegistry.MAX_CONTENT_CLASS_MAPPINGS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted content class mapping limit reached ("
            + MLuaBehaviorRegistry.MAX_CONTENT_CLASS_MAPPINGS_PER_ADDON + ")");
      }
   }

}
