package dev.mlua.minecraft.scripted;

import org.luaj.vm2.LuaValue;
import dev.mlua.core.MLuaLogger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;

public final class MLuaBehaviorRegistry {
   private static final ConcurrentMap<String, ConcurrentMap<String, Map<String, LuaValue>>> SCRIPTED_BEHAVIORS = new ConcurrentHashMap<>();
   private static final ConcurrentMap<String, ConcurrentMap<String, String>> CONTENT_CLASSES = new ConcurrentHashMap<>();
   private static final ConcurrentMap<String, ContainerDefinition> BLOCK_CONTAINERS = new ConcurrentHashMap<>();
   private static final ConcurrentMap<String, ContainerDefinition> CONTAINER_TYPES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> ENTITY_AI_INTERVAL = new ConcurrentHashMap();

   private MLuaBehaviorRegistry() {
   }

   public static void clearItemCallbacks(String key) {
      clearBehaviors("item", key);
   }

   public static void clearBlockCallbacks(String key) {
      clearBehaviors("block", key);
      BLOCK_CONTAINERS.remove(key);
   }

   public static void registerBehavior(String domain, String key, String event, LuaValue behavior) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank() || event == null || event.isBlank()) {
         throw new IllegalArgumentException("Scripted behavior domain, key, and member must be non-empty");
      }
      if (behavior == null || behavior.isnil()) {
         SCRIPTED_BEHAVIORS.computeIfPresent(domain, (ignoredDomain, definitions) -> {
            definitions.computeIfPresent(key, (ignored, current) -> {
               Map<String, LuaValue> updated = new java.util.HashMap<>(current);
               updated.remove(event);
               return updated.isEmpty() ? null : Map.copyOf(updated);
            });
            return definitions.isEmpty() ? null : definitions;
         });
         return;
      }
      SCRIPTED_BEHAVIORS.compute(domain, (ignoredDomain, definitions) -> {
         ConcurrentMap<String, Map<String, LuaValue>> result = definitions == null
            ? new ConcurrentHashMap<>() : definitions;
         result.compute(key, (ignoredKey, current) -> {
            Map<String, LuaValue> updated = current == null ? new java.util.HashMap<>() : new java.util.HashMap<>(current);
            updated.put(event, behavior);
            return Map.copyOf(updated);
         });
         return result;
      });
      MLuaLogger.LOGGER.debug("Registered {} behavior '{}' for {}", domain, event, key);
   }

   /** Replaces a content definition's complete behavior set in one visible update. */
   public static void registerBehaviors(String domain, String key, Map<String, LuaValue> behaviors) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank()) {
         throw new IllegalArgumentException("Scripted behavior domain and key must be non-empty");
      }
      Map<String, LuaValue> definition = new java.util.HashMap<>();
      if (behaviors != null) {
         behaviors.forEach((event, behavior) -> {
            if (event != null && !event.isBlank() && behavior != null && !behavior.isnil()) definition.put(event, behavior);
         });
      }
      SCRIPTED_BEHAVIORS.compute(domain, (ignoredDomain, definitions) -> {
         if (definitions == null && definition.isEmpty()) return null;
         ConcurrentMap<String, Map<String, LuaValue>> result = definitions == null
            ? new ConcurrentHashMap<>() : definitions;
         if (definition.isEmpty()) result.remove(key);
         else result.put(key, Map.copyOf(definition));
         return result.isEmpty() ? null : result;
      });
   }

   public static LuaValue getBehavior(String domain, String key, String event) {
      ConcurrentMap<String, Map<String, LuaValue>> definitions = SCRIPTED_BEHAVIORS.get(domain);
      if (definitions == null) return LuaValue.NIL;
      Map<String, LuaValue> behaviors = definitions.get(key);
      return behaviors == null ? LuaValue.NIL : behaviors.getOrDefault(event, LuaValue.NIL);
   }

   /** Compatibility name used by scripted content dispatchers. */
   public static LuaValue getCallback(String domain, String key, String event) {
      return getBehavior(domain, key, event);
   }

   public static void registerContentClass(String domain, String key, String className) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank()) {
         throw new IllegalArgumentException("Scripted content domain and key must be non-empty");
      }
      if (className == null || className.isBlank()) {
         CONTENT_CLASSES.computeIfPresent(domain, (ignoredDomain, definitions) -> {
            definitions.remove(key);
            return definitions.isEmpty() ? null : definitions;
         });
         return;
      }
      CONTENT_CLASSES.compute(domain, (ignoredDomain, definitions) -> {
         ConcurrentMap<String, String> result = definitions == null ? new ConcurrentHashMap<>() : definitions;
         result.put(key, className);
         return result;
      });
   }

   public static String getContentClass(String domain, String key) {
      ConcurrentMap<String, String> definitions = CONTENT_CLASSES.get(domain);
      return definitions == null ? null : definitions.get(key);
   }

   private static void clearBehaviors(String domain, String key) {
      SCRIPTED_BEHAVIORS.computeIfPresent(domain, (ignoredDomain, definitions) -> {
         definitions.remove(key);
         return definitions.isEmpty() ? null : definitions;
      });
   }

   public static void registerBlockContainer(String key, int rows, String title) {
      registerBlockContainer(key, new ContainerDefinition(rows, title));
   }

   public static void registerBlockContainer(String key, ContainerDefinition definition) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException("Scripted container block key must be non-empty");
      BLOCK_CONTAINERS.put(key, validateContainerDefinition(definition));
   }

   public static void registerContainerType(String key, int rows, String title) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException("Scripted container type key must be non-empty");
      CONTAINER_TYPES.put(key, validateContainerDefinition(new ContainerDefinition(rows, title)));
   }

   public static void registerContainerType(String key, ContainerDefinition definition) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException("Scripted container type key must be non-empty");
      CONTAINER_TYPES.put(key, validateContainerDefinition(definition));
   }

   public static ContainerDefinition getContainerType(String key) { return CONTAINER_TYPES.get(key); }

   private static ContainerDefinition validateContainerDefinition(ContainerDefinition definition) {
      if (definition == null) throw new IllegalArgumentException("Scripted container definition is required");
      if (definition.rows() < 1 || definition.rows() > 6) throw new IllegalArgumentException("Scripted container rows must be from 1 to 6");
      if (definition.title() == null || definition.title().isBlank()) throw new IllegalArgumentException("Scripted container title must be non-empty");
      if (definition.allowedItems().stream().anyMatch(item -> item.isBlank() || !item.contains(":"))) {
         throw new IllegalArgumentException("Scripted container allowed item ids must be non-empty and namespaced");
      }
      return definition;
   }

   public static ContainerDefinition getBlockContainer(String key) { return BLOCK_CONTAINERS.get(key); }

   public record ContainerDefinition(int rows, String title, java.util.Set<String> allowedItems) {
      public ContainerDefinition(int rows, String title) { this(rows, title, java.util.Set.of()); }
      public ContainerDefinition {
         allowedItems = allowedItems == null ? java.util.Set.of() : java.util.Set.copyOf(allowedItems);
      }
      public boolean allows(net.minecraft.world.item.ItemStack stack) {
         if (stack == null || stack.isEmpty() || allowedItems.isEmpty()) return true;
         net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
         return id != null && allowedItems.contains(id.toString());
      }
   }

   public static void clearEntityCallbacks(String key) {
      clearBehaviors("entity", key);
      ENTITY_AI_INTERVAL.remove(key);
   }

   public static void clearEffectCallbacks(String key) {
      clearBehaviors("effect", key);
   }

   /** Removes scripted definitions contributed by one addon during reload or unload. */
   public static void clearOwner(String owner) {
      if (owner == null || owner.isBlank()) return;
      String prefix = owner.trim() + ":";
      SCRIPTED_BEHAVIORS.forEach((domain, ignored) -> SCRIPTED_BEHAVIORS.computeIfPresent(domain, (domainKey, definitions) -> {
         definitions.keySet().removeIf(behaviorKey -> behaviorKey.startsWith(prefix));
         return definitions.isEmpty() ? null : definitions;
      }));
      CONTENT_CLASSES.forEach((domain, ignored) -> CONTENT_CLASSES.computeIfPresent(domain, (domainKey, definitions) -> {
         definitions.keySet().removeIf(key -> key.startsWith(prefix));
         return definitions.isEmpty() ? null : definitions;
      }));
      BLOCK_CONTAINERS.keySet().removeIf(key -> key.startsWith(prefix));
      CONTAINER_TYPES.keySet().removeIf(key -> key.startsWith(prefix));
      ENTITY_AI_INTERVAL.keySet().removeIf(key -> key.startsWith(prefix));
   }

    public static void registerEntityAIInterval(String key, int interval) {
       ENTITY_AI_INTERVAL.put(key, Math.max(1, Math.min(120, interval)));
    }

    public static int getEntityAIInterval(String key) {
       return ENTITY_AI_INTERVAL.getOrDefault(key, 5);
    }

}
