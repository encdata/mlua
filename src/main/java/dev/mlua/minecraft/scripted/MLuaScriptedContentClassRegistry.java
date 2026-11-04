package dev.mlua.minecraft.scripted;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry.ContentClassDefinition;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Owns Lua class bindings for scripted items, blocks, and block entities. */
final class MLuaScriptedContentClassRegistry {
   private static final Scope GLOBAL = new Scope(null, Side.BOTH);
   private static final ConcurrentMap<ScopedKey, ContentClassDefinition> DEFINITIONS = new ConcurrentHashMap<>();

   private MLuaScriptedContentClassRegistry() { }

   static ContentClassDefinition register(String domain, String key, String className, MLuaContext context) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank()) {
         throw new IllegalArgumentException("Scripted content domain and key must be non-empty");
      }
      Scope scope = scope(context);
      ScopedKey scopedKey = new ScopedKey(domain, key, scope);
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         if (className == null || className.isBlank()) {
            DEFINITIONS.remove(scopedKey);
            return null;
         }
         MLuaBehaviorRegistry.validateBehaviorKey(key);
         ensureCapacity(scopedKey, context);
         ContentClassDefinition definition = new ContentClassDefinition(className, context);
         DEFINITIONS.put(scopedKey, definition);
         return definition;
      }
   }

   static ContentClassDefinition get(String domain, String key) {
      ContentClassDefinition global = DEFINITIONS.get(new ScopedKey(domain, key, GLOBAL));
      if (global != null) return global;
      ContentClassDefinition only = null;
      for (Map.Entry<ScopedKey, ContentClassDefinition> entry : DEFINITIONS.entrySet()) {
         if (entry.getKey().domain().equals(domain) && entry.getKey().key().equals(key)) {
            if (only != null) return null;
            only = entry.getValue();
         }
      }
      return only;
   }

   static ContentClassDefinition get(String domain, String key, MLuaRuntimeSession session, Side side) {
      ContentClassDefinition scoped = DEFINITIONS.get(new ScopedKey(domain, key, new Scope(session, side)));
      return scoped != null ? scoped : DEFINITIONS.get(new ScopedKey(domain, key, GLOBAL));
   }

   static ContentClassDefinition get(String domain, String key, MLuaContext context) {
      Scope scope = scope(context);
      return get(domain, key, scope.session(), scope.side());
   }

   static boolean restore(String domain, String key, ContentClassDefinition expected,
                          ContentClassDefinition previous, MLuaContext context) {
      return restore(domain, key, expected, previous, context, false);
   }

   static boolean restoreExact(String domain, String key, ContentClassDefinition expected,
                               ContentClassDefinition previous, MLuaContext context) {
      return restore(domain, key, expected, previous, context, true);
   }

   private static boolean restore(String domain, String key, ContentClassDefinition expected,
                                  ContentClassDefinition previous, MLuaContext context, boolean exact) {
      if (domain == null || key == null) return false;
      ScopedKey scopedKey = new ScopedKey(domain, key, scope(context));
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         DEFINITIONS.compute(scopedKey, (ignored, current) -> {
            if (exact ? current != expected : !java.util.Objects.equals(current, expected)) return current;
            restored.set(true);
            return previous;
         });
      }
      return restored.get();
   }

   static int count() { return DEFINITIONS.size(); }

   static int countForOwner(String owner) {
      int count = 0;
      for (Map.Entry<ScopedKey, ContentClassDefinition> entry : DEFINITIONS.entrySet()) {
         if (owner.equals(MLuaScriptedOwnerPolicy.ownerOf(entry.getValue().context(), entry.getKey().key()))) count++;
      }
      return count;
   }

   static void clearOwner(String owner) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         DEFINITIONS.entrySet().removeIf(entry -> owner.equals(MLuaScriptedOwnerPolicy.ownerOf(
            entry.getValue().context(), entry.getKey().key())));
      }
   }

   static void clearOwner(String owner, MLuaRuntimeSession session, Side side) {
      DEFINITIONS.entrySet().removeIf(entry -> owner.equals(MLuaScriptedOwnerPolicy.ownerOf(
         entry.getValue().context(), entry.getKey().key()))
         && MLuaScriptedOwnerPolicy.inScope(entry.getValue().context(), session, side));
   }

   private static Scope scope(MLuaContext context) {
      return context == null ? GLOBAL : new Scope(context.session(), context.side());
   }

   private static void ensureCapacity(ScopedKey key, MLuaContext context) {
      if (DEFINITIONS.containsKey(key)) return;
      if (DEFINITIONS.size() >= MLuaBehaviorRegistry.MAX_CONTENT_CLASS_MAPPINGS) {
         throw new IllegalStateException("Scripted content class mapping limit reached ("
            + MLuaBehaviorRegistry.MAX_CONTENT_CLASS_MAPPINGS + " total)");
      }
      long ownerCount = DEFINITIONS.entrySet().stream()
         .filter(entry -> MLuaScriptedOwnerPolicy.sameOwner(entry.getValue().context(),
            entry.getKey().key(), context, key.key())).count();
      if (ownerCount >= MLuaBehaviorRegistry.MAX_CONTENT_CLASS_MAPPINGS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted content class mapping limit reached ("
            + MLuaBehaviorRegistry.MAX_CONTENT_CLASS_MAPPINGS_PER_ADDON + ")");
      }
   }

   private record Scope(MLuaRuntimeSession session, Side side) { }
   private record ScopedKey(String domain, String key, Scope scope) { }
}
