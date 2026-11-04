package dev.mlua.modules.enums;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Function;
import dev.mlua.common.enums.MLuaTypedEnumValue;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaEnumValue {
   private static final ClassValue<Map<String, LuaTable>> ENUM_REGISTRY = new ClassValue<>() {
      @Override protected Map<String, LuaTable> computeValue(Class<?> type) { return new HashMap<>(); }
   };

   private static final class EnumTable extends LuaTable implements MLuaTypedEnumValue {
      private final Class<?> enumClass;
      private final String name;
      private boolean sealed;

      private EnumTable(Class<?> enumClass, String name) {
         this.enumClass = enumClass;
         this.name = name;
      }

      private void requireWritable() {
         if (this.sealed) throw new LuaError("MLua enum values are read-only");
      }

      private void seal() { this.sealed = true; }

      @Override public Class<?> enumClass() { return this.enumClass; }
      @Override public String enumName() { return this.name; }

      @Override public void set(int key, LuaValue value) { requireWritable(); super.set(key, value); }
      @Override public void set(LuaValue key, LuaValue value) { requireWritable(); super.set(key, value); }
      @Override public void rawset(int key, LuaValue value) { requireWritable(); super.rawset(key, value); }
      @Override public void rawset(LuaValue key, LuaValue value) { requireWritable(); super.rawset(key, value); }
      @Override public LuaValue remove(int pos) { requireWritable(); return super.remove(pos); }
      @Override public void insert(int pos, LuaValue value) { requireWritable(); super.insert(pos, value); }
      @Override public void hashset(LuaValue key, LuaValue value) { requireWritable(); super.hashset(key, value); }
      @Override public void sort(LuaValue comparator) { requireWritable(); super.sort(comparator); }
      @Override public LuaValue setmetatable(LuaValue metatable) { requireWritable(); return super.setmetatable(metatable); }

      @Override
      public String tojstring() { return this.enumClass.getSimpleName() + "." + this.name; }
   }

   private MLuaEnumValue() {
   }

   public static <T extends Enum<T>> LuaTable create(Class<T> enumClass, T value) {
      EnumTable table = new EnumTable(enumClass, value.name());
      table.rawset("Name", LuaValue.valueOf(value.name()));
      table.rawset("Value", LuaValue.valueOf(value.toString()));
      LuaTable metatable = new LuaTable();
      metatable.set("__isEnum", LuaValue.TRUE);
      metatable.set("__enumClass", LuaValue.valueOf(enumClass.getName()));
      table.setmetatable(metatable);
      table.seal();
      return table;
   }

   public static <T extends Enum<T>> T toJava(LuaValue value, Class<T> enumClass) {
      if (!value.istable() || !(value.checktable() instanceof EnumTable enumValue)
            || enumValue.enumClass != enumClass) {
         return null;
      }
      return Enum.valueOf(enumClass, enumValue.name);
   }

   public static boolean isEnum(LuaValue value, Class<?> enumClass) {
      return value.istable() && value.checktable() instanceof EnumTable enumValue
            && enumValue.enumClass == enumClass;
   }

   public static synchronized <T extends Enum<T>> LuaTable forJava(T value) {
      Class<T> enumClass = value.getDeclaringClass();
      Map<String, LuaTable> classMap = ENUM_REGISTRY.get(enumClass);
      return classMap.computeIfAbsent(value.name(), ignored -> create(enumClass, value));
   }

   public static <T extends Enum<T>> LuaTable enumConstants(Class<T> enumClass, Function<T, Boolean> filter) {
      LuaTable constants = new LuaTable();
      for (T value : enumClass.getEnumConstants()) {
         if (filter == null || filter.apply(value)) constants.set(displayName(value.name()), forJava(value));
      }
      return constants;
   }

   private static String displayName(String enumName) {
      if (enumName == null || enumName.isEmpty()) return enumName;
      StringBuilder result = new StringBuilder();
      for (String part : enumName.split("_")) {
         String lower = part.toLowerCase();
         result.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
      }
      return result.toString();
   }
}
