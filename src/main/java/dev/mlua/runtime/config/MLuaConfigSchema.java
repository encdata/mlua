package dev.mlua.runtime.config;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.runtime.config.MLuaConfigRegistry.ConfigValue;

/** Parses and validates Lua config schemas before the registry installs a section. */
final class MLuaConfigSchema {
   private MLuaConfigSchema() { }

   private static record ConfigDescriptor(List<String> path, String typeName, Object defaultValue,
                                         Double minimum, Double maximum, List<String> options,
                                         String description) { }

   static void defineEntries(Map<String, ConfigValue> values, List<String> path, LuaTable table) {
      LuaValue key = LuaValue.NIL;

      while(true) {
         Varargs next = table.next(key);
         key = next.arg1();
         if (key.isnil()) {
            return;
         }

         LuaValue value = next.arg(2);
         String name = key.checkjstring();
         ArrayList<String> childPath = new ArrayList(path);
         childPath.add(name);
         if (value.istable() && !isValueDescriptor(value.checktable())) {
            defineEntries(values, childPath, value.checktable());
         } else {
            ConfigDescriptor descriptor = descriptorFor(value, childPath);
            values.put(String.join(".", childPath), new ConfigValue(descriptor.typeName(), descriptor.defaultValue(),
               descriptor.minimum(), descriptor.maximum(), descriptor.options(), descriptor.description()));
         }
      }
   }

