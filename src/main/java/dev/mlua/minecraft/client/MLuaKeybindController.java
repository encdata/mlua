package dev.mlua.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.common.enums.Side;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.minecraft.binding.MLuaKeyCatalog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class MLuaKeybindController {
   private static final Map<String, Entry> ENTRIES = new LinkedHashMap();
   private static final Map<KeyMapping, Set<PressOwner>> SIMULATED_PRESSES = new IdentityHashMap<>();
   private static final Map<KeyMapping, Boolean> SIMULATED_BASELINE_DOWN = new IdentityHashMap<>();
   private static final Map<String, Object> KEY_CATEGORIES_26 = new LinkedHashMap<>();
   private static final MLuaKeybindSignalRegistry SIGNALS = new MLuaKeybindSignalRegistry();
   private static final Method TYPE_GET_OR_CREATE = findTypeGetOrCreate();
   private static final Method INPUT_GET_KEY = findInputGetKey();
   private static final Method KEY_GET_TYPE = findKeyGetType();
   private static final Method KEY_GET_VALUE = findKeyGetValue();
   private static final Method KEY_GET_KEY = findKeyGetKey();
   private static final Method KEY_SET_KEY = findKeySetKey();
   private static final Method KEY_IS_DOWN = findKeyIsDown();
   private static final Method KEY_SET_DOWN = findKeySetDown();
   private static final Method KEY_CLICK = findKeyClick();
   private static final Method KEY_CONSUME_CLICK = findKeyConsumeClick();
   private static final Method KEY_GET_NAME = findKeyGetName();
   private static final Method KEY_GET_CATEGORY = findKeyGetCategory();
   private static final Method KEY_RESET_MAPPING = findKeyResetMapping();
   private static final Field MINECRAFT_OPTIONS = findMinecraftOptionsField();
   private static final Field OPTIONS_KEY_MAPPINGS = findOptionsKeyMappingsField();

   private MLuaKeybindController() {
   }

   public static synchronized Entry register(String ownerModId, String name, String label, String category, LuaValue defaultKey) {
      return registerBatch(ownerModId, List.of(new KeybindDefinition(name, label, category, defaultKey))).get(0);
   }

   /** Prepares every mapping before changing Minecraft's options, then installs the set in one operation. */
   public static synchronized List<Entry> registerBatch(String ownerModId, List<KeybindDefinition> definitions) {
      if (ownerModId == null || ownerModId.isBlank()) {
         throw new IllegalArgumentException("Keybind owner must be non-empty");
      }
      List<KeybindDefinition> requested = List.copyOf(definitions);
      Set<String> requestedIds = new LinkedHashSet<>();
      for (KeybindDefinition definition : requested) {
         if (definition == null || definition.name() == null || definition.name().isBlank()) {
            throw new IllegalArgumentException("Keybind definitions require non-empty names");
         }
         if (!requestedIds.add(ownerModId + ":" + definition.name())) {
            throw new IllegalArgumentException("Duplicate keybind in batch: " + definition.name());
         }
      }
      List<Entry> result = new ArrayList<>(requested.size());
      List<Entry> additions = new ArrayList<>();
      for (KeybindDefinition definition : requested) {
         String id = ownerModId + ":" + definition.name();
         Entry entry = ENTRIES.get(id);
         if (entry == null) {
            entry = new Entry(id, createKeyMapping(ownerModId, definition.label(), definition.category(),
               definition.defaultKey()));
            additions.add(entry);
         }
         result.add(entry);
      }

      if (!additions.isEmpty()) {
         Minecraft mc = MLuaMinecraftClient.instance();
         KeyMapping[] original = keyMappings(mc);
         KeyMapping[] updated = Arrays.copyOf(original, original.length + additions.size());
         for (int index = 0; index < additions.size(); index++) {
            updated[original.length + index] = additions.get(index).keyMapping();
         }
         try {
            setKeyMappings(mc, updated);
            resetMappings();
         } catch (RuntimeException failure) {
            try {
               setKeyMappings(mc, original);
               resetMappings();
            } catch (RuntimeException rollbackFailure) {
               failure.addSuppressed(rollbackFailure);
            }
            throw new IllegalStateException("[MLua] Failed to install keybind batch for addon '"
               + ownerModId + "'; the previous Minecraft key mappings were restored when possible.", failure);
         }
         for (Entry entry : additions) ENTRIES.put(entry.id(), entry);
      }
      return List.copyOf(result);
   }

   public record KeybindDefinition(String name, String label, String category, LuaValue defaultKey) { }

   public static synchronized boolean isDown(String ownerModId, String name) {
      Entry entry = (Entry)ENTRIES.get(ownerModId + ":" + name);
      return entry != null && isDown(entry.keyMapping);
   }

   public static synchronized MLuaSignal signal(String ownerModId, String name, MLuaTaskSchedulerView scheduler) {
      return SIGNALS.signal(ownerModId + ":" + name, scheduler);
   }

   public static synchronized void setKey(String ownerModId, String name, LuaValue key) {
      requireClientThread("SetKeybind");
      Entry entry = entry(ownerModId, name);
      setKey(entry.keyMapping, parseKey(key));
      persist(entry.keyMapping);
      refreshMappings();
   }

   public static synchronized String keyName(String ownerModId, String name) {
      KeyMapping keyMapping = resolveKeyMapping(ownerModId, name);
      return keyMapping != null ? key(keyMapping).toString() : "";
   }

   public static synchronized String category(String ownerModId, String name) {
      KeyMapping keyMapping = resolveKeyMapping(ownerModId, name);
      return keyMapping != null ? category(keyMapping) : "";
   }

   public static synchronized Map<String, String> allKeys(String ownerModId) {
      Map<String, String> values = new LinkedHashMap();
      ENTRIES.forEach((id, entry) -> {
         if (id.startsWith(ownerModId + ":")) {
            values.put(id.substring(ownerModId.length() + 1), key(entry.keyMapping).toString());
         }

      });
      return values;
   }

   public static synchronized boolean hasLocalKeybind(String ownerModId, String name) {
      return ENTRIES.containsKey(ownerModId + ":" + name);
   }

   public static synchronized KeyMapping resolve(String ownerModId, String name) {
      return resolveKeyMapping(ownerModId, name);
   }

   public static synchronized KeyMapping minecraftKeybind(String name) {
      Minecraft mc = MLuaMinecraftClient.instance();
      KeyMapping[] mappings = keyMappings(mc);
      for (KeyMapping mapping : mappings) {
         if (name(mapping).equals(name)) {
            return mapping;
         }
      }
      return null;
   }

   public static synchronized List<KeyMapping> minecraftKeybinds() {
      Minecraft mc = MLuaMinecraftClient.instance();
      return Arrays.asList(keyMappings(mc));
   }

   public static synchronized void tick(MLuaTaskSchedulerView scheduler) {
      if (scheduler == null) return;
      Iterator var0 = ENTRIES.values().iterator();

      while(var0.hasNext()) {
         Entry entry = (Entry)var0.next();
         if (!SIGNALS.hasSignal(entry.id)) continue;

         while(consumeClick(entry.keyMapping)) {
            SIGNALS.fireAll(entry.id, Side.CLIENT, LuaValue.NONE);
         }
      }

   }

   public static synchronized void clearSession(MLuaRuntimeSession session, Side side) {
      releasePresses(owner -> owner.runtimeSession == session);
      SIGNALS.clearSession(session, side);
   }

   public static synchronized void clearOwner(MLuaRuntimeSession session, String ownerModId, Side side) {
      if (session == null || ownerModId == null) return;
      releasePresses(owner -> owner.runtimeSession == session && owner.modId.equals(ownerModId));
      SIGNALS.clearOwner(session, ownerModId, side);
   }

   public static synchronized void releaseAllPressedMappings() {
      releasePresses(owner -> true);
   }

   /** Releases every simulated key or mouse mapping held by one addon runtime. */
   public static synchronized void releaseOwnerPressedMappings(MLuaRuntimeSession session, String ownerModId) {
      if (session == null || ownerModId == null) return;
      releasePresses(owner -> owner.runtimeSession == session && owner.modId.equals(ownerModId));
   }

   private static Entry entry(String ownerModId, String name) {
      Entry entry = (Entry)ENTRIES.get(ownerModId + ":" + name);
      if (entry == null) {
         throw new LuaError("[MLua] Unknown keybind '" + name + "' for addon '" + ownerModId + "'.");
      } else {
         return entry;
      }
   }

   private static KeyMapping resolveKeyMapping(String ownerModId, String name) {
      Entry entry = (Entry)ENTRIES.get(ownerModId + ":" + name);
      return entry != null ? entry.keyMapping : minecraftKeybind(name);
   }

   private static KeyMapping createKeyMapping(String ownerModId, String label, String category, LuaValue defaultKey) {
      InputConstants.Key key = parseKey(defaultKey);
      //--version 1.20.1,1.21.1
      return new KeyMapping(label, keyType(key), keyValue(key), category);
      //--endversion
      //--version 26.1
      return new KeyMapping(label, keyType(key), keyValue(key), keyCategory(ownerModId, category));
      //--endversion
   }

   //--version 26.1
   private static KeyMapping.Category keyCategory(String ownerModId, String category) {
      String value = category == null ? "" : category.trim();
      if (value.startsWith("key.categories.")) value = value.substring("key.categories.".length());
      if (value.isEmpty()) value = "controls";
      String id = value.indexOf(':') >= 0 ? value : ownerModId + ":" + value;
      return (KeyMapping.Category) KEY_CATEGORIES_26.computeIfAbsent(id,
         key -> {
            KeyMapping.Category existing = findRegisteredKeyCategory(key);
            return existing != null ? existing
               : KeyMapping.Category.register(dev.mlua.minecraft.MLuaResourceLocations.parse(key));
         });
   }

   private static KeyMapping.Category findRegisteredKeyCategory(String id) {
      for (Field field : KeyMapping.Category.class.getDeclaredFields()) {
         if (!Modifier.isStatic(field.getModifiers()) || !Iterable.class.isAssignableFrom(field.getType())) continue;
         try {
            if (!field.trySetAccessible()) continue;
            Object value = field.get(null);
            if (!(value instanceof Iterable<?> categories)) continue;
            for (Object category : categories) {
               if (category instanceof KeyMapping.Category candidate
                     && candidate.id().toString().equals(id)) return candidate;
            }
         } catch (IllegalAccessException | RuntimeException inaccessible) { }
      }
      return null;
   }
   //--endversion

   /** Validates a script-facing key before a batch creates any KeyMapping instances. */
   public static void validateKey(LuaValue value) {
      parseKey(value);
   }

   private static InputConstants.Key parseKey(LuaValue value) {
      if (value == null || value.isnil()) return InputConstants.UNKNOWN;
      if (value.istable()) {
         LuaValue raw = value.checktable().rawget("Value");
         LuaValue name = value.checktable().rawget("Name");
         if (!raw.isnil() && !name.isnil()) {
            InputConstants.Key fromValue = parseKey(raw);
            InputConstants.Key fromName = parseKey(name);
            if (!fromValue.equals(fromName)) {
               throw new LuaError("[MLua] KeyDefinition Name and Value fields refer to different keys.");
            }
            return fromValue;
         }
         if (!raw.isnil()) return parseKey(raw);
         if (!name.isnil()) return parseKey(name);
         throw new LuaError("[MLua] KeyDefinition must provide a Name or Value field.");
      }
      if (value.isstring()) return parseKey(value.checkjstring());
      if (value.isnumber()) {
         double numericCode = value.checkdouble();
         if (!Double.isFinite(numericCode) || numericCode != Math.rint(numericCode)
               || numericCode < -1 || numericCode > Integer.MAX_VALUE) {
            throw new LuaError("[MLua] Key codes must be whole numbers from -1 through " + Integer.MAX_VALUE + ".");
         }
         if (numericCode == -1) return InputConstants.UNKNOWN;
         InputConstants.Key key = createKey(Type.KEYSYM, (int) numericCode);
         if (key == null || key.equals(InputConstants.UNKNOWN)) {
            throw new LuaError("[MLua] Minecraft could not create a key mapping for code " + (int) numericCode + ".");
         }
         return key;
      }
      throw new LuaError("[MLua] A key must be a key name, numeric code, KeyDefinition, or nil.");
   }

   private static InputConstants.Key parseKey(String value) {
      if (value == null || value.isBlank()) {
         throw new LuaError("[MLua] Key names cannot be blank; use nil or -1 for an unbound key.");
      }
      String normalized = value.trim();
      if (normalized.equalsIgnoreCase("unknown") || normalized.equalsIgnoreCase("unbound")
            || normalized.equalsIgnoreCase("key.keyboard.unknown")) return InputConstants.UNKNOWN;

      String catalogValue = keyCatalogValue(normalized);
      if (catalogValue != null) normalized = catalogValue;
      else if (normalized.regionMatches(true, 0, "KEY_", 0, 4)) {
         catalogValue = keyCatalogValue(normalized.substring(4));
         if (catalogValue != null) normalized = catalogValue;
      }

      if (INPUT_GET_KEY != null) {
         try {
            Object parsed = INPUT_GET_KEY.invoke(null, normalized);
            if (parsed instanceof InputConstants.Key key && !key.equals(InputConstants.UNKNOWN)) return key;
         } catch (ReflectiveOperationException | RuntimeException ignored) { }
      }
      throw new LuaError("[MLua] Unknown key name '" + value + "'. Use a name from Enum.Key or a Minecraft key id such as 'key.keyboard.r'.");
   }

   private static String keyCatalogValue(String value) {
      for (MLuaKeyCatalog.KeyEntry entry : MLuaKeyCatalog.entries()) {
         if (entry.name().equalsIgnoreCase(value) || entry.value().equalsIgnoreCase(value)) return entry.value();
      }
      return null;
   }

   private static InputConstants.Key createKey(InputConstants.Type type, int code) {
      if (TYPE_GET_OR_CREATE != null) {
         try {
            return (InputConstants.Key)TYPE_GET_OR_CREATE.invoke(type, code);
         } catch (Exception var3) {
         }
      }

      return InputConstants.UNKNOWN;
   }

   private static Method findTypeGetOrCreate() {
      return findMethod(InputConstants.Type.class, InputConstants.Key.class, false,
         new Class<?>[]{int.class}, "getOrCreate");
   }

   private static Method findInputGetKey() {
      return findMethod(InputConstants.class, InputConstants.Key.class, true,
         new Class<?>[]{String.class}, "getKey");
   }

   private static InputConstants.Type keyType(InputConstants.Key key) {
      Object value = invokeRequired(KEY_GET_TYPE, key, "read key type");
      if (value instanceof InputConstants.Type type) return type;
      throw new IllegalStateException("[MLua] Minecraft returned an invalid key type.");
   }

   private static int keyValue(InputConstants.Key key) {
      Object value = invokeRequired(KEY_GET_VALUE, key, "read key code");
      if (value instanceof Number number) return number.intValue();
      throw new IllegalStateException("[MLua] Minecraft returned an invalid key code.");
   }

   public static InputConstants.Key key(KeyMapping keyMapping) {
      Object value = invokeRequired(KEY_GET_KEY, keyMapping, "read key mapping value");
      if (value instanceof InputConstants.Key key) return key;
      throw new IllegalStateException("[MLua] Minecraft returned an invalid key mapping value.");
   }

   public static void setKey(KeyMapping keyMapping, InputConstants.Key key) {
      requireClientThread("SetKeybind");
      if (KEY_SET_KEY != null) {
         try {
            KEY_SET_KEY.invoke(keyMapping, key);
            return;
         } catch (Exception var3) {
         }
      }

      throw new IllegalStateException("[MLua] Failed to set key binding.");
   }

   /** Reads the effective state, including owner-held logical presses set through InputService. */
   public static synchronized boolean isDown(KeyMapping keyMapping) {
      if (keyMapping == null) return false;
      Set<PressOwner> simulatedOwners = SIMULATED_PRESSES.get(keyMapping);
      if (simulatedOwners != null && !simulatedOwners.isEmpty()) return true;
      Object value = invokeRequired(KEY_IS_DOWN, keyMapping, "read key mapping state");
      if (value instanceof Boolean down) return down;
      throw new IllegalStateException("[MLua] Minecraft returned an invalid key mapping state.");
   }

   /** Sets the logical pressed state of one Minecraft key mapping. Call on the client thread. */
   public static void setDown(KeyMapping keyMapping, boolean down) {
      requireClientThread("SetKeyDown");
      if (keyMapping == null || KEY_SET_DOWN == null) {
         throw new IllegalStateException("[MLua] This Minecraft version does not expose key mapping press control.");
      }
      try {
         if (Modifier.isStatic(KEY_SET_DOWN.getModifiers())) KEY_SET_DOWN.invoke(null, keyMapping, down);
         else KEY_SET_DOWN.invoke(keyMapping, down);
      } catch (ReflectiveOperationException error) {
         throw new IllegalStateException("[MLua] Failed to change key mapping state.", error);
      }
   }

   /** Sets a mapping's simulated state for one script owner; independent owners cannot release each other. */
   public static synchronized void setDown(KeyMapping keyMapping, boolean down,
         MLuaRuntimeSession runtimeSession, String ownerModId) {
      requireClientThread("SetKeyDown");
      if (keyMapping == null) throw new IllegalArgumentException("Key mapping must not be null");
      PressOwner owner = new PressOwner(runtimeSession, ownerModId == null ? "" : ownerModId);
      Set<PressOwner> owners = SIMULATED_PRESSES.get(keyMapping);
      if (down) {
         if (owners == null || owners.isEmpty()) {
            boolean baseline = isDown(keyMapping);
            setDown(keyMapping, true);
            owners = new LinkedHashSet<>();
            owners.add(owner);
            SIMULATED_BASELINE_DOWN.put(keyMapping, baseline);
            SIMULATED_PRESSES.put(keyMapping, owners);
            return;
         }
         setDown(keyMapping, true);
         owners.add(owner);
         return;
      }
      // A release only undoes a press this runtime actually owns. Calling
      // SetKeyDown(..., false) must not clear a physical or another mod's
      // logical key state when this runtime never pressed the mapping.
      if (owners == null || !owners.remove(owner)) return;
      if (owners.isEmpty()) {
         SIMULATED_PRESSES.remove(keyMapping);
         restoreUnsimulatedState(keyMapping);
      }
   }

   /** Validates and applies a group of owner-scoped key states on the client thread. */
   public static synchronized void setDownBatch(List<KeyState> states,
         MLuaRuntimeSession runtimeSession, String ownerModId) {
      requireClientThread("SetKeyStates");
      if (runtimeSession == null || ownerModId == null || ownerModId.isBlank()) {
         throw new IllegalArgumentException("Key state batches require an addon runtime and owner");
      }
      List<KeyState> requested = List.copyOf(states);
      Set<KeyMapping> seen = Collections.newSetFromMap(new IdentityHashMap<>());
      PressOwner owner = new PressOwner(runtimeSession, ownerModId);
      List<Boolean> previous = new ArrayList<>(requested.size());
      for (KeyState state : requested) {
         if (state == null || state.keyMapping() == null) {
            throw new IllegalArgumentException("Key state batches cannot contain a missing mapping");
         }
         if (!seen.add(state.keyMapping())) {
            throw new IllegalArgumentException("Key state batch contains the same mapping more than once");
         }
         Set<PressOwner> owners = SIMULATED_PRESSES.get(state.keyMapping());
         previous.add(owners != null && owners.contains(owner));
      }

      int applied = 0;
      try {
         for (int index = 0; index < requested.size(); index++) {
            KeyState state = requested.get(index);
            applied = index + 1;
            setDown(state.keyMapping(), state.pressed(), runtimeSession, ownerModId);
         }
      } catch (RuntimeException failure) {
         for (int index = applied - 1; index >= 0; index--) {
            KeyState state = requested.get(index);
            try {
               setDown(state.keyMapping(), previous.get(index), runtimeSession, ownerModId);
            } catch (RuntimeException rollbackFailure) {
               failure.addSuppressed(rollbackFailure);
            }
         }
         String reason = failure.getMessage();
         throw new IllegalStateException("[MLua] Failed to apply key state batch"
            + (reason == null || reason.isBlank() ? "" : ": " + reason)
            + "; previous addon-owned states were restored where possible.", failure);
      }
   }

   public record KeyState(KeyMapping keyMapping, boolean pressed) { }

   private static void releasePresses(java.util.function.Predicate<PressOwner> predicate) {
      Minecraft minecraft = MLuaMinecraftClient.instance();
      if (!dev.mlua.runtime.client.MLuaClientThread.isClientThread()) {
         minecraft.execute(() -> {
            synchronized (MLuaKeybindController.class) {
               releasePresses(predicate);
            }
         });
         return;
      }
      List<KeyMapping> release = new ArrayList<>();
      SIMULATED_PRESSES.entrySet().removeIf(entry -> {
         Set<PressOwner> owners = entry.getValue();
         owners.removeIf(predicate);
         if (!owners.isEmpty()) return false;
         release.add(entry.getKey());
         return true;
      });
      if (release.isEmpty()) return;
      for (KeyMapping mapping : release) {
         if (!SIMULATED_PRESSES.containsKey(mapping)) restoreUnsimulatedState(mapping);
      }
   }

   /** Restores a mapping's state from before Lua simulated it, including a key still physically held. */
   private static void restoreUnsimulatedState(KeyMapping mapping) {
      boolean baseline = SIMULATED_BASELINE_DOWN.getOrDefault(mapping, false);
      SIMULATED_BASELINE_DOWN.remove(mapping);
      setDown(mapping, baseline || isPhysicalKeyDown(mapping));
   }

   private static boolean isPhysicalKeyDown(KeyMapping mapping) {
      try {
         InputConstants.Key inputKey = key(mapping);
         int value = keyValue(inputKey);
         if (value < 0) return false;
//--version 26.1
         long window = MLuaMinecraftClient.instance().getWindow().handle();
//--endversion
//--version 1.20.1,1.21.1
         long window = MLuaMinecraftClient.instance().getWindow().getWindow();
//--endversion
         Type type = keyType(inputKey);
         if (type == Type.SCANCODE) return false;
         if (type == Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, value) == GLFW.GLFW_PRESS;
         }
         int state = GLFW.glfwGetKey(window, value);
         return state == GLFW.GLFW_PRESS || state == GLFW.GLFW_REPEAT;
      } catch (RuntimeException | LinkageError unavailable) {
         // Some mappings use non-standard scan codes; retain their captured state when
         // GLFW cannot answer for that mapping.
         return false;
      }
   }

   private record PressOwner(MLuaRuntimeSession runtimeSession, String modId) {
      @Override public boolean equals(Object other) {
         return other instanceof PressOwner owner
            && runtimeSession == owner.runtimeSession && modId.equals(owner.modId);
      }
      @Override public int hashCode() {
         return 31 * System.identityHashCode(runtimeSession) + modId.hashCode();
      }
   }

   /** Queues one logical key click, matching Minecraft's key mapping click queue. */
   public static void click(KeyMapping keyMapping) {
      requireClientThread("ClickKey");
      if (keyMapping == null || KEY_CLICK == null) {
         throw new IllegalStateException("[MLua] This Minecraft version does not expose key mapping clicks.");
      }
      try {
         if (Modifier.isStatic(KEY_CLICK.getModifiers())) KEY_CLICK.invoke(null, key(keyMapping));
         else KEY_CLICK.invoke(keyMapping);
      } catch (ReflectiveOperationException error) {
         throw new IllegalStateException("[MLua] Failed to queue key mapping click.", error);
      }
   }

   public static boolean consumeClick(KeyMapping keyMapping) {
      Object value = invokeRequired(KEY_CONSUME_CLICK, keyMapping, "consume key mapping click");
      if (value instanceof Boolean consumed) return consumed;
      throw new IllegalStateException("[MLua] Minecraft returned an invalid key click result.");
   }

   public static String name(KeyMapping keyMapping) {
      Object value = invokeRequired(KEY_GET_NAME, keyMapping, "read key mapping identifier");
      if (value instanceof String name) return name;
      throw new IllegalStateException("[MLua] Minecraft returned an invalid key mapping identifier.");
   }

   public static String category(KeyMapping keyMapping) {
      //--version 1.20.1,1.21.1
      Object value = invokeRequired(KEY_GET_CATEGORY, keyMapping, "read key mapping category");
      if (value instanceof String category) return category;
      throw new IllegalStateException("[MLua] Minecraft returned an invalid key mapping category.");
      //--endversion
      //--version 26.1
      return keyMapping.getCategory().id().toString();
      //--endversion
   }

   private static Method findKeyGetKey() {
      return findMethod(KeyMapping.class, InputConstants.Key.class, false,
         new Class<?>[0], "getKey", "getDefaultKey");
   }

   private static Method findKeySetKey() {
      return findMethod(KeyMapping.class, Void.TYPE, false,
         new Class<?>[]{InputConstants.Key.class}, "setKey");
   }

   private static Method findKeyIsDown() {
      return findMethod(KeyMapping.class, Boolean.TYPE, false,
         new Class<?>[0], "isDown");
   }

   private static Method findKeySetDown() {
      Method method = findMethod(KeyMapping.class, Void.TYPE, false,
         new Class<?>[]{Boolean.TYPE}, "setDown");
      if (method == null) method = findMethod(KeyMapping.class, Void.TYPE, true,
         new Class<?>[]{KeyMapping.class, Boolean.TYPE}, "setDown");
      if (method == null) method = findMethod(KeyMapping.class, Void.TYPE, false,
         new Class<?>[]{Boolean.TYPE}, "set");
      if (method == null) method = findMethod(KeyMapping.class, Void.TYPE, true,
         new Class<?>[]{KeyMapping.class, Boolean.TYPE}, "set");
      return method;
   }

   private static Method findKeyClick() {
      Method method = findMethod(KeyMapping.class, Void.TYPE, false,
         new Class<?>[0], "click");
      if (method == null) method = findMethod(KeyMapping.class, Void.TYPE, true,
         new Class<?>[]{InputConstants.Key.class}, "click");
      return method;
   }

   private static Method findKeyConsumeClick() {
      return findMethod(KeyMapping.class, Boolean.TYPE, false,
         new Class<?>[0], "consumeClick");
   }

   private static Method findKeyGetName() {
      return findMethod(KeyMapping.class, String.class, false,
         new Class<?>[0], "getName", "saveString");
   }

   private static Method findKeyGetCategory() {
      //--version 1.20.1,1.21.1
      return findMethod(KeyMapping.class, String.class, false,
         new Class<?>[0], "getCategory");
      //--endversion
      //--version 26.1
      return findMethod(KeyMapping.class, KeyMapping.Category.class, false,
         new Class<?>[0], "getCategory");
      //--endversion
   }

   private static Method findKeyGetType() {
      return findMethod(InputConstants.Key.class, InputConstants.Type.class, false,
         new Class<?>[0], "getType");
   }

   private static Method findKeyGetValue() {
      return findMethod(InputConstants.Key.class, Integer.TYPE, false,
         new Class<?>[0], "getValue");
   }

   private static void install(KeyMapping keyMapping) {
      Minecraft mc = MLuaMinecraftClient.instance();
      KeyMapping[] original = keyMappings(mc);
      KeyMapping[] updated = Arrays.copyOf(original, original.length + 1);
      updated[original.length] = keyMapping;
      try {
         setKeyMappings(mc, updated);
         resetMappings();
      } catch (RuntimeException failure) {
         try {
            setKeyMappings(mc, original);
            resetMappings();
         } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
         }
         throw new IllegalStateException("[MLua] Failed to install key mapping '"
            + name(keyMapping) + "'; the previous Minecraft key mappings were restored when possible.", failure);
      }
   }

   private static void refreshMappings() {
      Minecraft mc = MLuaMinecraftClient.instance();
      requireOptions(mc);
      resetMappings();
   }

   private static void persist(KeyMapping keyMapping) {
      Minecraft mc = MLuaMinecraftClient.instance();
      Options options = requireOptions(mc);
      //--version 1.20.1,1.21.1
      options.setKey(keyMapping, key(keyMapping));
      //--endversion
      //--version 26.1
      keyMapping.setKey(key(keyMapping));
      //--endversion
      options.save();
   }

   private static void requireClientThread(String operation) {
      Minecraft minecraft = MLuaMinecraftClient.instance();
      if (!dev.mlua.runtime.client.MLuaClientThread.isClientExecutionContext()) {
         throw new LuaError("[MLua] InputService:" + operation + " must run on Minecraft's client thread.");
      }
   }

   private static KeyMapping[] keyMappings(Minecraft mc) {
      Options options = requireOptions(mc);
      if (OPTIONS_KEY_MAPPINGS == null) {
         throw new IllegalStateException("[MLua] This Minecraft version does not expose its key mapping array.");
      }
      try {
         KeyMapping[] mappings = (KeyMapping[]) OPTIONS_KEY_MAPPINGS.get(options);
         if (mappings == null) throw new IllegalStateException("[MLua] Minecraft returned a null key mapping array.");
         return mappings;
      } catch (IllegalAccessException | ClassCastException failure) {
         throw new IllegalStateException("[MLua] Failed to read Minecraft's key mappings.", failure);
      }
   }

   private static void setKeyMappings(Minecraft mc, KeyMapping[] mappings) {
      Options options = requireOptions(mc);
      if (OPTIONS_KEY_MAPPINGS == null) {
         throw new IllegalStateException("[MLua] This Minecraft version does not expose its key mapping array.");
      }
      try {
         OPTIONS_KEY_MAPPINGS.set(options, mappings);
      } catch (IllegalAccessException failure) {
         throw new IllegalStateException("[MLua] Failed to update Minecraft's key mappings.", failure);
      }
   }

   private static Field findOptionsKeyMappingsField() {
      Class<?> optionsClass = Options.class;
      Iterator var1 = List.of("keyMappings", "keyMappings").iterator();

      while(var1.hasNext()) {
         String name = (String)var1.next();

         try {
            Field field = optionsClass.getDeclaredField(name);
            if (field.getType() == KeyMapping[].class) {
               field.setAccessible(true);
               return field;
            }
         } catch (NoSuchFieldException var5) {
         }
      }

      Field[] var6 = optionsClass.getDeclaredFields();
      int var7 = var6.length;

      for(int var8 = 0; var8 < var7; ++var8) {
         Field field = var6[var8];
         if (field.getType() == KeyMapping[].class) {
            field.setAccessible(true);
            return field;
         }
      }

      return null;
   }

   private static Options options(Minecraft mc) {
      if (MINECRAFT_OPTIONS != null) {
         try {
            return (Options)MINECRAFT_OPTIONS.get(mc);
         } catch (Exception var2) {
         }
      }

      return null;
   }

   private static Options requireOptions(Minecraft mc) {
      Options options = options(mc);
      if (options == null) {
         throw new IllegalStateException("[MLua] Minecraft options are not available; key mappings cannot be read or changed yet.");
      }
      return options;
   }

   private static Field findMinecraftOptionsField() {
      Class<?> minecraftClass = Minecraft.class;
      Iterator var1 = List.of("options", "options").iterator();

      while(var1.hasNext()) {
         String name = (String)var1.next();

         try {
            Field field = minecraftClass.getDeclaredField(name);
            if (field.getType() == Options.class) {
               field.setAccessible(true);
               return field;
            }
         } catch (NoSuchFieldException var5) {
         }
      }

      Field[] var6 = minecraftClass.getDeclaredFields();
      int var7 = var6.length;

      for(int var8 = 0; var8 < var7; ++var8) {
         Field field = var6[var8];
         if (field.getType() == Options.class) {
            field.setAccessible(true);
            return field;
         }
      }

      return null;
   }

   private static void resetMappings() {
      if (KEY_RESET_MAPPING != null) {
         try {
            KEY_RESET_MAPPING.invoke((Object)null);
            return;
         } catch (Exception var1) {
         }
      }

      throw new IllegalStateException("[MLua] Failed to reset key mappings.");
   }

   private static Method findKeyResetMapping() {
      return findMethod(KeyMapping.class, Void.TYPE, true,
         new Class<?>[0], "resetMapping");
   }

   /** Resolves a named method first, using structural lookup only when one exact candidate exists. */
   private static Method findMethod(Class<?> owner, Class<?> returnType, boolean isStatic,
                                    Class<?>[] parameterTypes, String... names) {
      for (String name : names) {
         try {
            Method method = owner.getMethod(name, parameterTypes);
            if (matches(method, returnType, isStatic, parameterTypes)) {
               Method accessible = makeAccessible(method);
               if (accessible != null) return accessible;
            }
         } catch (NoSuchMethodException ignored) { }
      }

      Method candidate = null;
      for (Method method : owner.getMethods()) {
         if (!matches(method, returnType, isStatic, parameterTypes)) continue;
         if (candidate != null && !candidate.equals(method)) return null;
         candidate = method;
      }
      return candidate == null ? null : makeAccessible(candidate);
   }

   private static boolean matches(Method method, Class<?> returnType, boolean isStatic,
                                  Class<?>[] parameterTypes) {
      return Modifier.isStatic(method.getModifiers()) == isStatic
         && method.getReturnType() == returnType
         && Arrays.equals(method.getParameterTypes(), parameterTypes)
         && !method.isBridge() && !method.isSynthetic();
   }

   private static Method makeAccessible(Method method) {
      try {
         if (!method.trySetAccessible() && !Modifier.isPublic(method.getModifiers())) return null;
         return method;
      } catch (RuntimeException inaccessible) {
         return Modifier.isPublic(method.getModifiers()) ? method : null;
      }
   }

   private static Object invokeRequired(Method method, Object receiver, String operation, Object... arguments) {
      if (method == null) {
         throw new IllegalStateException("[MLua] This Minecraft version does not expose the method needed to "
            + operation + ".");
      }
      try {
         return method.invoke(receiver, arguments);
      } catch (ReflectiveOperationException | RuntimeException failure) {
         throw new IllegalStateException("[MLua] Failed to " + operation + ".", failure);
      }
   }

   public static record Entry(String id, KeyMapping keyMapping) { }
}
