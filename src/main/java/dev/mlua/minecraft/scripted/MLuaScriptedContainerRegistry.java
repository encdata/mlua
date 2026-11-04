package dev.mlua.minecraft.scripted;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry.ContainerDefinition;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.common.enums.Side;

/** Owns registered scripted block containers and reusable container types. */
final class MLuaScriptedContainerRegistry {
   private static final ConcurrentMap<String, OwnedContainer> BLOCK_CONTAINERS = new ConcurrentHashMap<>();
   private static final ConcurrentMap<String, OwnedContainer> CONTAINER_TYPES = new ConcurrentHashMap<>();

   private MLuaScriptedContainerRegistry() { }

   static ContainerDefinition registerBlock(String key, ContainerDefinition definition) {
      return registerBlock(key, definition, null);
   }

   static ContainerDefinition registerBlock(String key, ContainerDefinition definition, MLuaContext context) {
      requireKey(key, "Scripted container block key");
      MLuaBehaviorRegistry.validateBehaviorKey(key);
      ContainerDefinition registered = validate(definition);
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         ensureCapacity(BLOCK_CONTAINERS, key, MLuaBehaviorRegistry.MAX_BLOCK_CONTAINERS);
         BLOCK_CONTAINERS.put(key, new OwnedContainer(registered, context));
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
         ensureCapacity(CONTAINER_TYPES, key, MLuaBehaviorRegistry.MAX_CONTAINER_TYPES);
         if (CONTAINER_TYPES.putIfAbsent(key, new OwnedContainer(registered, context)) != null) {
            throw new IllegalStateException("Scripted container type is already registered: " + key);
         }
      }
      return registered;
   }

   static ContainerDefinition getBlock(String key) { return definition(BLOCK_CONTAINERS.get(key)); }
   static MLuaContext getBlockContext(String key) {
      OwnedContainer container = BLOCK_CONTAINERS.get(key);
      return container == null ? null : container.context();
   }
   static ContainerDefinition getType(String key) { return definition(CONTAINER_TYPES.get(key)); }
   static Map<String, ContainerDefinition> getTypes() {
      Map<String, ContainerDefinition> result = new java.util.HashMap<>();
      CONTAINER_TYPES.forEach((key, value) -> result.put(key, value.definition()));
      return Map.copyOf(result);
   }
   static int blockCount() { return BLOCK_CONTAINERS.size(); }
   static int typeCount() { return CONTAINER_TYPES.size(); }
   static int blockCountForOwner(String owner) { return countOwnerKeys(BLOCK_CONTAINERS, owner); }
   static int typeCountForOwner(String owner) { return countOwnerKeys(CONTAINER_TYPES, owner); }

   static void clearBlock(String key) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         BLOCK_CONTAINERS.remove(key);
      }
   }

   static boolean unregisterType(String key, ContainerDefinition expected) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         return remove(CONTAINER_TYPES, key, expected);
      }
   }

   static boolean unregisterBlock(String key, ContainerDefinition expected) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         return remove(BLOCK_CONTAINERS, key, expected);
      }
   }

   static boolean restoreBlock(String key, ContainerDefinition expected, ContainerDefinition previous) {
      OwnedContainer current = BLOCK_CONTAINERS.get(key);
      return restoreBlock(key, expected, current == null ? null : current.context(), previous, null);
   }

   static boolean restoreBlock(String key, ContainerDefinition expected, MLuaContext expectedContext,
                               ContainerDefinition previous, MLuaContext previousContext) {
      if (key == null || key.isBlank()) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         BLOCK_CONTAINERS.compute(key, (ignored, current) -> {
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

   static void clearOwnerPrefix(String prefix) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         BLOCK_CONTAINERS.keySet().removeIf(key -> key.startsWith(prefix));
         CONTAINER_TYPES.keySet().removeIf(key -> key.startsWith(prefix));
      }
   }

   static void clearOwner(String owner, MLuaRuntimeSession session, Side side) {
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         clearOwner(BLOCK_CONTAINERS, owner, session, side);
         clearOwner(CONTAINER_TYPES, owner, session, side);
      }
   }

   private static void clearOwner(ConcurrentMap<String, OwnedContainer> values, String owner,
                                  MLuaRuntimeSession session, Side side) {
      values.entrySet().removeIf(entry -> owner.equals(MLuaScriptedOwnerPolicy.ownerOf(
         entry.getValue().context(), entry.getKey()))
         && MLuaScriptedOwnerPolicy.inScope(entry.getValue().context(), session, side));
   }

   private static void requireKey(String key, String label) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException(label + " must be non-empty");
   }

   private static void ensureCapacity(ConcurrentMap<String, OwnedContainer> values,
         String key, int maxTotal) {
      if (values.containsKey(key)) return;
      if (values.size() >= maxTotal) {
         throw new IllegalStateException("Scripted container definition limit reached (" + maxTotal + " total)");
      }
      String prefix = ownerPrefix(key);
      if (!prefix.isEmpty()) {
         int ownerCount = 0;
         for (String existingKey : values.keySet()) if (existingKey.startsWith(prefix)) ownerCount++;
         if (ownerCount >= MLuaBehaviorRegistry.MAX_CONTAINERS_PER_ADDON) {
            throw new IllegalStateException("Addon scripted container definition limit reached ("
               + MLuaBehaviorRegistry.MAX_CONTAINERS_PER_ADDON + ")");
         }
      }
   }

   private static boolean remove(ConcurrentMap<String, OwnedContainer> values,
         String key, ContainerDefinition expected) {
      if (key == null || expected == null) return false;
      java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
      values.computeIfPresent(key, (ignored, current) -> {
         if (current.definition() != expected) return current;
         removed.set(true);
         return null;
      });
      return removed.get();
   }

   private static int countOwnerKeys(Map<String, OwnedContainer> values, String owner) {
      String prefix = owner + ":";
      int count = 0;
      for (String key : values.keySet()) if (key.startsWith(prefix)) count++;
      return count;
   }

   private static ContainerDefinition definition(OwnedContainer container) {
      return container == null ? null : container.definition();
   }

   private record OwnedContainer(ContainerDefinition definition, MLuaContext context) { }

   private static String ownerPrefix(String key) {
      int separator = key.indexOf(':');
      return separator > 0 ? key.substring(0, separator + 1) : "";
   }

}