   static void validateConfigTable(LuaTable table,
                                   IdentityHashMap<LuaTable, Boolean> active,
                                   IdentityHashMap<LuaTable, Boolean> visited,
                                   int depth) {
      if (depth > 64) throw new LuaError("[MLua] Config definitions cannot be nested more than 64 tables deep.");
      if (active.containsKey(table)) throw new LuaError("[MLua] Config definitions cannot contain cyclic tables.");
      if (visited.containsKey(table)) return;
      active.put(table, Boolean.TRUE);
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = table.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         LuaValue value = entry.arg(2);
         if (value.istable()) {
            validateConfigTable(value.checktable(), active, visited, depth + 1);
         }
      }
      active.remove(table);
      visited.put(table, Boolean.TRUE);
   }

   private static boolean isValueDescriptor(LuaTable table) {
      return !table.get("default").isnil() || !table.get("Default").isnil() || !table.get("value").isnil() || !table.get("Value").isnil();
   }

   private static ConfigDescriptor descriptorFor(LuaValue value, List<String> path) {
      if (value.istable() && isValueDescriptor(value.checktable())) {
         LuaTable table = value.checktable();
         LuaValue defaultValue = first(table, "default", "Default", "value", "Value");
         if (defaultValue.isnil()) {
            throw new LuaError("[MLua] Config '" + String.join(".", path) + "' is missing a default value.");
         } else {
            List<String> options = readOptions(table, path);
            String description = readOptionalString(table, "description", "Description");
            LuaValue explicitType = first(table, "type", "Type");
            String typeName = explicitType.isnil() && !options.isEmpty()
               ? "enum" : readTypeName(table, defaultValue, path);
            Object converted = convertDefault(defaultValue, path, typeName);
            Double min = readOptionalNumber(table, "min", "Min");
            Double max = readOptionalNumber(table, "max", "Max");
            validateRangeUsage(path, typeName, min, max);
            validateOptions(path, typeName, converted, options);
            if (!MLuaConfigRegistry.isWithinRange(converted, min, max)) {
               throw new LuaError("[MLua] Config '" + String.join(".", path)
                  + "' default must be " + MLuaConfigRegistry.rangeDescription(min, max) + ".");
            }
            return new ConfigDescriptor(path, typeName, converted, min, max, options, description);
         }
      } else {
         String typeName = inferTypeName(value, path);
         return new ConfigDescriptor(path, typeName, convertDefault(value, path, typeName), null, null, List.of(), null);
      }
   }

   private static void validateRangeUsage(List<String> path, String typeName, Double min, Double max) {
      if ((min != null || max != null) && !List.of("int", "long", "double").contains(typeName)) {
         throw new LuaError("[MLua] Config '" + String.join(".", path) + "' only supports Min/Max on numeric types.");
      }
      if (min != null && max != null && min > max) {
         throw new LuaError("[MLua] Config '" + String.join(".", path) + "' Min cannot be greater than Max.");
      }
      if ("int".equals(typeName) || "long".equals(typeName)) {
         if ((min != null && min != Math.rint(min)) || (max != null && max != Math.rint(max))) {
            throw new LuaError("[MLua] Config '" + String.join(".", path) + "' requires whole-number Min/Max bounds.");
         }
         double lower = "int".equals(typeName) ? Integer.MIN_VALUE : Long.MIN_VALUE;
         double upperExclusive = "int".equals(typeName) ? (double)Integer.MAX_VALUE + 1.0 : 0x1.0p63;
         if ((min != null && min < lower) || (max != null && max >= upperExclusive)) {
            throw new LuaError("[MLua] Config '" + String.join(".", path) + "' Min/Max exceeds the " + typeName + " range.");
         }
      }
   }

   private static LuaValue first(LuaTable table, String... keys) {
      for (String key : keys) {
         LuaValue value = table.get(key);
         if (!value.isnil()) {
            return value;
         }
      }

      return LuaValue.NIL;
   }

   private static Double readOptionalNumber(LuaTable table, String... keys) {
      LuaValue value = first(table, keys);
      if (value.isnil()) return null;
      double number = value.checkdouble();
      if (!Double.isFinite(number)) throw new LuaError("[MLua] Config Min and Max values must be finite numbers.");
      return number;
   }

   private static String readOptionalString(LuaTable table, String... keys) {
      LuaValue value = first(table, keys);
      if (value.isnil()) return null;
      if (!value.isstring()) throw new LuaError("[MLua] Config Description must be a string.");
      String description = value.tojstring();
      return description.isBlank() ? null : description;
   }

   private static List<String> readOptions(LuaTable definition, List<String> path) {
      LuaValue value = first(definition, "options", "Options");
      if (value.isnil()) return List.of();
      if (!value.istable()) throw new LuaError("[MLua] Config '" + String.join(".", path) + "' Options must be a string array.");
      LuaTable table = value.checktable();
      int length = table.length();
      if (length == 0) throw new LuaError("[MLua] Config '" + String.join(".", path) + "' Options must not be empty.");
      List<String> options = new ArrayList<>(length);
      java.util.HashSet<String> unique = new java.util.HashSet<>();
      for (int index = 1; index <= length; index++) {
         LuaValue option = table.get(index);
         if (!option.isstring() || option.tojstring().isBlank()) {
            throw new LuaError("[MLua] Config '" + String.join(".", path) + "' Options must contain non-empty strings.");
         }
         String text = option.tojstring();
         if (!unique.add(text)) throw new LuaError("[MLua] Config '" + String.join(".", path) + "' Options contains duplicate '" + text + "'.");
         options.add(text);
      }
      if (table.keys().length != length) {
         throw new LuaError("[MLua] Config '" + String.join(".", path) + "' Options must be a dense string array.");
      }
      return List.copyOf(options);
   }

   private static void validateOptions(List<String> path, String typeName, Object defaultValue, List<String> options) {
      if (options.isEmpty()) {
         if ("enum".equals(typeName)) {
            throw new LuaError("[MLua] Config '" + String.join(".", path) + "' enum values require Options.");
         }
         return;
      }
      if (!"enum".equals(typeName)) {
         throw new LuaError("[MLua] Config '" + String.join(".", path) + "' Options require Type = 'enum'.");
      }
      if (!(defaultValue instanceof String text) || !options.contains(text)) {
         throw new LuaError("[MLua] Config '" + String.join(".", path) + "' default must match one of its Options.");
      }
   }

   private static Object convertDefault(LuaValue value, List<String> path, String typeName) {
      return switch (typeName) {
         case "boolean" -> {
            if (!value.isboolean()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a boolean default.");
            }
            yield value.toboolean();
         }
         case "int" -> {
            if (!value.isnumber()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a numeric default.");
            }
            double number = value.todouble();
            if (!Double.isFinite(number) || number != Math.rint(number)
                  || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' requires a finite whole-number default within the integer range.");
            }
            yield (int)number;
         }
         case "long" -> {
            if (!value.isnumber()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a numeric default.");
            }
            double number = value.todouble();
            if (!Double.isFinite(number) || number != Math.rint(number)
                  || number < Long.MIN_VALUE || number >= 0x1.0p63) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' requires a finite whole-number default within the long range.");
            }
            yield (long)number;
         }
         case "double" -> {
            if (!value.isnumber()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a numeric default.");
            }
            double number = value.todouble();
            if (!Double.isFinite(number)) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' requires a finite numeric default.");
            }
            yield number;
         }
         case "string" -> {
            if (!value.isstring()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a string default.");
            }
            yield value.tojstring();
         }
         case "enum" -> {
            if (!value.isstring()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a string enum default.");
            }
            yield value.tojstring();
         }
         default -> throw new LuaError("[MLua] Unsupported config type '" + typeName + "' for '" + String.join(".", path) + "'.");
      };
   }

   private static String readTypeName(LuaTable table, LuaValue defaultValue, List<String> path) {
      LuaValue explicit = first(table, "type", "Type");
      return explicit.isnil() ? inferTypeName(defaultValue, path) : normalizeTypeName(explicit.checkjstring(), path);
   }

   private static String inferTypeName(LuaValue value, List<String> path) {
      if (value.isboolean()) {
         return "boolean";
      } else if (value.isnumber()) {
         double number = value.todouble();
         return Math.rint(number) == number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE ? "int" : "double";
      } else if (value.isstring()) {
         return "string";
      } else {
         throw new LuaError("[MLua] Config '" + String.join(".", path) + "' must use boolean, number, or string defaults.");
      }
   }

   private static String normalizeTypeName(String raw, List<String> path) {
      String normalized = raw.toLowerCase(Locale.ROOT);
      return switch (normalized) {
         case "bool", "boolean" -> "boolean";
         case "int", "integer" -> "int";
         case "long" -> "long";
         case "float", "double", "number" -> "double";
         case "string" -> "string";
         case "enum" -> "enum";
         default -> throw new LuaError("[MLua] Config '" + String.join(".", path) + "' has unknown Type '" + raw + "'.");
      };
   }
}
