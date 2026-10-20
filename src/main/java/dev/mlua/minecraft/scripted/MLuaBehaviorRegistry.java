package dev.mlua.minecraft.scripted;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaLogger;
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
   public static final int MAX_ENTITY_AI_STATES_PER_DEFINITION = 256;
   public static final int MAX_ENTITY_AI_TABLE_ENTRIES = 16_384;
   public static final int MAX_ENTITY_AI_TABLE_DEPTH = 64;
   public record ScriptLimits(int activeBehaviors, int activeBehaviorsForAddon,
                              int activeClassMappings, int activeClassMappingsForAddon,
                              int activeBlockContainers, int blockContainersForAddon,
                              int activeContainerTypes, int containerTypesForAddon,
                              int activeEntityAIConfigs, int entityAIConfigsForAddon) { }
   private record BehaviorSet(Map<String, LuaValue> callbacks, MLuaContext context) { }
   public static final class Snapshot {
      private final Map<String, LuaValue> callbacks;
      private final MLuaContext context;
      private final String domain;
      private final String key;
      private final BehaviorSet definition;

      /** Creates an unversioned snapshot for rollback and compatibility callers. */
      public Snapshot(Map<String, LuaValue> callbacks, MLuaContext context) {
         this(callbacks, context, null, null, null);
      }

      private Snapshot(Map<String, LuaValue> callbacks, MLuaContext context,
                       String domain, String key, BehaviorSet definition) {
         this.callbacks = callbacks == null ? Map.of() : Map.copyOf(callbacks);
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
   private static final ConcurrentMap<String, ConcurrentMap<String, BehaviorSet>> SCRIPTED_BEHAVIORS = new ConcurrentHashMap<>();
   private static final ConcurrentMap<String, ConcurrentMap<String, ContentClassDefinition>> CONTENT_CLASSES = new ConcurrentHashMap<>();
   private static final ConcurrentMap<String, ContainerDefinition> BLOCK_CONTAINERS = new ConcurrentHashMap<>();
   private static final ConcurrentMap<String, ContainerDefinition> CONTAINER_TYPES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> ENTITY_AI_INTERVAL = new ConcurrentHashMap();
   private static final Object REGISTRATION_LOCK = new Object();

   private MLuaBehaviorRegistry() {
   }

   /** Deep-copies a validated AI state definition while retaining its Lua callback functions. */
   public static LuaTable snapshotEntityAI(LuaTable source) {
      if (source == null) throw new IllegalArgumentException("Entity AI definition cannot be null");
      validateEntityAI(source);
      return snapshotEntityAITable(source, new java.util.IdentityHashMap<>(),
         new java.util.IdentityHashMap<>(), new int[]{0}, 0);
   }

   /** Validates the same raw table structure that snapshotEntityAI stores for runtime use. */
   public static void validateEntityAI(LuaValue ai) {
      if (ai == null || ai.isnil() || ai.isfunction()) return;
      if (!ai.istable()) throw new LuaError("[MLua] Entity AI must be a function or a state table.");
      LuaTable definition = ai.checktable();
      validateAIDataTable(definition, new java.util.IdentityHashMap<>(), new int[]{0}, 0);
      LuaValue initial = definition.rawget("initial");
      LuaValue statesValue = definition.rawget("states");
      if (initial.type() != LuaValue.TSTRING || initial.tojstring().isBlank() || !statesValue.istable()) {
         throw new LuaError("[MLua] State-based AI requires raw 'initial' and 'states' table entries.");
      }
      if (initial.tojstring().length() > MAX_BEHAVIOR_NAME_LENGTH) {
         throw new LuaError("[MLua] Entity AI initial state name exceeds the maximum length of "
            + MAX_BEHAVIOR_NAME_LENGTH + " characters.");
      }
      LuaTable states = statesValue.checktable();
      int stateCount = 0;
      LuaValue stateKey = LuaValue.NIL;
      while (true) {
         Varargs entry = states.next(stateKey);
         stateKey = entry.arg1();
         if (stateKey.isnil()) break;
         stateCount++;
         if (stateCount > MAX_ENTITY_AI_STATES_PER_DEFINITION) {
            throw new LuaError("[MLua] Entity AI exceeds the maximum of "
               + MAX_ENTITY_AI_STATES_PER_DEFINITION + " states per definition.");
         }
      }
      if (states.rawget(initial).isnil()) {
         throw new LuaError("[MLua] State-based AI initial state '" + initial.tojstring() + "' is not defined.");
      }
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = states.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         LuaValue state = entry.arg(2);
         if (key.type() != LuaValue.TSTRING || key.tojstring().isBlank()
               || key.tojstring().length() > MAX_BEHAVIOR_NAME_LENGTH
               || !state.isfunction() && !state.istable()) {
            throw new LuaError("[MLua] Each AI state must have a name and be a function or state table.");
         }
         if (state.istable()) {
            LuaTable callbacks = state.checktable();
            for (String callbackName : java.util.List.of("onEnter", "update", "onExit")) {
               LuaValue callback = callbacks.rawget(callbackName);
               if (!callback.isnil() && !callback.isfunction()) {
                  throw new LuaError("[MLua] AI state '" + key.tojstring() + "' callback '" + callbackName
                     + "' must be a raw function entry.");
               }
            }
         }
      }
   }

   private static void validateAIDataTable(LuaTable table,
         java.util.IdentityHashMap<LuaTable, Boolean> active, int[] entries, int depth) {
      if (depth >= MAX_ENTITY_AI_TABLE_DEPTH) {
         throw new LuaError("[MLua] Entity AI definition cannot be nested more than "
            + MAX_ENTITY_AI_TABLE_DEPTH + " tables deep.");
      }
      if (table.getmetatable() != null) {
         throw new LuaError("[MLua] Entity AI definition tables cannot have metatables; AI definitions are snapshotted as data.");
      }
      if (active.put(table, Boolean.TRUE) != null) {
         throw new LuaError("[MLua] Entity AI definition cannot contain cyclic tables.");
      }
      try {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) return;
            if (++entries[0] > MAX_ENTITY_AI_TABLE_ENTRIES) {
               throw new LuaError("[MLua] Entity AI definition cannot contain more than "
                  + MAX_ENTITY_AI_TABLE_ENTRIES + " table entries.");
            }
            if (key.istable()) validateAIDataTable(key.checktable(), active, entries, depth + 1);
            LuaValue value = entry.arg(2);
            if (value.istable()) validateAIDataTable(value.checktable(), active, entries, depth + 1);
         }
      } finally {
         active.remove(table);
      }
   }

   private static LuaTable snapshotEntityAITable(LuaTable source,
         java.util.IdentityHashMap<LuaTable, LuaTable> copies,
         java.util.IdentityHashMap<LuaTable, Boolean> active, int[] entries, int depth) {
      if (depth >= MAX_ENTITY_AI_TABLE_DEPTH) {
         throw new LuaError("[MLua] Entity AI definition cannot be nested more than "
            + MAX_ENTITY_AI_TABLE_DEPTH + " tables deep.");
      }
      if (active.containsKey(source)) {
         throw new LuaError("[MLua] Entity AI definition cannot contain cyclic tables.");
      }
      LuaTable existing = copies.get(source);
      if (existing != null) return existing;

      LuaTable copy = new LuaTable();
      copies.put(source, copy);
      active.put(source, Boolean.TRUE);
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = source.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (++entries[0] > MAX_ENTITY_AI_TABLE_ENTRIES) {
            throw new LuaError("[MLua] Entity AI definition cannot contain more than "
               + MAX_ENTITY_AI_TABLE_ENTRIES + " table entries.");
         }
         LuaValue value = entry.arg(2);
         LuaValue copiedKey = key.istable()
            ? snapshotEntityAITable(key.checktable(), copies, active, entries, depth + 1) : key;
         LuaValue copiedValue = value.istable()
            ? snapshotEntityAITable(value.checktable(), copies, active, entries, depth + 1) : value;
         copy.rawset(copiedKey, copiedValue);
      }
      active.remove(source);
      return copy;
   }

   public record ContentClassDefinition(String className, MLuaContext context) { }

   public static void clearItemCallbacks(String key) {
      clearBehaviors("item", key);
   }

   public static void clearBlockCallbacks(String key) {
      synchronized (REGISTRATION_LOCK) {
         clearBehaviors("block", key);
         clearBehaviors("block_entity", key);
         BLOCK_CONTAINERS.remove(key);
      }
   }

   public static void registerBehavior(String domain, String key, String event, LuaValue behavior) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank() || event == null || event.isBlank()) {
         throw new IllegalArgumentException("Scripted behavior domain, key, and member must be non-empty");
      }
      validateBehaviorName(event);
      validateBehaviorKey(key);
      if (behavior != null && !behavior.isnil() && !behavior.isfunction()) {
         throw new IllegalArgumentException("Scripted behavior callback must be a Lua function or nil");
      }
      if (behavior == null || behavior.isnil()) {
         synchronized (REGISTRATION_LOCK) {
            SCRIPTED_BEHAVIORS.computeIfPresent(domain, (ignoredDomain, definitions) -> {
               definitions.computeIfPresent(key, (ignored, current) -> {
                  Map<String, LuaValue> updated = new java.util.HashMap<>(current.callbacks());
                  updated.remove(event);
                  return updated.isEmpty() ? null : new BehaviorSet(Map.copyOf(updated), current.context());
               });
               return definitions.isEmpty() ? null : definitions;
            });
         }
         return;
      }
      synchronized (REGISTRATION_LOCK) {
         ConcurrentMap<String, BehaviorSet> definitions = SCRIPTED_BEHAVIORS.get(domain);
         BehaviorSet current = definitions == null ? null : definitions.get(key);
         Map<String, LuaValue> updated = current == null ? new java.util.HashMap<>() : new java.util.HashMap<>(current.callbacks());
         updated.put(event, behavior);
         ensureBehaviorCapacity(domain, key, current == null ? null : current.context());
         if (updated.size() > MAX_CALLBACKS_PER_DEFINITION) {
            throw new IllegalStateException("Scripted behavior callback limit reached (" + MAX_CALLBACKS_PER_DEFINITION + " per definition)");
         }
         SCRIPTED_BEHAVIORS.compute(domain, (ignoredDomain, currentDefinitions) -> {
            ConcurrentMap<String, BehaviorSet> result = currentDefinitions == null
               ? new ConcurrentHashMap<>() : currentDefinitions;
            result.put(key, new BehaviorSet(Map.copyOf(updated), current == null ? null : current.context()));
            return result;
         });
      }
      MLuaLogger.LOGGER.debug("Registered {} behavior '{}' for {}", domain, event, key);
   }

   /** Replaces a content definition's complete behavior set in one visible update. */
   public static Map<String, LuaValue> registerBehaviors(String domain, String key, Map<String, LuaValue> behaviors) {
      return registerBehaviors(domain, key, behaviors, null);
   }

   public static Map<String, LuaValue> registerBehaviors(String domain, String key, Map<String, LuaValue> behaviors,
                                                         MLuaContext context) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank()) {
         throw new IllegalArgumentException("Scripted behavior domain and key must be non-empty");
      }
      Map<String, LuaValue> definition = new java.util.HashMap<>();
      if (behaviors != null) {
         behaviors.forEach((event, behavior) -> {
            if (behavior == null || behavior.isnil()) return;
            if (event == null || event.isBlank()) throw new IllegalArgumentException("Scripted behavior name must be non-empty");
            validateBehaviorName(event);
            if ("entity".equals(domain) && "AI".equals(event) && behavior.istable()) {
               // AI is the one content behavior that may be a structured definition. Copy it
               // at the registry boundary so later mutation of the registering script's table
               // cannot change live entities without a new registration/reload.
               definition.put(event, snapshotEntityAI(behavior.checktable()));
            } else if (behavior.isfunction()) {
               definition.put(event, behavior);
            } else {
               throw new IllegalArgumentException("Scripted behavior callback '" + event
                  + "' must be a Lua function or nil"
                  + ("entity".equals(domain) && "AI".equals(event) ? ", or an AI state definition table" : ""));
            }
         });
      }
      validateBehaviorKey(key);
      if (definition.size() > MAX_CALLBACKS_PER_DEFINITION) {
         throw new IllegalArgumentException("Scripted behavior callback limit reached (" + MAX_CALLBACKS_PER_DEFINITION + " per definition)");
      }
      synchronized (REGISTRATION_LOCK) {
         BehaviorSet current = definition(domain, key);
         if (!definition.isEmpty()) ensureBehaviorCapacity(domain, key, context);
         SCRIPTED_BEHAVIORS.compute(domain, (ignoredDomain, definitions) -> {
            if (definitions == null && definition.isEmpty()) return null;
            ConcurrentMap<String, BehaviorSet> result = definitions == null
               ? new ConcurrentHashMap<>() : definitions;
            if (definition.isEmpty()) result.remove(key);
            else result.put(key, new BehaviorSet(Map.copyOf(definition), context));
            return result.isEmpty() ? null : result;
         });
      }
      return Map.copyOf(definition);
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
      return definition == null ? new Snapshot(Map.of(), null, domain, key, null)
         : new Snapshot(definition.callbacks(), definition.context(), domain, key, definition);
   }

   public static Varargs invokeBehavior(String domain, String key, String event, Varargs args) {
      BehaviorSet definition = definition(domain, key);
      if (definition == null) return LuaValue.NONE;
      return invokeCallback(definition.context(), definition.callbacks().get(event), args);
   }

   public static Varargs invokeCallback(String domain, String key, String event, LuaValue callback, Varargs args) {
      BehaviorSet definition = definition(domain, key);
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
      BehaviorSet definition = definition(domain, key);
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
         if (owner.definition == null || definition(owner.domain, owner.key) != owner.definition) {
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
      synchronized (REGISTRATION_LOCK) {
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
      }
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
      registerContentClass(domain, key, className, null);
   }

   public static void registerContentClass(String domain, String key, String className, MLuaContext context) {
      if (domain == null || domain.isBlank() || key == null || key.isBlank()) {
         throw new IllegalArgumentException("Scripted content domain and key must be non-empty");
      }
      if (className == null || className.isBlank()) {
         synchronized (REGISTRATION_LOCK) {
            CONTENT_CLASSES.computeIfPresent(domain, (ignoredDomain, definitions) -> {
               definitions.remove(key);
               return definitions.isEmpty() ? null : definitions;
            });
         }
         return;
      }
      validateBehaviorKey(key);
      synchronized (REGISTRATION_LOCK) {
         ensureContentClassCapacity(domain, key, context);
         CONTENT_CLASSES.compute(domain, (ignoredDomain, definitions) -> {
            ConcurrentMap<String, ContentClassDefinition> result = definitions == null
               ? new ConcurrentHashMap<>() : definitions;
            result.put(key, new ContentClassDefinition(className, context));
            return result;
         });
      }
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
      ConcurrentMap<String, ContentClassDefinition> definitions = CONTENT_CLASSES.get(domain);
      return definitions == null ? null : definitions.get(key);
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
      if (domain == null || key == null) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (REGISTRATION_LOCK) {
         CONTENT_CLASSES.compute(domain, (ignored, definitions) -> {
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

   private static void clearBehaviors(String domain, String key) {
      synchronized (REGISTRATION_LOCK) {
         SCRIPTED_BEHAVIORS.computeIfPresent(domain, (ignoredDomain, definitions) -> {
            definitions.remove(key);
            return definitions.isEmpty() ? null : definitions;
         });
      }
   }

   public static void registerBlockContainer(String key, int rows, String title) {
      registerBlockContainer(key, new ContainerDefinition(rows, title));
   }

   public static ContainerDefinition registerBlockContainer(String key, ContainerDefinition definition) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException("Scripted container block key must be non-empty");
      validateBehaviorKey(key);
      ContainerDefinition registered = validateContainerDefinition(definition);
      synchronized (REGISTRATION_LOCK) {
         ensureContainerCapacity(BLOCK_CONTAINERS, key, MAX_BLOCK_CONTAINERS);
         BLOCK_CONTAINERS.put(key, registered);
      }
      return registered;
   }

   public static void registerContainerType(String key, int rows, String title) {
      registerContainerType(key, new ContainerDefinition(rows, title));
   }

   public static ContainerDefinition registerContainerType(String key, ContainerDefinition definition) {
      if (key == null || key.isBlank()) throw new IllegalArgumentException("Scripted container type key must be non-empty");
      validateBehaviorKey(key);
      ContainerDefinition registered = validateContainerDefinition(definition);
      synchronized (REGISTRATION_LOCK) {
         ensureContainerCapacity(CONTAINER_TYPES, key, MAX_CONTAINER_TYPES);
         if (CONTAINER_TYPES.putIfAbsent(key, registered) != null) {
            throw new IllegalStateException("Scripted container type is already registered: " + key);
         }
      }
      return registered;
   }

   public static ContainerDefinition getContainerType(String key) { return CONTAINER_TYPES.get(key); }

   public static Map<String, ContainerDefinition> getContainerTypes() {
      return Map.copyOf(CONTAINER_TYPES);
   }

   public static ScriptLimits scriptLimits(String ownerModId) {
      String owner = ownerModId == null ? "" : ownerModId;
      synchronized (REGISTRATION_LOCK) {
         int behaviorCount = 0;
         int ownedBehaviorCount = 0;
         for (Map.Entry<String, ConcurrentMap<String, BehaviorSet>> domain : SCRIPTED_BEHAVIORS.entrySet()) {
            for (Map.Entry<String, BehaviorSet> definition : domain.getValue().entrySet()) {
               behaviorCount++;
               if (owner.equals(ownerFor(definition.getValue().context(), definition.getKey()))) ownedBehaviorCount++;
            }
         }
         int classCount = 0;
         int ownedClassCount = 0;
         for (ConcurrentMap<String, ContentClassDefinition> domain : CONTENT_CLASSES.values()) {
            for (Map.Entry<String, ContentClassDefinition> definition : domain.entrySet()) {
               classCount++;
               if (owner.equals(ownerFor(definition.getValue().context(), definition.getKey()))) ownedClassCount++;
            }
         }
         int blockContainers = BLOCK_CONTAINERS.size();
         int ownedBlockContainers = countOwnerKeys(BLOCK_CONTAINERS, owner);
         int containerTypes = CONTAINER_TYPES.size();
         int ownedContainerTypes = countOwnerKeys(CONTAINER_TYPES, owner);
         int aiConfigs = ENTITY_AI_INTERVAL.size();
         int ownedAIConfigs = countOwnerKeys(ENTITY_AI_INTERVAL, owner);
         return new ScriptLimits(behaviorCount, ownedBehaviorCount, classCount, ownedClassCount,
            blockContainers, ownedBlockContainers, containerTypes, ownedContainerTypes, aiConfigs, ownedAIConfigs);
      }
   }

   public static boolean unregisterContainerType(String key, ContainerDefinition expected) {
      synchronized (REGISTRATION_LOCK) { return removeContainer(CONTAINER_TYPES, key, expected); }
   }

   public static boolean unregisterBlockContainer(String key, ContainerDefinition expected) {
      synchronized (REGISTRATION_LOCK) { return removeContainer(BLOCK_CONTAINERS, key, expected); }
   }

   /** Restores a block container only while the current value is still the registration being rolled back. */
   public static boolean restoreBlockContainer(String key, ContainerDefinition expected,
                                               ContainerDefinition previous) {
      if (key == null || key.isBlank()) return false;
      java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
      synchronized (REGISTRATION_LOCK) {
         BLOCK_CONTAINERS.compute(key, (ignored, current) -> {
            if (current != expected) return current;
            restored.set(true);
            return previous;
         });
      }
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
      if (definition.title().length() > MAX_CONTAINER_TITLE_LENGTH) {
         throw new IllegalArgumentException("Scripted container titles cannot exceed " + MAX_CONTAINER_TITLE_LENGTH + " characters");
      }
      int itemReferences = definition.allowedItems().size();
      if (definition.allowedItems().size() > MAX_ALLOWED_ITEMS_PER_RULE) {
         throw new IllegalArgumentException("Scripted container item rule limit reached (" + MAX_ALLOWED_ITEMS_PER_RULE + ")");
      }
      if (definition.slotAllowedItems().size() > definition.rows() * 9) {
         throw new IllegalArgumentException("Scripted container slot rule count cannot exceed its slot count");
      }
      if (definition.allowedItems().stream().anyMatch(item -> item.isBlank() || !item.contains(":"))) {
         throw new IllegalArgumentException("Scripted container allowed item ids must be non-empty and namespaced");
      }
      for (Map.Entry<Integer, java.util.Set<String>> slot : definition.slotAllowedItems().entrySet()) {
         itemReferences += slot.getValue().size();
         if (slot.getValue().size() > MAX_ALLOWED_ITEMS_PER_RULE) {
            throw new IllegalArgumentException("Scripted container slot item rule limit reached (" + MAX_ALLOWED_ITEMS_PER_RULE + ")");
         }
         if (slot.getKey() == null || slot.getKey() < 1 || slot.getKey() > definition.rows() * 9) {
            throw new IllegalArgumentException("Scripted container slot rules must use one-based slots within the registered row count");
         }
         if (slot.getValue().stream().anyMatch(item -> item.isBlank() || !item.contains(":"))) {
            throw new IllegalArgumentException("Scripted container slot item ids must be non-empty and namespaced");
         }
      }
      if (itemReferences > MAX_ALLOWED_ITEM_REFERENCES_PER_CONTAINER) {
         throw new IllegalArgumentException("Scripted container item rule limit reached (" + MAX_ALLOWED_ITEM_REFERENCES_PER_CONTAINER + " item references per container)");
      }
      return definition;
   }

   public static ContainerDefinition getBlockContainer(String key) { return BLOCK_CONTAINERS.get(key); }

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

   private static void validateBehaviorName(String name) {
      if (name.length() > MAX_BEHAVIOR_NAME_LENGTH) {
         throw new IllegalArgumentException("Scripted behavior names cannot exceed " + MAX_BEHAVIOR_NAME_LENGTH + " characters");
      }
   }

   private static void validateBehaviorKey(String key) {
      if (key == null || key.isBlank()) {
         throw new IllegalArgumentException("Scripted content identifier must be non-empty");
      }
      if (key.length() > MAX_BEHAVIOR_KEY_LENGTH) {
         throw new IllegalArgumentException("Scripted content identifiers cannot exceed " + MAX_BEHAVIOR_KEY_LENGTH + " characters");
      }
   }

   private static void ensureBehaviorCapacity(String domain, String key, MLuaContext context) {
      int total = 0;
      int ownerCount = 0;
      for (Map.Entry<String, ConcurrentMap<String, BehaviorSet>> domainEntry : SCRIPTED_BEHAVIORS.entrySet()) {
         for (Map.Entry<String, BehaviorSet> entry : domainEntry.getValue().entrySet()) {
            if (domainEntry.getKey().equals(domain) && entry.getKey().equals(key)) continue;
            total++;
            if (sameOwner(entry.getValue().context(), context, entry.getKey())) ownerCount++;
         }
      }
      if (total >= MAX_BEHAVIOR_DEFINITIONS) {
         throw new IllegalStateException("Scripted behavior definition limit reached (" + MAX_BEHAVIOR_DEFINITIONS + " total)");
      }
      if (ownerCount >= MAX_BEHAVIOR_DEFINITIONS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted behavior definition limit reached (" + MAX_BEHAVIOR_DEFINITIONS_PER_ADDON + ")");
      }
   }

   private static void ensureContentClassCapacity(String domain, String key, MLuaContext context) {
      int total = 0;
      int ownerCount = 0;
      for (Map.Entry<String, ConcurrentMap<String, ContentClassDefinition>> domainEntry : CONTENT_CLASSES.entrySet()) {
         for (Map.Entry<String, ContentClassDefinition> entry : domainEntry.getValue().entrySet()) {
            if (domainEntry.getKey().equals(domain) && entry.getKey().equals(key)) continue;
            total++;
            if (sameOwner(entry.getValue().context(), context, entry.getKey())) ownerCount++;
         }
      }
      if (total >= MAX_CONTENT_CLASS_MAPPINGS) {
         throw new IllegalStateException("Scripted content class mapping limit reached (" + MAX_CONTENT_CLASS_MAPPINGS + " total)");
      }
      if (ownerCount >= MAX_CONTENT_CLASS_MAPPINGS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted content class mapping limit reached (" + MAX_CONTENT_CLASS_MAPPINGS_PER_ADDON + ")");
      }
   }

   private static boolean sameOwner(MLuaContext left, MLuaContext right, String key) {
      if (left != null && right != null) return left.ownerModId().equals(right.ownerModId());
      int separator = key.indexOf(':');
      String keyOwner = separator > 0 ? key.substring(0, separator) : "";
      String leftOwner = left == null ? keyOwner : left.ownerModId();
      String rightOwner = right == null ? keyOwner : right.ownerModId();
      return leftOwner.equals(rightOwner);
   }

   private static String ownerFor(MLuaContext context, String key) {
      if (context != null && context.ownerModId() != null && !context.ownerModId().isBlank()) {
         return context.ownerModId();
      }
      int separator = key == null ? -1 : key.indexOf(':');
      return separator > 0 ? key.substring(0, separator) : "";
   }

   private static int countOwnerKeys(Map<String, ?> values, String owner) {
      String prefix = owner + ":";
      return (int) values.keySet().stream().filter(key -> key.startsWith(prefix)).count();
   }

   private static void ensureContainerCapacity(ConcurrentMap<String, ContainerDefinition> values,
                                               String key, int maxTotal) {
      if (values.containsKey(key)) return;
      if (values.size() >= maxTotal) {
         throw new IllegalStateException("Scripted container definition limit reached (" + maxTotal + " total)");
      }
      int separator = key.indexOf(':');
      String ownerPrefix = separator > 0 ? key.substring(0, separator + 1) : "";
      if (!ownerPrefix.isEmpty() && values.keySet().stream().filter(id -> id.startsWith(ownerPrefix)).count()
          >= MAX_CONTAINERS_PER_ADDON) {
         throw new IllegalStateException("Addon scripted container definition limit reached (" + MAX_CONTAINERS_PER_ADDON + ")");
      }
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
