package dev.mlua.minecraft.scripted;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry.ContainerDefinition;

/** Owns registered scripted block containers and reusable container types. */
final class MLuaScriptedContainerRegistry {
   private static final ConcurrentMap<String, ContainerDefinition> BLOCK_CONTAINERS = new ConcurrentHashMap<>();
   private static final ConcurrentMap<String, ContainerDefinition> CONTAINER_TYPES = new ConcurrentHashMap<>();

   private MLuaScriptedContainerRegistry() { }

   static ContainerDefinition registerBlock(String key, ContainerDefinition definition) {
      requireKey(key, "Scripted container block key");
      MLuaBehaviorRegistry.validateBehaviorKey(key);
      ContainerDefinition registered = validate(definition);
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         ensureCapacity(BLOCK_CONTAINERS, key, MLuaBehaviorRegistry.MAX_BLOCK_CONTAINERS);
         BLOCK_CONTAINERS.put(key, registered);
      }
      return registered;
   }

   static ContainerDefinition registerType(String key, ContainerDefinition definition) {
      requireKey(key, "Scripted container type key");
      MLuaBehaviorRegistry.validateBehaviorKey(key);
      ContainerDefinition registered = validate(definition);
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         ensureCapacity(CONTAINER_TYPES, key, MLuaBehaviorRegistry.MAX_CONTAINER_TYPES);
         if (CONTAINER_TYPES.putIfAbsent(key, registered) != null) {
            throw new IllegalStateException("Scripted container type is already registered: " + key);
         }
      }
      return registered;
   }

   static ContainerDefinition getBlock(String key) { return BLOCK_CONTAINERS.get(key); }
   static ContainerDefinition getType(String key) { return CONTAINER_TYPES.get(key); }
   static Map<String, ContainerDefinition> getTypes() { return Map.copyOf(CONTAINER_TYPES); }
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
      if (key == null || key.isBlank()) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (MLuaBehaviorRegistry.REGISTRATION_LOCK) {
         BLOCK_CONTAINERS.compute(key, (ignored, current) -> {
            if (current != expected) return current;
            restored.set(true);
            return previous;
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

   private static void requireKey(String key, String label) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException(label + " must be non-empty");
   }

   private static void ensureCapacity(ConcurrentMap<String, ContainerDefinition> values,
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

   private static boolean remove(ConcurrentMap<String, ContainerDefinition> values,
         String key, ContainerDefinition expected) {
      if (key == null || expected == null) return false;
      java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
      values.computeIfPresent(key, (ignored, current) -> {
         if (current != expected) return current;
         removed.set(true);
         return null;
      });
      return removed.get();
   }

   private static int countOwnerKeys(Map<String, ?> values, String owner) {
      String prefix = owner + ":";
      int count = 0;
      for (String key : values.keySet()) if (key.startsWith(prefix)) count++;
      return count;
   }

   private static String ownerPrefix(String key) {
      int separator = key.indexOf(':');
      return separator > 0 ? key.substring(0, separator + 1) : "";
   }

}
