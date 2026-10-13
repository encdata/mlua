package dev.mlua.minecraft.binding;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class MLuaKeyCatalog {
   private static final Map<String, LuaTable> KEYS_BY_NAME = new LinkedHashMap();
   private static final Map<String, LuaTable> KEYS_BY_VALUE = new LinkedHashMap();
   private MLuaKeyCatalog() {
   }

   public static synchronized LuaTable keysTable() {
      if (KEYS_BY_NAME.isEmpty()) {
         loadKeys();
      }

      LuaTable table = new LuaTable();
      Iterator var1 = KEYS_BY_NAME.entrySet().iterator();

      while(var1.hasNext()) {
         Map.Entry<String, LuaTable> entry = (Map.Entry)var1.next();
         table.set((String)entry.getKey(), (LuaValue)entry.getValue());
      }

      return table;
   }

   public static synchronized LuaValue valueOf(LuaValue value) {
      if (value != null && !value.isnil()) {
         LuaTable key;
         if (value.istable()) {
            key = value.checktable();
            LuaValue raw = key.get("Value");
            if (!raw.isnil()) {
               return raw;
            }

            LuaValue name = key.get("Name");
            if (!name.isnil()) {
               LuaTable mappedKey = (LuaTable)KEYS_BY_NAME.get(name.tojstring());
               if (mappedKey != null) {
                  return mappedKey.get("Value");
               }
            }
         }

         if (value.isstring()) {
            key = (LuaTable)KEYS_BY_VALUE.get(value.tojstring());
            if (key != null) {
               return key.get("Value");
            }
         }

         return value;
      } else {
         return LuaValue.NIL;
      }
   }

   public static synchronized List<KeyEntry> entries() {
      if (KEYS_BY_NAME.isEmpty()) {
         loadKeys();
      }

      List<KeyEntry> out = new ArrayList();
      Iterator var1 = KEYS_BY_NAME.entrySet().iterator();

      while(var1.hasNext()) {
         Map.Entry<String, LuaTable> entry = (Map.Entry)var1.next();
         LuaTable value = (LuaTable)entry.getValue();
         out.add(new KeyEntry((String)entry.getKey(), value.get("Value").tojstring(), value.get("Type").tojstring(), value.get("Code").toint()));
      }

      out.sort(Comparator.comparing(KeyEntry::name));
      return out;
   }

   private static void loadKeys() {
      Map<Integer, String> keyboard = new TreeMap();
      Map<Integer, String> mouse = new TreeMap();

      try {
         Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
         Field[] var3 = glfw.getFields();
         int var4 = var3.length;

         for(int var5 = 0; var5 < var4; ++var5) {
            Field field = var3[var5];
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Integer.TYPE) {
               String name = field.getName();
               if (name.startsWith("GLFW_KEY_")) {
                  keyboard.put(field.getInt((Object)null), name.substring("GLFW_KEY_".length()));
               } else if (name.startsWith("GLFW_MOUSE_BUTTON_")) {
                  mouse.put(field.getInt((Object)null), name.substring("GLFW_MOUSE_BUTTON_".length()));
               }
            }
         }
      } catch (Exception var8) {
      }

      Iterator var9 = keyboard.entrySet().iterator();

      Map.Entry entry;
      while(var9.hasNext()) {
         entry = (Map.Entry)var9.next();
         addKey("Keyboard", (String)entry.getValue(), (Integer)entry.getKey(), keyValue("Keyboard", (String)entry.getValue()));
      }

      var9 = mouse.entrySet().iterator();

      while(var9.hasNext()) {
         entry = (Map.Entry)var9.next();
         addKey("Mouse", (String)entry.getValue(), (Integer)entry.getKey(), keyValue("Mouse", (String)entry.getValue()));
      }

      if (KEYS_BY_NAME.isEmpty()) {
         addKey("Keyboard", "Space", 32, keyValue("Keyboard", "Space"));
         addKey("Keyboard", "LeftShift", 340, keyValue("Keyboard", "LeftShift"));
         addKey("Keyboard", "LeftControl", 341, keyValue("Keyboard", "LeftControl"));
         addKey("Keyboard", "Escape", 256, keyValue("Keyboard", "Escape"));
         addKey("Keyboard", "Enter", 257, keyValue("Keyboard", "Enter"));
         addKey("Mouse", "Left", 0, keyValue("Mouse", "Left"));
         addKey("Mouse", "Right", 1, keyValue("Mouse", "Right"));
      }

   }

   private static void addKey(String group, String suffix, int code, String key) {
      String name = normalizeName(group, suffix);
      LuaTable table = new LuaTable();
      table.set("Name", name);
      table.set("Value", key);
      table.set("Type", group);
      table.set("Code", LuaValue.valueOf(code));
      LuaTable meta = new LuaTable();
      meta.set("__isKey", LuaValue.TRUE);
      meta.set("__keyName", name);
      meta.set("__keyValue", key);
      table.setmetatable(meta);
      KEYS_BY_NAME.put(name, table);
      KEYS_BY_VALUE.put(key, table);
   }

   private static String keyValue(String group, String suffix) {
      String normalized = suffix.toLowerCase(Locale.ROOT);
      if ("mouse".equalsIgnoreCase(group)) return "key.mouse." + normalized;
      if (normalized.startsWith("kp_")) return "key.keyboard.keypad." + normalized.substring(3);
      return "key.keyboard." + normalized;
   }

   private static String normalizeName(String group, String suffix) {
      String raw = suffix.toLowerCase(Locale.ROOT);
      if ("mouse".equalsIgnoreCase(group)) {
         return "Mouse" + pascal(raw);
      } else {
         return raw.startsWith("kp_") ? "NumPad" + pascal(raw.substring(3)) : pascal(raw);
      }
   }

   private static String pascal(String raw) {
      if (raw != null && !raw.isBlank()) {
         StringBuilder out = new StringBuilder();
         String[] var2 = raw.split("_");
         int var3 = var2.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String part = var2[var4];
            if (!part.isEmpty()) {
               String lower = part.toLowerCase(Locale.ROOT);
               out.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
         }

         return out.toString();
      } else {
         return raw;
      }
   }

   public static record KeyEntry(String name, String value, String type, int code) {
      public KeyEntry(String name, String value, String type, int code) {
         this.name = name;
         this.value = value;
         this.type = type;
         this.code = code;
      }

      public String name() {
         return this.name;
      }

      public String value() {
         return this.value;
      }

      public String type() {
         return this.type;
      }

      public int code() {
         return this.code;
      }
   }
}
