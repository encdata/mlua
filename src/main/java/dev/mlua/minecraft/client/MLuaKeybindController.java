package dev.mlua.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.common.enums.Side;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.minecraft.binding.MLuaKeyCatalog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class MLuaKeybindController {
   private static final Map<String, Entry> ENTRIES = new LinkedHashMap();
   private static final MLuaKeybindSignalRegistry SIGNALS = new MLuaKeybindSignalRegistry();
   private static final Method TYPE_GET_OR_CREATE = findTypeGetOrCreate();
   private static final Method INPUT_GET_KEY = findInputGetKey();
   private static final Method KEY_GET_TYPE = findKeyGetType();
   private static final Method KEY_GET_VALUE = findKeyGetValue();
   private static final Method KEY_GET_KEY = findKeyGetKey();
   private static final Method KEY_SET_KEY = findKeySetKey();
   private static final Method KEY_IS_DOWN = findKeyIsDown();
   private static final Method KEY_CONSUME_CLICK = findKeyConsumeClick();
   private static final Method KEY_GET_NAME = findKeyGetName();
   private static final Method KEY_GET_CATEGORY = findKeyGetCategory();
   private static final Method KEY_RESET_MAPPING = findKeyResetMapping();
   private static final Field MINECRAFT_OPTIONS = findMinecraftOptionsField();
   private static final Field OPTIONS_KEY_MAPPINGS = findOptionsKeyMappingsField();

   private MLuaKeybindController() {
   }

   public static synchronized Entry register(String ownerModId, String name, String label, String category, LuaValue defaultKey) {
      String id = ownerModId + ":" + name;
      Entry entry = (Entry)ENTRIES.get(id);
      if (entry == null) {
         entry = new Entry(id, createKeyMapping(label, category, defaultKey));
         ENTRIES.put(id, entry);
         install(entry.keyMapping);
      }

      return entry;
   }

   public static synchronized boolean isDown(String ownerModId, String name) {
      Entry entry = (Entry)ENTRIES.get(ownerModId + ":" + name);
      return entry != null && isDown(entry.keyMapping);
   }

   public static synchronized MLuaSignal signal(String ownerModId, String name, MLuaTaskSchedulerView scheduler) {
      return SIGNALS.signal(ownerModId + ":" + name, scheduler);
   }

   public static synchronized void setKey(String ownerModId, String name, LuaValue key) {
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

   public static synchronized KeyMapping minecraftKeybind(String name) {
      Minecraft mc = MLuaMinecraftClient.instance();
      Options options = options(mc);
      if (options == null) {
         return null;
      } else {
         KeyMapping[] var3 = keyMappings(mc);
         int var4 = var3.length;

         for(int var5 = 0; var5 < var4; ++var5) {
            KeyMapping mapping = var3[var5];
            if (name(mapping).equals(name)) {
               return mapping;
            }
         }

         return null;
      }
   }

   public static synchronized List<KeyMapping> minecraftKeybinds() {
      Minecraft mc = MLuaMinecraftClient.instance();
      return options(mc) == null ? List.of() : Arrays.asList(keyMappings(mc));
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
      SIGNALS.clearSession(session, side);
   }

   private static Entry entry(String ownerModId, String name) {
      Entry entry = (Entry)ENTRIES.get(ownerModId + ":" + name);
      if (entry == null) {
         throw new IllegalArgumentException("[MLua] Unknown keybind: " + name);
      } else {
         return entry;
      }
   }

   private static KeyMapping resolveKeyMapping(String ownerModId, String name) {
      Entry entry = (Entry)ENTRIES.get(ownerModId + ":" + name);
      return entry != null ? entry.keyMapping : minecraftKeybind(name);
   }

   private static KeyMapping createKeyMapping(String label, String category, LuaValue defaultKey) {
      InputConstants.Key key = parseKey(defaultKey);
      return new KeyMapping(label, keyType(key), keyValue(key), category);
   }

   private static InputConstants.Key parseKey(LuaValue value) {
      if (value != null && !value.isnil()) {
         if (value.istable()) {
            LuaValue raw = value.checktable().get("Value");
            if (!raw.isnil()) {
               return parseKey(raw.tojstring());
            }

            LuaValue name = value.checktable().get("Name");
            if (!name.isnil()) {
               return parseKey(name.tojstring());
            }
         }

         if (value.isstring()) {
            return parseKey(value.tojstring());
         } else if (value.isnumber()) {
            int code = value.toint();
            return code >= 0 ? createKey(Type.KEYSYM, code) : InputConstants.UNKNOWN;
         } else {
            return InputConstants.UNKNOWN;
         }
      } else {
         return InputConstants.UNKNOWN;
      }
   }

   private static InputConstants.Key parseKey(String value) {
      if (value != null && !value.isBlank()) {
         LuaValue catalogValue = keyCatalogLookup(value);
         if (!catalogValue.isnil()) {
            return parseKey(catalogValue);
         } else {
            if (INPUT_GET_KEY != null) {
               try {
                  return (InputConstants.Key)INPUT_GET_KEY.invoke((Object)null, value);
               } catch (Exception var3) {
               }
            }

            return InputConstants.UNKNOWN;
         }
      } else {
         return InputConstants.UNKNOWN;
      }
   }

   private static LuaValue keyCatalogLookup(String value) {
      LuaValue catalog = MLuaKeyCatalog.keysTable().get(value);
      return !catalog.isnil() ? catalog.get("Value") : LuaValue.NIL;
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
      Iterator var0 = List.of("getOrCreate", "getOrCreate").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = InputConstants.Type.class.getMethod(name, Integer.TYPE);
            if (method.getReturnType() == InputConstants.Key.class) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = InputConstants.Type.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == Integer.TYPE && method.getReturnType() == InputConstants.Key.class) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static Method findInputGetKey() {
      Iterator var0 = List.of("getKey", "getKey").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = InputConstants.class.getMethod(name, String.class);
            if (method.getReturnType() == InputConstants.Key.class) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = InputConstants.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1 && method.getParameterTypes()[0] == String.class && method.getReturnType() == InputConstants.Key.class) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static InputConstants.Type keyType(InputConstants.Key key) {
      if (KEY_GET_TYPE != null) {
         try {
            return (InputConstants.Type)KEY_GET_TYPE.invoke(key);
         } catch (Exception var2) {
         }
      }

      return Type.KEYSYM;
   }

   private static int keyValue(InputConstants.Key key) {
      if (KEY_GET_VALUE != null) {
         try {
            return ((Number)KEY_GET_VALUE.invoke(key)).intValue();
         } catch (Exception var2) {
         }
      }

      return -1;
   }

   public static InputConstants.Key key(KeyMapping keyMapping) {
      if (KEY_GET_KEY != null) {
         try {
            return (InputConstants.Key)KEY_GET_KEY.invoke(keyMapping);
         } catch (Exception var2) {
         }
      }

      return InputConstants.UNKNOWN;
   }

   public static void setKey(KeyMapping keyMapping, InputConstants.Key key) {
      if (KEY_SET_KEY != null) {
         try {
            KEY_SET_KEY.invoke(keyMapping, key);
            return;
         } catch (Exception var3) {
         }
      }

      throw new IllegalStateException("[MLua] Failed to set key binding.");
   }

   public static boolean isDown(KeyMapping keyMapping) {
      if (KEY_IS_DOWN != null) {
         try {
            return (Boolean)KEY_IS_DOWN.invoke(keyMapping);
         } catch (Exception var2) {
         }
      }

      return false;
   }

   public static boolean consumeClick(KeyMapping keyMapping) {
      if (KEY_CONSUME_CLICK != null) {
         try {
            return (Boolean)KEY_CONSUME_CLICK.invoke(keyMapping);
         } catch (Exception var2) {
         }
      }

      return false;
   }

   public static String name(KeyMapping keyMapping) {
      if (KEY_GET_NAME != null) {
         try {
            return (String)KEY_GET_NAME.invoke(keyMapping);
         } catch (Exception var2) {
         }
      }

      return "";
   }

   public static String category(KeyMapping keyMapping) {
      if (KEY_GET_CATEGORY != null) {
         try {
            return (String)KEY_GET_CATEGORY.invoke(keyMapping);
         } catch (Exception var2) {
         }
      }

      return "";
   }

   private static Method findKeyGetKey() {
      Iterator var0 = List.of("getKey", "getDefaultKey").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = KeyMapping.class.getMethod(name);
            if (method.getReturnType() == InputConstants.Key.class) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = KeyMapping.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 0 && method.getReturnType() == InputConstants.Key.class) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static Method findKeySetKey() {
      Iterator var0 = List.of("setKey", "setKey").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = KeyMapping.class.getMethod(name, InputConstants.Key.class);
            if (method.getReturnType() == Void.TYPE) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = KeyMapping.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == InputConstants.Key.class && method.getReturnType() == Void.TYPE) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static Method findKeyIsDown() {
      Iterator var0 = List.of("isDown", "isDown").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = KeyMapping.class.getMethod(name);
            if (method.getReturnType() == Boolean.TYPE) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = KeyMapping.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 0 && method.getReturnType() == Boolean.TYPE) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static Method findKeyConsumeClick() {
      Iterator var0 = List.of("consumeClick", "click").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = KeyMapping.class.getMethod(name);
            if (method.getReturnType() == Boolean.TYPE) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = KeyMapping.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 0 && method.getReturnType() == Boolean.TYPE) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static Method findKeyGetName() {
      Iterator var0 = List.of("getName", "saveString").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = KeyMapping.class.getMethod(name);
            if (method.getReturnType() == String.class) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = KeyMapping.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 0 && method.getReturnType() == String.class) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static Method findKeyGetCategory() {
      Iterator var0 = List.of("getCategory", "release").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = KeyMapping.class.getMethod(name);
            if (method.getReturnType() == String.class) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = KeyMapping.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 0 && method.getReturnType() == String.class) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static Method findKeyGetType() {
      Iterator var0 = List.of("getType", "getType").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = InputConstants.Key.class.getMethod(name);
            if (method.getReturnType() == InputConstants.Type.class) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = InputConstants.Key.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 0 && method.getReturnType() == InputConstants.Type.class) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static Method findKeyGetValue() {
      Iterator var0 = List.of("getValue", "getValue").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = InputConstants.Key.class.getMethod(name);
            if (method.getReturnType() == Integer.TYPE) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = InputConstants.Key.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 0 && method.getReturnType() == Integer.TYPE) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static void install(KeyMapping keyMapping) {
      Minecraft mc = MLuaMinecraftClient.instance();
      if (options(mc) != null) {
         KeyMapping[] mappings = keyMappings(mc);
         KeyMapping[] updated = (KeyMapping[])Arrays.copyOf(mappings, mappings.length + 1);
         updated[mappings.length] = keyMapping;
         setKeyMappings(mc, updated);
         resetMappings();
      }
   }

   private static void refreshMappings() {
      Minecraft mc = MLuaMinecraftClient.instance();
      if (options(mc) != null) {
         resetMappings();
      }

   }

   private static void persist(KeyMapping keyMapping) {
      Minecraft mc = MLuaMinecraftClient.instance();
      Options options = options(mc);
      if (options != null) {
         options.setKey(keyMapping, key(keyMapping));
         options.save();
      }
   }

   private static KeyMapping[] keyMappings(Minecraft mc) {
      Options options = options(mc);
      if (options == null) {
         return new KeyMapping[0];
      } else {
         if (OPTIONS_KEY_MAPPINGS != null) {
            try {
               return (KeyMapping[])OPTIONS_KEY_MAPPINGS.get(options);
            } catch (Exception var3) {
            }
         }

         return new KeyMapping[0];
      }
   }

   private static void setKeyMappings(Minecraft mc, KeyMapping[] mappings) {
      Options options = options(mc);
      if (options == null) {
         throw new IllegalStateException("[MLua] Failed to resolve Minecraft options.");
      } else {
         if (OPTIONS_KEY_MAPPINGS != null) {
            try {
               OPTIONS_KEY_MAPPINGS.set(options, mappings);
               return;
            } catch (Exception var4) {
            }
         }

         throw new IllegalStateException("[MLua] Failed to update Minecraft key mappings.");
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
      Iterator var0 = List.of("resetMapping", "resetMapping").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = KeyMapping.class.getMethod(name);
            if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0 && method.getReturnType() == Void.TYPE) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = KeyMapping.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0 && method.getReturnType() == Void.TYPE) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   public static record Entry(String id, KeyMapping keyMapping) { }
}
