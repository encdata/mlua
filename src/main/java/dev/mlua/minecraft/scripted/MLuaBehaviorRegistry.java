package dev.mlua.minecraft.scripted;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.engine.MLuaLuaBindings;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MLuaBehaviorRegistry {
   /** Caps interpreted Lua work performed synchronously by one scripted gameplay callback. */
   public static final long MAX_LUA_INSTRUCTIONS_PER_CALLBACK = MLuaCallbackBudget.MAX_INSTRUCTIONS;
   public static final int MAX_BEHAVIOR_DEFINITIONS = 8_192;
   public static final int MAX_BEHAVIOR_DEFINITIONS_PER_ADDON = 1_024;
   public static final int MAX_CALLBACKS_PER_DEFINITION = 128;
   public static final int MAX_CONTENT_CLASS_MAPPINGS = 8_192;
   public static final int MAX_CONTENT_CLASS_MAPPINGS_PER_ADDON = 1_024;
   public static final int MAX_BEHAVIOR_NAME_LENGTH = 128;
   public static final int MAX_BEHAVIOR_KEY_LENGTH = 256;
   public static final int MAX_BLOCK_CONTAINERS = 8_192;
   public static final int MAX_CONTAINER_TYPES = 8_192;
   public static final int MAX_CONTAINERS_PER_ADDON = 1_024;
   public static final int MAX_ALLOWED_ITEMS_PER_RULE = 1_024;
   public static final int MAX_ALLOWED_ITEM_REFERENCES_PER_CONTAINER = 4_096;
   public static final int MAX_CONTAINER_TITLE_LENGTH = 256;
   public static final int MAX_ENTITY_AI_CONFIGS = 8_192;
   public static final int MAX_ENTITY_AI_STATES_PER_DEFINITION = MLuaEntityAIDefinition.MAX_STATES;
   public static final int MAX_ENTITY_AI_TABLE_ENTRIES = MLuaEntityAIDefinition.MAX_TABLE_ENTRIES;
   public static final int MAX_ENTITY_AI_TABLE_DEPTH = MLuaEntityAIDefinition.MAX_TABLE_DEPTH;
   public record ScriptLimits(int activeBehaviors, int activeBehaviorsForAddon,
                              int activeClassMappings, int activeClassMappingsForAddon,
                              int activeBlockContainers, int blockContainersForAddon,
                              int activeContainerTypes, int containerTypesForAddon,
                              int activeEntityAIConfigs, int entityAIConfigsForAddon) { }
   static final class BehaviorSet {
      private final Map<String, LuaValue> callbacks;
      private final MLuaContext context;
      private final Snapshot snapshot;

      BehaviorSet(Map<String, LuaValue> callbacks, MLuaContext context,
                          String domain, String key) {
         this.callbacks = Map.copyOf(callbacks);
         this.context = context;
         this.snapshot = new Snapshot(this.callbacks, context, domain, key, this);
      }

      Map<String, LuaValue> callbacks() { return this.callbacks; }
      MLuaContext context() { return this.context; }
   }
   public static final class Snapshot {
      private final Map<String, LuaValue> callbacks;
      private final MLuaContext context;
      private final String domain;
      private final String key;
      private final BehaviorSet definition;

      /** Creates an unversioned snapshot for rollback and compatibility callers. */
      public Snapshot(Map<String, LuaValue> callbacks, MLuaContext context) {
         this(callbacks == null ? Map.of() : Map.copyOf(callbacks), context, null, null, null);
      }

      private Snapshot(Map<String, LuaValue> callbacks, MLuaContext context,
                       String domain, String key, BehaviorSet definition) {
         // BehaviorSet callbacks are immutable snapshots created when the behavior is
         // registered or updated. Keep that map by reference on this hot path: scripted
         // item/entity hooks can request a snapshot every tick or interaction.
         this.callbacks = callbacks == null ? Map.of() : callbacks;
         this.context = context;
         this.domain = domain;
         this.key = key;
         this.definition = definition;
      }

      public Map<String, LuaValue> callbacks() { return callbacks; }
      public MLuaContext context() { return context; }
      /** Opaque definition token for runtime caches that must reset when a behavior is replaced. */
      public Object identity() { return definition; }

      @Override public boolean equals(Object other) {
         return other instanceof Snapshot snapshot
            && callbacks.equals(snapshot.callbacks)
            && java.util.Objects.equals(context, snapshot.context);
      }

      @Override public int hashCode() { return java.util.Objects.hash(callbacks, context); }

      @Override public String toString() {
         return "Snapshot[callbacks=" + callbacks + ", context=" + context + "]";
      }
   }
    private static final ConcurrentMap<String, Integer> ENTITY_AI_INTERVAL = new ConcurrentHashMap();
   static final Object REGISTRATION_LOCK = new Object();

   private MLuaBehaviorRegistry() {
   }

   /** Deep-copies a validated AI state definition while retaining its Lua callback functions. */
   public static LuaTable snapshotEntityAI(LuaTable source) {
      return MLuaEntityAIDefinition.snapshot(source);
   }

   /** Validates the same raw table structure that snapshotEntityAI stores for runtime use. */
   public static void validateEntityAI(LuaValue ai) {
      MLuaEntityAIDefinition.validate(ai);
   }

   public record ContentClassDefinition(String className, MLuaContext context) { }

   public static void clearItemCallbacks(String key) {
      clearBehaviors("item", key);
   }

   public static void clearBlockCallbacks(String key) {
      synchronized (REGISTRATION_LOCK) {
         clearBehaviors("block", key);
         clearBehaviors("block_entity", key);
         MLuaScriptedContainerRegistry.clearBlock(key);
      }
   }

   public static void registerBehavior(String domain, String key, String event, LuaValue behavior) {
      MLuaScriptedBehaviorDefinitions.registerBehavior(domain, key, event, behavior);
   }

   /** Replaces a content definition's complete behavior set in one visible update. */
   public static Map<String, LuaValue> registerBehaviors(String domain, String key, Map<String, LuaValue> behaviors) {
      return registerBehaviors(domain, key, behaviors, null);
   }

   public static Map<String, LuaValue> registerBehaviors(String domain, String key, Map<String, LuaValue> behaviors,
                                                         MLuaContext context) {
      return MLuaScriptedBehaviorDefinitions.registerBehaviors(domain, key, behaviors, context);
   }

   public static LuaValue getBehavior(String domain, String key, String event) {
      BehaviorSet definition = MLuaScriptedBehaviorDefinitions.definition(domain, key);
      return definition == null ? LuaValue.NIL : definition.callbacks().getOrDefault(event, LuaValue.NIL);
   }

   public static Map<String, LuaValue> getBehaviors(String domain, String key) {
      BehaviorSet definition = MLuaScriptedBehaviorDefinitions.definition(domain, key);
      return definition == null ? Map.of() : definition.callbacks();
   }

   public static Snapshot snapshotBehaviors(String domain, String key) {
      BehaviorSet definition = MLuaScriptedBehaviorDefinitions.definition(domain, key);
      return definition == null ? new Snapshot(Map.of(), null, domain, key, null) : definition.snapshot;
   }

   public static Varargs invokeBehavior(String domain, String key, String event, Varargs args) {
      BehaviorSet definition = MLuaScriptedBehaviorDefinitions.definition(domain, key);
      if (definition == null) return LuaValue.NONE;
      return invokeCallback(definition.context(), definition.callbacks().get(event), args);
   }

   public static Varargs invokeCallback(String domain, String key, String event, LuaValue callback, Varargs args) {
      BehaviorSet definition = MLuaScriptedBehaviorDefinitions.definition(domain, key);
      // Callback lookup and invocation are separate operations in several Minecraft hooks. If a
      // script reload replaced the behavior in between, never run the old closure under the new
      // script's context (or keep invoking a callback that has already been removed).
      if (definition == null || callback == null || event == null
          || definition.callbacks().get(event) != callback) {
         return LuaValue.NONE;
      }
      return invokeCallback(definition.context(), callback, args);
   }

   /** Invokes a callback nested inside a registered definition only while that parent remains current. */
   public static Varargs invokeNestedCallback(String domain, String key, String parentEvent,
                                              LuaValue expectedParent, LuaValue callback, Varargs args) {
      BehaviorSet definition = MLuaScriptedBehaviorDefinitions.definition(domain, key);
      if (definition == null || parentEvent == null || expectedParent == null
          || definition.callbacks().get(parentEvent) != expectedParent) {
         return LuaValue.NONE;
      }
      return invokeCallback(definition.context(), callback, args);
   }

   /** Invokes a callback with the owner context captured alongside its behavior definition. */
   public static Varargs invokeCallback(Snapshot owner, LuaValue callback, Varargs args) {
      if (owner == null || callback == null || callback.isnil() || !callback.isfunction()) {
         return LuaValue.NONE;
      }
      if (owner.domain != null && owner.key != null) {
         // Entity and block-entity hooks may retain a snapshot while a script reloads.
         // Only invoke it while the exact definition that produced it is still registered.
         if (owner.definition == null
               || MLuaScriptedBehaviorDefinitions.definition(owner.domain, owner.key) != owner.definition) {
            return LuaValue.NONE;
         }
      }
      return invokeCallback(owner == null ? null : owner.context(), callback, args);
   }

   /** Invokes a scripted content callback with per-level failure isolation. */
   public static Varargs invokeGameplayCallback(Level level,
         BlockPos position, String domain, String key, String event,
         Snapshot owner, LuaValue callback, Varargs args) {
      return MLuaBehaviorCallbackFailureTracker.invoke(level, position, domain, key, event,
         owner, callback, () -> invokeCallback(owner, callback, args));
   }

   /** Invokes an independently owned scripted behavior with the same safety cap as registered behaviors. */
   public static Varargs invokeCallback(MLuaContext context, LuaValue callback, Varargs args) {
      if (callback == null || callback.isnil() || !callback.isfunction()) return LuaValue.NONE;
      Varargs invocationArgs = args == null ? LuaValue.NONE : args;
      return MLuaLuaBindings.withContext(context,
         () -> MLuaCallbackBudget.run(context, () -> callback.invoke(invocationArgs)));
   }

   /** Restores a previous behavior set only while the definition still matches the failed registration. */
   public static boolean restoreBehaviors(String domain, String key, Map<String, LuaValue> expected,
                                          Map<String, LuaValue> previous) {
      BehaviorSet current = MLuaScriptedBehaviorDefinitions.definition(domain, key);
      MLuaContext expectedContext = current == null ? null : current.context();
      return restoreBehaviors(domain, key, expected,
         previous == null ? new Snapshot(Map.of(), null) : new Snapshot(previous, null), expectedContext);
   }

   public static boolean restoreBehaviors(String domain, String key, Map<String, LuaValue> expected,
                                          Snapshot previous, MLuaContext expectedContext) {
      return MLuaScriptedBehaviorDefinitions.restore(domain, key, expected, previous, expectedContext);
   }

   /** Compatibility name used by scripted content dispatchers. */
   public static LuaValue getCallback(String domain, String key, String event) {
      return getBehavior(domain, key, event);
   }

   public static void registerContentClass(String domain, String key, String className) {
      registerContentClass(domain, key, className, null);
   }

   public static void registerContentClass(String domain, String key, String className, MLuaContext context) {
      MLuaScriptedContentClassRegistry.register(domain, key, className, context);
   }

   public static String getContentClass(String domain, String key) {
      ContentClassDefinition definition = getContentClassDefinition(domain, key);
      return definition == null ? null : definition.className();
   }

   public static MLuaContext getContentClassContext(String domain, String key) {
      ContentClassDefinition definition = getContentClassDefinition(domain, key);
      return definition == null ? null : definition.context();
   }

   public static ContentClassDefinition getContentClassDefinition(String domain, String key) {
      return MLuaScriptedContentClassRegistry.get(domain, key);
   }

   /** Restores a previous class mapping only while the mapping still belongs to the failed registration. */
   public static boolean restoreContentClass(String domain, String key, String expected, String previous) {
      if (domain == null || key == null) return false;
      ContentClassDefinition expectedDefinition = expected == null ? null : new ContentClassDefinition(expected, null);
      ContentClassDefinition previousDefinition = previous == null ? null : new ContentClassDefinition(previous, null);
      return restoreContentClass(domain, key, expectedDefinition, previousDefinition);
   }

   /** Restores the full class/context binding only while the current registration still owns it. */
   public static boolean restoreContentClass(String domain, String key, ContentClassDefinition expected,
                                             ContentClassDefinition previous) {
      return MLuaScriptedContentClassRegistry.restore(domain, key, expected, previous);
   }

   private static void clearBehaviors(String domain, String key) {
      MLuaScriptedBehaviorDefinitions.clear(domain, key);
   }

   public static void registerBlockContainer(String key, int rows, String title) {
      registerBlockContainer(key, new ContainerDefinition(rows, title));
   }

   public static ContainerDefinition registerBlockContainer(String key, ContainerDefinition definition) {
      return MLuaScriptedContainerRegistry.registerBlock(key, definition);
   }

   public static void registerContainerType(String key, int rows, String title) {
      registerContainerType(key, new ContainerDefinition(rows, title));
   }

   public static ContainerDefinition registerContainerType(String key, ContainerDefinition definition) {
      return MLuaScriptedContainerRegistry.registerType(key, definition);
   }

   public static ContainerDefinition getContainerType(String key) { return MLuaScriptedContainerRegistry.getType(key); }

   public static Map<String, ContainerDefinition> getContainerTypes() {
      return MLuaScriptedContainerRegistry.getTypes();
   }

   public static ScriptLimits scriptLimits(String ownerModId) {
      String owner = ownerModId == null ? "" : ownerModId;
      synchronized (REGISTRATION_LOCK) {
         int behaviorCount = MLuaScriptedBehaviorDefinitions.count();
         int ownedBehaviorCount = MLuaScriptedBehaviorDefinitions.countForOwner(owner);
         int classCount = MLuaScriptedContentClassRegistry.count();
         int ownedClassCount = MLuaScriptedContentClassRegistry.countForOwner(owner);
         int blockContainers = MLuaScriptedContainerRegistry.blockCount();
         int ownedBlockContainers = MLuaScriptedContainerRegistry.blockCountForOwner(owner);
         int containerTypes = MLuaScriptedContainerRegistry.typeCount();
         int ownedContainerTypes = MLuaScriptedContainerRegistry.typeCountForOwner(owner);
         int aiConfigs = ENTITY_AI_INTERVAL.size();
         int ownedAIConfigs = countOwnerKeys(ENTITY_AI_INTERVAL, owner);
         return new ScriptLimits(behaviorCount, ownedBehaviorCount, classCount, ownedClassCount,
            blockContainers, ownedBlockContainers, containerTypes, ownedContainerTypes, aiConfigs, ownedAIConfigs);
      }
   }

   public static boolean unregisterContainerType(String key, ContainerDefinition expected) {
      return MLuaScriptedContainerRegistry.unregisterType(key, expected);
   }

   public static boolean unregisterBlockContainer(String key, ContainerDefinition expected) {
      return MLuaScriptedContainerRegistry.unregisterBlock(key, expected);
   }

   /** Restores a block container only while the current value is still the registration being rolled back. */
   public static boolean restoreBlockContainer(String key, ContainerDefinition expected,
                                               ContainerDefinition previous) {
      return MLuaScriptedContainerRegistry.restoreBlock(key, expected, previous);
   }

   public static ContainerDefinition validateContainerDefinition(ContainerDefinition definition) {
      return MLuaScriptedContainerRegistry.validate(definition);
   }

   public static ContainerDefinition getBlockContainer(String key) { return MLuaScriptedContainerRegistry.getBlock(key); }

   public record ContainerDefinition(int rows, String title, java.util.Set<String> allowedItems,
                                     Map<Integer, java.util.Set<String>> slotAllowedItems) {
      public ContainerDefinition(int rows, String title) { this(rows, title, java.util.Set.of(), Map.of()); }
      public ContainerDefinition(int rows, String title, java.util.Set<String> allowedItems) {
         this(rows, title, allowedItems, Map.of());
      }
      public ContainerDefinition {
         allowedItems = allowedItems == null ? java.util.Set.of() : java.util.Set.copyOf(allowedItems);
         java.util.TreeMap<Integer, java.util.Set<String>> slots = new java.util.TreeMap<>();
         if (slotAllowedItems != null) slotAllowedItems.forEach((slot, items) -> {
            java.util.Set<String> copy = items == null ? java.util.Set.of() : java.util.Set.copyOf(items);
            slots.put(slot, copy);
         });
         slotAllowedItems = java.util.Collections.unmodifiableMap(slots);
      }
      public boolean allows(ItemStack stack) {
         if (stack == null || stack.isEmpty()) return true;
         String itemId = registeredItemId(stack);
         for (int slot = 0; slot < rows * 9; slot++) {
            if (allows(slot, itemId)) return true;
         }
         return false;
      }

      private boolean allowsByContainerRule(String itemId) {
         return allowedItems.isEmpty() || itemId != null && allowedItems.contains(itemId);
      }

      public boolean allows(int zeroBasedSlot, ItemStack stack) {
         if (zeroBasedSlot < 0 || zeroBasedSlot >= rows * 9) return false;
         if (stack == null || stack.isEmpty()) return true;
         return allows(zeroBasedSlot, registeredItemId(stack));
      }

      private boolean allows(int zeroBasedSlot, String itemId) {
         if (zeroBasedSlot < 0 || zeroBasedSlot >= rows * 9) return false;
         int oneBasedSlot = zeroBasedSlot + 1;
         if (!slotAllowedItems.containsKey(oneBasedSlot)) return allowsByContainerRule(itemId);
         java.util.Set<String> slotItems = slotAllowedItems.get(oneBasedSlot);
         return slotItems.isEmpty() || itemId != null && slotItems.contains(itemId);
      }

      private static String registeredItemId(ItemStack stack) {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         return id == null ? null : id.toString();
      }
   }

   public static void clearEntityCallbacks(String key) {
      synchronized (REGISTRATION_LOCK) {
         clearBehaviors("entity", key);
         ENTITY_AI_INTERVAL.remove(key);
      }
   }

   public static void clearEffectCallbacks(String key) {
      clearBehaviors("effect", key);
   }

   /** Removes scripted definitions contributed by one addon during reload or unload. */
   public static void clearOwner(String owner) {
      if (owner == null || owner.isBlank()) return;
      String prefix = owner.trim() + ":";
      synchronized (REGISTRATION_LOCK) {
         MLuaScriptedBehaviorDefinitions.clearOwnerPrefix(prefix);
         MLuaScriptedContentClassRegistry.clearOwnerPrefix(prefix);
         MLuaScriptedContainerRegistry.clearOwnerPrefix(prefix);
         ENTITY_AI_INTERVAL.keySet().removeIf(key -> key.startsWith(prefix));
      }
   }

    public static void registerEntityAIInterval(String key, int interval) {
       synchronized (REGISTRATION_LOCK) {
          validateBehaviorKey(key);
          ensureKeyCapacity(ENTITY_AI_INTERVAL, key, MAX_ENTITY_AI_CONFIGS);
          ENTITY_AI_INTERVAL.put(key, Math.max(1, Math.min(120, interval)));
       }
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
       synchronized (REGISTRATION_LOCK) {
          ENTITY_AI_INTERVAL.compute(key, (ignored, current) -> {
             if (current == null || current != expected) return current;
             restored.set(true);
             return previous;
          });
       }
       return restored.get();
    }

   static void validateBehaviorKey(String key) {
      if (key == null || key.isBlank()) {
         throw new IllegalArgumentException("Scripted content identifier must be non-empty");
      }
      if (key.length() > MAX_BEHAVIOR_KEY_LENGTH) {
         throw new IllegalArgumentException("Scripted content identifiers cannot exceed " + MAX_BEHAVIOR_KEY_LENGTH + " characters");
      }
   }

   private static int countOwnerKeys(Map<String, ?> values, String owner) {
      String prefix = owner + ":";
      return (int) values.keySet().stream().filter(key -> key.startsWith(prefix)).count();
   }

   private static void ensureKeyCapacity(ConcurrentMap<String, ?> values, String key, int maxTotal) {
      if (values.containsKey(key)) return;
      if (values.size() >= maxTotal) {
         throw new IllegalStateException("Scripted definition limit reached (" + maxTotal + " total)");
      }
      int separator = key.indexOf(':');
      String ownerPrefix = separator > 0 ? key.substring(0, separator + 1) : "";
      if (!ownerPrefix.isEmpty() && values.keySet().stream().filter(id -> id.startsWith(ownerPrefix)).count()
          >= MAX_BEHAVIOR_DEFINITIONS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted definition limit reached (" + MAX_BEHAVIOR_DEFINITIONS_PER_ADDON + ")");
      }
   }

}
