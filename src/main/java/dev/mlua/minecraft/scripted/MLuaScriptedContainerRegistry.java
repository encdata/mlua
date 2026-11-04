package dev.mlua.minecraft.scripted;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry.ContainerDefinition;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.common.enums.Side;
import net.minecraft.world.level.Level;

/** Owns registered scripted block containers and reusable container types. */
final class MLuaScriptedContainerRegistry {
   private static final Scope GLOBAL = new Scope(null, Side.BOTH);
   private static final ConcurrentMap<ScopedKey, OwnedContainer> BLOCK_CONTAINERS = new ConcurrentHashMap<>();
   private static final ConcurrentMap<ScopedKey, OwnedContainer> CONTAINER_TYPES = new ConcurrentHashMap<>();

   private MLuaScriptedContainerRegistry() { }

   static ContainerDefinition registerBlock(String key, ContainerDefinition definition) {
      return registerBlock(key, definition, null);
   }

   static ContainerDefinition registerBlock(String key, ContainerDefinition definition, MLuaContext context) {
      requireKey(key, "Scripted container block key");
      MLuaBehaviorRegistry.validateBehaviorKey(key);
      ContainerDefinition registered = validate(definition);
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         ScopedKey scopedKey = scopedKey(key, context);
         ensureCapacity(BLOCK_CONTAINERS, scopedKey, MLuaBehaviorRegistry.MAX_BLOCK_CONTAINERS, context);
         BLOCK_CONTAINERS.put(scopedKey, new OwnedContainer(registered, context));
      }
      return registered;
   }

   static ContainerDefinition registerType(String key, ContainerDefinition definition) {
      return registerType(key, definition, null);
   }

   static ContainerDefinition registerType(String key, ContainerDefinition definition, MLuaContext context) {
      requireKey(key, "Scripted container type key");
      MLuaBehaviorRegistry.validateBehaviorKey(key);
      ContainerDefinition registered = validate(definition);
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         ScopedKey scopedKey = scopedKey(key, context);
         ensureCapacity(CONTAINER_TYPES, scopedKey, MLuaBehaviorRegistry.MAX_CONTAINER_TYPES, context);
         if (CONTAINER_TYPES.putIfAbsent(scopedKey, new OwnedContainer(registered, context)) != null) {
            throw new IllegalStateException("Scripted container type is already registered: " + key);
         }
      }
      return registered;
   }

   static ContainerDefinition getBlock(String key) { return definition(lookup(BLOCK_CONTAINERS, key)); }
   static ContainerDefinition getBlock(String key, Level level) {
      return definition(level == null ? lookup(BLOCK_CONTAINERS, key)
         : lookup(BLOCK_CONTAINERS, key, MLuaRuntime.sessionForLevel(level), level.isClientSide() ? Side.CLIENT : Side.SERVER));
   }
   static ContainerDefinition getBlock(String key, MLuaContext context) {
      return definition(lookup(BLOCK_CONTAINERS, key, scope(context)));
   }
   static MLuaContext getBlockContext(String key) {
      OwnedContainer container = lookup(BLOCK_CONTAINERS, key);
      return container == null ? null : container.context();
   }
   static ContainerDefinition getType(String key) { return definition(lookup(CONTAINER_TYPES, key)); }
   static ContainerDefinition getType(String key, MLuaContext context) {
      return definition(lookup(CONTAINER_TYPES, key, scope(context)));
   }
   static Map<String, ContainerDefinition> getTypes() {
      Map<String, ContainerDefinition> result = new java.util.HashMap<>();
      CONTAINER_TYPES.keySet().stream().map(ScopedKey::key).distinct().forEach(key -> {
         OwnedContainer container = lookup(CONTAINER_TYPES, key);
         if (container != null) result.put(key, container.definition());
      });
      return Map.copyOf(result);
   }
   static Map<String, ContainerDefinition> getTypes(MLuaContext context) {
      Scope requested = scope(context);
      Map<String, ContainerDefinition> result = new java.util.HashMap<>();
      CONTAINER_TYPES.forEach((key, value) -> {
         if (key.scope().equals(GLOBAL)) result.put(key.key(), value.definition());
      });
      if (!requested.equals(GLOBAL)) CONTAINER_TYPES.forEach((key, value) -> {
         if (key.scope().equals(requested)) result.put(key.key(), value.definition());
      });
      return Map.copyOf(result);
   }
   static int blockCount() { return BLOCK_CONTAINERS.size(); }
   static int typeCount() { return CONTAINER_TYPES.size(); }
   static int blockCountForOwner(String owner) { return countOwnerKeys(BLOCK_CONTAINERS, owner); }
   static int typeCountForOwner(String owner) { return countOwnerKeys(CONTAINER_TYPES, owner); }

   static void clearBlock(String key) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         BLOCK_CONTAINERS.keySet().removeIf(scopedKey -> scopedKey.key().equals(key));
      }
   }

   static boolean unregisterType(String key, ContainerDefinition expected) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         return remove(CONTAINER_TYPES, key, expected, null);
      }
   }

   static boolean unregisterType(String key, ContainerDefinition expected, MLuaContext context) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         return remove(CONTAINER_TYPES, key, expected, scope(context));
      }
   }

   static boolean unregisterBlock(String key, ContainerDefinition expected) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         return remove(BLOCK_CONTAINERS, key, expected, null);
      }
   }

   static boolean unregisterBlock(String key, ContainerDefinition expected, MLuaContext context) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         return remove(BLOCK_CONTAINERS, key, expected, scope(context));
      }
   }

   static boolean restoreBlock(String key, ContainerDefinition expected, ContainerDefinition previous) {
      OwnedContainer current = lookup(BLOCK_CONTAINERS, key);
      return restoreBlock(key, expected, current == null ? null : current.context(), previous, null);
   }

   static boolean restoreBlock(String key, ContainerDefinition expected, MLuaContext expectedContext,
                               ContainerDefinition previous, MLuaContext previousContext) {
      if (key == null || key.isBlank()) return false;
      ScopedKey scopedKey = scopedKey(key, expectedContext);
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         BLOCK_CONTAINERS.compute(scopedKey, (ignored, current) -> {
            if (definition(current) != expected || current != null && current.context() != expectedContext) return current;
            restored.set(true);
            return previous == null ? null : new OwnedContainer(previous, previousContext);
         });
      }
      return restored.get();
   }

   static ContainerDefinition validate(ContainerDefinition definition) {
      if (definition == null) throw new IllegalArgumentException("Scripted container definition is required");
      if (definition.rows() < 1 || definition.rows() > 6) {
         throw new IllegalArgumentException("Scripted container rows must be from 1 to 6");
      }
      if (definition.title() == null || definition.title().isBlank()) {
         throw new IllegalArgumentException("Scripted container title must be non-empty");
      }
      if (definition.title().length() > MLuaBehaviorRegistry.MAX_CONTAINER_TITLE_LENGTH) {
         throw new IllegalArgumentException("Scripted container titles cannot exceed "
            + MLuaBehaviorRegistry.MAX_CONTAINER_TITLE_LENGTH + " characters");
      }
      int itemReferences = definition.allowedItems().size();
      if (itemReferences > MLuaBehaviorRegistry.MAX_ALLOWED_ITEMS_PER_RULE) {
         throw new IllegalArgumentException("Scripted container item rule limit reached ("
            + MLuaBehaviorRegistry.MAX_ALLOWED_ITEMS_PER_RULE + ")");
      }
      if (definition.slotAllowedItems().size() > definition.rows() * 9) {
         throw new IllegalArgumentException("Scripted container slot rule count cannot exceed its slot count");
      }
      if (definition.allowedItems().stream().anyMatch(item -> item.isBlank() || !item.contains(":"))) {
         throw new IllegalArgumentException("Scripted container allowed item ids must be non-empty and namespaced");
      }
      for (Map.Entry<Integer, java.util.Set<String>> slot : definition.slotAllowedItems().entrySet()) {
         itemReferences += slot.getValue().size();
         if (slot.getValue().size() > MLuaBehaviorRegistry.MAX_ALLOWED_ITEMS_PER_RULE) {
            throw new IllegalArgumentException("Scripted container slot item rule limit reached ("
               + MLuaBehaviorRegistry.MAX_ALLOWED_ITEMS_PER_RULE + ")");
         }
         if (slot.getKey() == null || slot.getKey() < 1 || slot.getKey() > definition.rows() * 9) {
            throw new IllegalArgumentException(
               "Scripted container slot rules must use one-based slots within the registered row count");
         }
         if (slot.getValue().stream().anyMatch(item -> item.isBlank() || !item.contains(":"))) {
            throw new IllegalArgumentException("Scripted container slot item ids must be non-empty and namespaced");
         }
      }
      if (itemReferences > MLuaBehaviorRegistry.MAX_ALLOWED_ITEM_REFERENCES_PER_CONTAINER) {
         throw new IllegalArgumentException("Scripted container item rule limit reached ("
            + MLuaBehaviorRegistry.MAX_ALLOWED_ITEM_REFERENCES_PER_CONTAINER + " item references per container)");
      }
      return definition;
   }

   static void clearOwner(String owner) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         clearOwner(BLOCK_CONTAINERS, owner);
         clearOwner(CONTAINER_TYPES, owner);
      }
   }

   private static void clearOwner(ConcurrentMap<ScopedKey, OwnedContainer> values, String owner) {
      values.entrySet().removeIf(entry -> owner.equals(MLuaScriptedOwnerPolicy.ownerOf(
         entry.getValue().context(), entry.getKey().key())));
   }

   static void clearOwner(String owner, MLuaRuntimeSession session, Side side) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         clearOwner(BLOCK_CONTAINERS, owner, session, side);
         clearOwner(CONTAINER_TYPES, owner, session, side);
      }
   }

   private static void clearOwner(ConcurrentMap<ScopedKey, OwnedContainer> values, String owner,
                                  MLuaRuntimeSession session, Side side) {
      values.entrySet().removeIf(entry -> owner.equals(MLuaScriptedOwnerPolicy.ownerOf(
         entry.getValue().context(), entry.getKey().key()))
         && MLuaScriptedOwnerPolicy.inScope(entry.getValue().context(), session, side));
   }

   private static void requireKey(String key, String label) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException(label + " must be non-empty");
   }

   private static void ensureCapacity(ConcurrentMap<ScopedKey, OwnedContainer> values,
         ScopedKey key, int maxTotal, MLuaContext context) {
      if (values.containsKey(key)) return;
      if (values.size() >= maxTotal) {
         throw new IllegalStateException("Scripted container definition limit reached (" + maxTotal + " total)");
      }
      long ownerCount = values.entrySet().stream().filter(entry ->
         MLuaScriptedOwnerPolicy.sameOwner(entry.getValue().context(), entry.getKey().key(),
            context, key.key())).count();
      if (ownerCount >= MLuaBehaviorRegistry.MAX_CONTAINERS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted container definition limit reached ("
            + MLuaBehaviorRegistry.MAX_CONTAINERS_PER_ADDON + ")");
      }
   }

   private static boolean remove(ConcurrentMap<ScopedKey, OwnedContainer> values,
         String key, ContainerDefinition expected, Scope scope) {
      if (key == null || expected == null) return false;
      java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
      for (ScopedKey scopedKey : values.keySet()) {
         if (!scopedKey.key().equals(key) || scope != null && !scopedKey.scope().equals(scope)) continue;
         values.computeIfPresent(scopedKey, (ignored, current) -> {
            if (current.definition() != expected) return current;
            removed.set(true);
            return null;
         });
      }
      return removed.get();
   }

   private static int countOwnerKeys(Map<ScopedKey, OwnedContainer> values, String owner) {
      int count = 0;
      for (Map.Entry<ScopedKey, OwnedContainer> entry : values.entrySet()) {
         if (owner.equals(MLuaScriptedOwnerPolicy.ownerOf(
               entry.getValue().context(), entry.getKey().key()))) count++;
      }
      return count;
   }

   private static ContainerDefinition definition(OwnedContainer container) {
      return container == null ? null : container.definition();
   }

   private static OwnedContainer lookup(ConcurrentMap<ScopedKey, OwnedContainer> values, String key) {
      OwnedContainer global = values.get(new ScopedKey(key, GLOBAL));
      if (global != null) return global;
      OwnedContainer only = null;
      for (Map.Entry<ScopedKey, OwnedContainer> entry : values.entrySet())
         if (entry.getKey().key().equals(key)) {
            if (only != null) return null;
            only = entry.getValue();
         }
      return only;
   }

   private static OwnedContainer lookup(ConcurrentMap<ScopedKey, OwnedContainer> values, String key, Scope scope) {
      OwnedContainer scoped = values.get(new ScopedKey(key, scope));
      return scoped != null ? scoped : values.get(new ScopedKey(key, GLOBAL));
   }

   private static OwnedContainer lookup(ConcurrentMap<ScopedKey, OwnedContainer> values, String key,
                                        MLuaRuntimeSession session, Side side) {
      return lookup(values, key, new Scope(session, side));
   }

   private static ScopedKey scopedKey(String key, MLuaContext context) {
      return new ScopedKey(key, scope(context));
   }

   private static Scope scope(MLuaContext context) {
      return context == null ? GLOBAL : new Scope(context.session(), context.side());
   }

   private record Scope(MLuaRuntimeSession session, Side side) { }
   private record ScopedKey(String key, Scope scope) { }
   private record OwnedContainer(ContainerDefinition definition, MLuaContext context) { }

}
