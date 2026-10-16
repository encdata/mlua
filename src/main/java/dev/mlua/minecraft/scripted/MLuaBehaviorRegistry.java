package dev.mlua.minecraft.scripted;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;

public final class MLuaBehaviorRegistry {
   private record BehaviorSet(Map<String, LuaValue> callbacks, MLuaContext context) { }
   public record Snapshot(Map<String, LuaValue> callbacks, MLuaContext context) { }
   private static final ConcurrentMap<String, ConcurrentMap<String, BehaviorSet>> SCRIPTED_BEHAVIORS = new ConcurrentHashMap<>();
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
      clearBehaviors("block_entity", key);
      BLOCK_CONTAINERS.remove(key);
   }

   public static void registerBehavior(String domain, String key, String event, LuaValue behavior) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank() || event == null || event.isBlank()) {
         throw new IllegalArgumentException("Scripted behavior domain, key, and member must be non-empty");
      }
      if (behavior == null || behavior.isnil()) {
         SCRIPTED_BEHAVIORS.computeIfPresent(domain, (ignoredDomain, definitions) -> {
            definitions.computeIfPresent(key, (ignored, current) -> {
               Map<String, LuaValue> updated = new java.util.HashMap<>(current.callbacks());
               updated.remove(event);
               return updated.isEmpty() ? null : new BehaviorSet(Map.copyOf(updated), current.context());
            });
            return definitions.isEmpty() ? null : definitions;
         });
         return;
      }
      SCRIPTED_BEHAVIORS.compute(domain, (ignoredDomain, definitions) -> {
         ConcurrentMap<String, BehaviorSet> result = definitions == null
            ? new ConcurrentHashMap<>() : definitions;
         result.compute(key, (ignoredKey, current) -> {
            Map<String, LuaValue> updated = current == null ? new java.util.HashMap<>() : new java.util.HashMap<>(current.callbacks());
            updated.put(event, behavior);
            return new BehaviorSet(Map.copyOf(updated), current == null ? null : current.context());
         });
         return result;
      });
      MLuaLogger.LOGGER.debug("Registered {} behavior '{}' for {}", domain, event, key);
   }

   /** Replaces a content definition's complete behavior set in one visible update. */
   public static void registerBehaviors(String domain, String key, Map<String, LuaValue> behaviors) {
      registerBehaviors(domain, key, behaviors, null);
   }

   public static void registerBehaviors(String domain, String key, Map<String, LuaValue> behaviors,
                                        MLuaContext context) {
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
         ConcurrentMap<String, BehaviorSet> result = definitions == null
            ? new ConcurrentHashMap<>() : definitions;
         if (definition.isEmpty()) result.remove(key);
         else result.put(key, new BehaviorSet(Map.copyOf(definition), context));
         return result.isEmpty() ? null : result;
      });
   }

   public static LuaValue getBehavior(String domain, String key, String event) {
      BehaviorSet definition = definition(domain, key);
      return definition == null ? LuaValue.NIL : definition.callbacks().getOrDefault(event, LuaValue.NIL);
   }

   public static Map<String, LuaValue> getBehaviors(String domain, String key) {
      BehaviorSet definition = definition(domain, key);
      return definition == null ? Map.of() : definition.callbacks();
   }

   public static Snapshot snapshotBehaviors(String domain, String key) {
      BehaviorSet definition = definition(domain, key);
      return definition == null ? new Snapshot(Map.of(), null)
         : new Snapshot(definition.callbacks(), definition.context());
   }

   public static Varargs invokeBehavior(String domain, String key, String event, Varargs args) {
      BehaviorSet definition = definition(domain, key);
      if (definition == null) return LuaValue.NONE;
      return invokeCallback(definition.context(), definition.callbacks().get(event), args);
   }

   public static Varargs invokeCallback(String domain, String key, LuaValue callback, Varargs args) {
      BehaviorSet definition = definition(domain, key);
      return invokeCallback(definition == null ? null : definition.context(), callback, args);
   }

   /** Invokes a callback with the owner context captured alongside its behavior definition. */
   public static Varargs invokeCallback(Snapshot owner, LuaValue callback, Varargs args) {
      return invokeCallback(owner == null ? null : owner.context(), callback, args);
   }

   private static Varargs invokeCallback(MLuaContext context, LuaValue callback, Varargs args) {
      if (callback == null || callback.isnil() || !callback.isfunction()) return LuaValue.NONE;
      Varargs invocationArgs = args == null ? LuaValue.NONE : args;
      if (context == null) return callback.invoke(invocationArgs);
      synchronized (context.executionLock()) {
         MLuaLuaBindings.bindContext(context.globals(), context);
         return callback.invoke(invocationArgs);
      }
   }

   private static BehaviorSet definition(String domain, String key) {
      ConcurrentMap<String, BehaviorSet> definitions = SCRIPTED_BEHAVIORS.get(domain);
      return definitions == null ? null : definitions.get(key);
   }

   /** Restores a previous behavior set only while the definition still matches the failed registration. */
   public static boolean restoreBehaviors(String domain, String key, Map<String, LuaValue> expected,
                                          Map<String, LuaValue> previous) {
      BehaviorSet current = definition(domain, key);
      MLuaContext expectedContext = current == null ? null : current.context();
      return restoreBehaviors(domain, key, expected,
         previous == null ? new Snapshot(Map.of(), null) : new Snapshot(previous, null), expectedContext);
   }

   public static boolean restoreBehaviors(String domain, String key, Map<String, LuaValue> expected,
                                          Snapshot previous, MLuaContext expectedContext) {
      if (domain == null || key == null) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      SCRIPTED_BEHAVIORS.compute(domain, (ignored, definitions) -> {
         ConcurrentMap<String, BehaviorSet> values = definitions == null
            ? new ConcurrentHashMap<>() : definitions;
         values.compute(key, (ignoredKey, current) -> {
            Map<String, LuaValue> actual = current == null ? Map.of() : current.callbacks();
            if (!sameBehaviors(actual, expected == null ? Map.of() : expected)
                || current != null && current.context() != expectedContext) return current;
            restored.set(true);
            return previous == null || previous.callbacks().isEmpty() ? null
               : new BehaviorSet(Map.copyOf(previous.callbacks()), previous.context());
         });
         return values.isEmpty() ? null : values;
      });
      return restored.get();
   }

   private static boolean sameBehaviors(Map<String, LuaValue> left, Map<String, LuaValue> right) {
      if (left.size() != right.size()) return false;
      for (Map.Entry<String, LuaValue> entry : left.entrySet()) {
         if (right.get(entry.getKey()) != entry.getValue()) return false;
      }
      return true;
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

   /** Restores a previous class mapping only while the mapping still belongs to the failed registration. */
   public static boolean restoreContentClass(String domain, String key, String expected, String previous) {
      if (domain == null || key == null) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      CONTENT_CLASSES.compute(domain, (ignored, definitions) -> {
         ConcurrentMap<String, String> values = definitions == null
            ? new ConcurrentHashMap<>() : definitions;
         values.compute(key, (ignoredKey, current) -> {
            if (!java.util.Objects.equals(current, expected)) return current;
            restored.set(true);
            return previous;
         });
         return values.isEmpty() ? null : values;
      });
      return restored.get();
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

   public static ContainerDefinition registerBlockContainer(String key, ContainerDefinition definition) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException("Scripted container block key must be non-empty");
      ContainerDefinition registered = validateContainerDefinition(definition);
      BLOCK_CONTAINERS.put(key, registered);
      return registered;
   }

   public static void registerContainerType(String key, int rows, String title) {
      registerContainerType(key, new ContainerDefinition(rows, title));
   }

   public static ContainerDefinition registerContainerType(String key, ContainerDefinition definition) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException("Scripted container type key must be non-empty");
      ContainerDefinition registered = validateContainerDefinition(definition);
      if (CONTAINER_TYPES.putIfAbsent(key, registered) != null) {
         throw new IllegalStateException("Scripted container type is already registered: " + key);
      }
      return registered;
   }

   public static ContainerDefinition getContainerType(String key) { return CONTAINER_TYPES.get(key); }

   public static Map<String, ContainerDefinition> getContainerTypes() {
      return Map.copyOf(CONTAINER_TYPES);
   }

   public static boolean unregisterContainerType(String key, ContainerDefinition expected) {
      return removeContainer(CONTAINER_TYPES, key, expected);
   }

   public static boolean unregisterBlockContainer(String key, ContainerDefinition expected) {
      return removeContainer(BLOCK_CONTAINERS, key, expected);
   }

   /** Restores a block container only while the current value is still the registration being rolled back. */
   public static boolean restoreBlockContainer(String key, ContainerDefinition expected,
                                               ContainerDefinition previous) {
      if (key == null || key.isBlank()) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      BLOCK_CONTAINERS.compute(key, (ignored, current) -> {
         if (current != expected) return current;
         restored.set(true);
         return previous;
      });
      return restored.get();
   }

   private static boolean removeContainer(ConcurrentMap<String, ContainerDefinition> containers,
                                          String key, ContainerDefinition expected) {
      if (key == null || expected == null) return false;
      java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
      containers.computeIfPresent(key, (ignored, current) -> {
         if (current != expected) return current;
         removed.set(true);
         return null;
      });
      return removed.get();
   }

   public static ContainerDefinition validateContainerDefinition(ContainerDefinition definition) {
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

    public static Integer getRegisteredEntityAIInterval(String key) {
       return ENTITY_AI_INTERVAL.get(key);
    }

    public static boolean restoreEntityAIInterval(String key, int expected, Integer previous) {
       if (key == null) return false;
       java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
       ENTITY_AI_INTERVAL.compute(key, (ignored, current) -> {
          if (current == null || current != expected) return current;
          restored.set(true);
          return previous;
       });
       return restored.get();
    }

}
