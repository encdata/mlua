package dev.mlua.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.minecraft.binding.MLuaKeyCatalog;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

public final class MLuaKeybindController {
   private static final Map<String, Entry> ENTRIES = new LinkedHashMap();
   private static final Map<String, Object> KEY_CATEGORIES_26 = new LinkedHashMap<>();
   private static final MLuaKeybindSignalRegistry SIGNALS = new MLuaKeybindSignalRegistry();
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
         KeyMapping[] original = MLuaKeyMappingAccess.keyMappings(mc);
         KeyMapping[] updated = Arrays.copyOf(original, original.length + additions.size());
         for (int index = 0; index < additions.size(); index++) {
            updated[original.length + index] = additions.get(index).keyMapping();
         }
         try {
            MLuaKeyMappingAccess.setKeyMappings(mc, updated);
            MLuaKeyMappingAccess.resetMappings();
         } catch (RuntimeException failure) {
            try {
               MLuaKeyMappingAccess.setKeyMappings(mc, original);
               MLuaKeyMappingAccess.resetMappings();
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
      MLuaKeyMappingAccess.setKey(entry.keyMapping, parseKey(key));
      MLuaKeyMappingAccess.persist(entry.keyMapping);
      MLuaKeyMappingAccess.resetMappings();
   }

   public static synchronized String keyName(String ownerModId, String name) {
      KeyMapping keyMapping = resolveKeyMapping(ownerModId, name);
      return keyMapping != null ? MLuaKeyMappingAccess.key(keyMapping).toString() : "";
   }

   public static synchronized String category(String ownerModId, String name) {
      KeyMapping keyMapping = resolveKeyMapping(ownerModId, name);
      return keyMapping != null ? MLuaKeyMappingAccess.category(keyMapping) : "";
   }

   public static synchronized Map<String, String> allKeys(String ownerModId) {
      Map<String, String> values = new LinkedHashMap();
      ENTRIES.forEach((id, entry) -> {
         if (id.startsWith(ownerModId + ":")) {
            values.put(id.substring(ownerModId.length() + 1), MLuaKeyMappingAccess.key(entry.keyMapping).toString());
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
      KeyMapping[] mappings = MLuaKeyMappingAccess.keyMappings(mc);
      for (KeyMapping mapping : mappings) {
         if (MLuaKeyMappingAccess.name(mapping).equals(name)) {
            return mapping;
         }
      }
      return null;
   }

   public static synchronized List<KeyMapping> minecraftKeybinds() {
      Minecraft mc = MLuaMinecraftClient.instance();
      return Arrays.asList(MLuaKeyMappingAccess.keyMappings(mc));
   }

   public static synchronized void tick(MLuaTaskSchedulerView scheduler) {
      if (scheduler == null) return;
      Iterator var0 = ENTRIES.values().iterator();

      while(var0.hasNext()) {
         Entry entry = (Entry)var0.next();
         if (!SIGNALS.hasSignal(entry.id)) continue;

         while(MLuaKeyMappingAccess.consumeClick(entry.keyMapping)) {
            SIGNALS.fireAll(entry.id, Side.CLIENT, LuaValue.NONE);
         }
      }

   }

   public static synchronized void clearSession(MLuaRuntimeSession session, Side side) {
      MLuaSimulatedKeyState.releaseSession(session);
      SIGNALS.clearSession(session, side);
   }

   public static synchronized void clearOwner(MLuaRuntimeSession session, String ownerModId, Side side) {
      if (session == null || ownerModId == null) return;
      MLuaSimulatedKeyState.releaseOwner(session, ownerModId);
      SIGNALS.clearOwner(session, ownerModId, side);
   }

   public static synchronized void releaseAllPressedMappings() {
      MLuaSimulatedKeyState.releaseAll();
   }

   /** Releases every simulated key or mouse mapping held by one addon runtime. */
   public static synchronized void releaseOwnerPressedMappings(MLuaRuntimeSession session, String ownerModId) {
      if (session == null || ownerModId == null) return;
      MLuaSimulatedKeyState.releaseOwner(session, ownerModId);
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
      return new KeyMapping(label, MLuaKeyMappingAccess.keyType(key), MLuaKeyMappingAccess.keyValue(key), category);
      //--endversion
      //--version 26.1
      return new KeyMapping(label, MLuaKeyMappingAccess.keyType(key), MLuaKeyMappingAccess.keyValue(key), keyCategory(ownerModId, category));
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
         InputConstants.Key key = MLuaKeyMappingAccess.createKey(InputConstants.Type.KEYSYM, (int) numericCode);
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

      InputConstants.Key parsed = MLuaKeyMappingAccess.parseKeyName(normalized);
      if (parsed != null) return parsed;
      throw new LuaError("[MLua] Unknown key name '" + value + "'. Use a name from Enum.Key or a Minecraft key id such as 'key.keyboard.r'.");
   }

   private static String keyCatalogValue(String value) {
      for (MLuaKeyCatalog.KeyEntry entry : MLuaKeyCatalog.entries()) {
         if (entry.name().equalsIgnoreCase(value) || entry.value().equalsIgnoreCase(value)) return entry.value();
      }
      return null;
   }

   public static InputConstants.Key key(KeyMapping keyMapping) {
      return MLuaKeyMappingAccess.key(keyMapping);
   }

   public static void setKey(KeyMapping keyMapping, InputConstants.Key key) {
      requireClientThread("SetKeybind");
      MLuaKeyMappingAccess.setKey(keyMapping, key);
   }

   /** Reads the effective state, including addon-owned logical presses. */
   public static boolean isDown(KeyMapping keyMapping) {
      return MLuaSimulatedKeyState.isDown(keyMapping);
   }

   /** Reasserts addon-held logical presses before Minecraft samples player input. */
   public static void refreshSimulatedPresses() {
      MLuaSimulatedKeyState.refresh();
   }

   /** Sets a native key mapping state directly. Call on the client thread. */
   public static void setDown(KeyMapping keyMapping, boolean down) {
      requireClientThread("SetKeyDown");
      if (keyMapping == null) {
         throw new IllegalStateException("[MLua] This Minecraft version does not expose key mapping press control.");
      }
      MLuaKeyMappingAccess.setDown(keyMapping, down);
   }

   /** Sets one addon-owned simulated press without changing another addon's ownership. */
   public static void setDown(KeyMapping keyMapping, boolean down,
         MLuaRuntimeSession runtimeSession, String ownerModId) {
      MLuaSimulatedKeyState.setDown(keyMapping, down, runtimeSession, ownerModId);
   }

   /** Validates and applies a group of addon-owned key states atomically. */
   public static void setDownBatch(List<KeyState> states,
         MLuaRuntimeSession runtimeSession, String ownerModId) {
      MLuaSimulatedKeyState.setDownBatch(states, runtimeSession, ownerModId);
   }

   public record KeyState(KeyMapping keyMapping, boolean pressed) { }

   /** Queues one logical key click, matching Minecraft's key mapping click queue. */
   public static void click(KeyMapping keyMapping) {
      requireClientThread("ClickKey");
      if (keyMapping == null) {
         throw new IllegalStateException("[MLua] This Minecraft version does not expose key mapping clicks.");
      }
      MLuaKeyMappingAccess.click(keyMapping);
   }

   public static boolean consumeClick(KeyMapping keyMapping) {
      return MLuaKeyMappingAccess.consumeClick(keyMapping);
   }

   public static String name(KeyMapping keyMapping) {
      return MLuaKeyMappingAccess.name(keyMapping);
   }

   public static String category(KeyMapping keyMapping) {
      return MLuaKeyMappingAccess.category(keyMapping);
   }

   private static void requireClientThread(String operation) {
      Minecraft minecraft = MLuaMinecraftClient.instance();
      if (!dev.mlua.runtime.client.MLuaClientThread.isClientExecutionContext()) {
         throw new LuaError("[MLua] InputService:" + operation + " must run on Minecraft's client thread.");
      }
   }

   public static record Entry(String id, KeyMapping keyMapping) { }
}
