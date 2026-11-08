package dev.mlua.api;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.IdentityHashMap;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Converts Java service results and addon values into safe Lua values. */
final class MLuaJavaToLuaConverter {
   private MLuaJavaToLuaConverter() { }

   static LuaValue convert(Object value) {
      return convert(value, new IdentityHashMap<>(), 0, new int[] {0});
   }

   private static LuaValue convert(Object value, IdentityHashMap<Object, Boolean> active,
                                   int depth, int[] entryCount) {
      if (value == null) {
         return depth == 0 ? LuaValue.NIL : MLuaLuaCoercions.javaNullValue();
      } else if (value instanceof LuaValue luaValue) {
         validateLuaOutput(luaValue, active, depth, entryCount);
         return depth > 0 && luaValue.isnil() ? MLuaLuaCoercions.javaNullValue() : luaValue;
      } else if (value instanceof MLuaScopedJavaReference) {
         // Keep the lifetime guard in place. Lua-to-Java conversion unwraps it later.
         return LuaValue.userdataOf(value);
      } else if (MLuaLuaCoercions.hasAdapter(value.getClass())) {
         LuaValue adapted = toLuaRegisteredAdapter(value);
         validateLuaOutput(adapted, active, depth, entryCount);
         return depth > 0 && adapted.isnil() ? MLuaLuaCoercions.javaNullValue() : adapted;
      } else if (value instanceof Boolean bool) {
         return LuaValue.valueOf(bool);
      } else if (value instanceof Integer integer) {
         return LuaValue.valueOf(integer);
      } else if (value instanceof Long longValue) {
         try {
            return MLuaLuaCoercions.toLuaLong(longValue);
         } catch (IllegalArgumentException outsideLuaIntegerRange) {
            // Keep the exact integer instead of rounding it through LuaJ's double.
            return LuaValue.userdataOf(longValue);
         }
      } else if (value instanceof java.math.BigInteger integer) {
         java.math.BigInteger exactLimit = java.math.BigInteger.valueOf(MLuaLuaCoercions.MAX_EXACT_LUA_INTEGER_VALUE);
         if (integer.compareTo(exactLimit.negate()) >= 0 && integer.compareTo(exactLimit) <= 0) {
            return LuaValue.valueOf(integer.doubleValue());
         }
         return LuaValue.userdataOf(integer);
      } else if (value instanceof BigDecimal decimal) {
         double converted = decimal.doubleValue();
         BigDecimal roundTrip = BigDecimal.valueOf(converted);
         if (Double.isFinite(converted) && roundTrip.compareTo(decimal) == 0) {
            return LuaValue.valueOf(converted);
         }
         return LuaValue.userdataOf(decimal);
      } else if (value instanceof Short shortValue) {
         return LuaValue.valueOf(shortValue.intValue());
      } else if (value instanceof Byte byteValue) {
         return LuaValue.valueOf(byteValue.intValue());
      } else if (value instanceof Float floatValue) {
         return LuaValue.valueOf((double) floatValue);
      } else if (value instanceof Double doubleValue) {
         return LuaValue.valueOf(doubleValue);
      } else if (value instanceof Number number) {
         return convertNumber(number);
      } else if (value instanceof String string) {
         return LuaValue.valueOf(string);
      } else if (value instanceof Character character) {
         return LuaValue.valueOf(character.toString());
      } else if (value instanceof Enum<?> enumValue) {
         return LuaValue.valueOf(enumValue.name());
      } else if (value instanceof java.util.Optional<?> optional) {
         return optional.isPresent() ? convert(optional.get(), active, depth, entryCount)
            : depth == 0 ? LuaValue.NIL : MLuaLuaCoercions.javaNullValue();
      } else if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
         return convertCollection(value, active, depth, entryCount);
      }
      throw new IllegalArgumentException("No Lua conversion is registered for Java type "
         + value.getClass().getName() + "; register an MLuaLuaCoercions adapter or return a supported value");
   }

   private static LuaValue convertNumber(Number number) {
      try {
         double converted = number.doubleValue();
         BigDecimal exactLimit = BigDecimal.valueOf(MLuaLuaCoercions.MAX_EXACT_LUA_INTEGER_VALUE);
         BigDecimal decimal = new BigDecimal(number.toString());
         boolean integral = decimal.stripTrailingZeros().scale() <= 0;
         boolean integralOutOfRange = integral
            && (decimal.compareTo(exactLimit) > 0 || decimal.compareTo(exactLimit.negate()) < 0);
         if (Double.isFinite(converted)
               && !integralOutOfRange
               && decimal.compareTo(BigDecimal.valueOf(converted)) == 0) {
            return LuaValue.valueOf(converted);
         }
      } catch (RuntimeException ignored) {
         // Some Number implementations do not use a decimal string representation.
      }
      return LuaValue.userdataOf(number);
   }

   private static LuaValue convertCollection(Object value, IdentityHashMap<Object, Boolean> active,
                                             int depth, int[] entryCount) {
      if (depth >= MLuaLuaCoercions.MAX_CONVERSION_DEPTH) {
         throw new IllegalArgumentException("Java collection nesting exceeds the maximum depth of "
            + MLuaLuaCoercions.MAX_CONVERSION_DEPTH);
      }
      if (active.put(value, Boolean.TRUE) != null) {
         throw new IllegalArgumentException("Cyclic Java collection cannot be converted to a Lua table");
      }
      try {
         LuaTable table = new LuaTable();
         if (value instanceof Map<?, ?> map) {
            map.forEach((key, entry) -> {
               consumeEntry(entryCount);
               LuaValue luaKey = mapKey(key);
               if (!table.rawget(luaKey).isnil()) {
                  throw new IllegalArgumentException("Java map keys collapse to the same Lua key: " + key);
               }
               table.set(luaKey, convert(entry, active, depth + 1, entryCount));
            });
         } else if (value instanceof Iterable<?> iterable) {
            int index = 1;
            for (Object entry : iterable) {
               consumeEntry(entryCount);
               table.set(index++, convert(entry, active, depth + 1, entryCount));
            }
         } else {
            int length = Array.getLength(value);
            requireConversionEntries((long) entryCount[0] + length);
            for (int index = 0; index < length; index++) {
               consumeEntry(entryCount);
               table.set(index + 1, convert(Array.get(value, index), active, depth + 1, entryCount));
            }
         }
         return table;
      } finally {
         active.remove(value);
      }
   }

   private static void validateLuaOutput(LuaValue value, IdentityHashMap<Object, Boolean> active,
                                         int depth, int[] entryCount) {
      if (value == null || !value.istable()) return;
      LuaTable table = value.checktable();
      if (active.containsKey(table)) return;
      if (depth >= MLuaLuaCoercions.MAX_CONVERSION_DEPTH) {
         throw new IllegalArgumentException("Lua table nesting exceeds the maximum depth of "
            + MLuaLuaCoercions.MAX_CONVERSION_DEPTH);
      }
      active.put(table, Boolean.TRUE);
      try {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) return;
            consumeEntry(entryCount);
            validateLuaOutput(key, active, depth + 1, entryCount);
            validateLuaOutput(next.arg(2), active, depth + 1, entryCount);
         }
      } finally {
         active.remove(table);
      }
   }

   static LuaValue mapKey(Object key) {
      if (key == null) throw new IllegalArgumentException("Java map keys cannot be null in Lua tables");
      if (key instanceof String string) return LuaValue.valueOf(string);
      if (key instanceof Number number) {
         double converted = number.doubleValue();
         if (!Double.isFinite(converted)) {
            throw new IllegalArgumentException("Java map keys must be finite Lua numbers");
         }
         if (!(number instanceof Float || number instanceof Double)) {
            try {
               if (new BigDecimal(number.toString()).compareTo(BigDecimal.valueOf(converted)) != 0) {
                  throw new IllegalArgumentException("Java numeric map key " + number
                     + " cannot be represented exactly as a Lua number");
               }
            } catch (NumberFormatException invalidNumber) {
               throw new IllegalArgumentException("Java numeric map key " + number
                  + " cannot be represented as a Lua number", invalidNumber);
            }
         }
         return LuaValue.valueOf(converted);
      }
      if (key instanceof Boolean bool) return LuaValue.valueOf(bool);
      if (key instanceof Character character) return LuaValue.valueOf(character.toString());
      throw new IllegalArgumentException("Java map key type " + key.getClass().getName()
         + " cannot be preserved in a Lua table; use String, Number, Boolean, or Character keys");
   }

   private static void consumeEntry(int[] entryCount) {
      requireConversionEntries(++entryCount[0]);
   }

   private static void requireConversionEntries(long count) {
      if (count > MLuaLuaCoercions.MAX_CONVERSION_ENTRIES) {
         throw new IllegalArgumentException("Java value exceeds the maximum conversion size of "
            + MLuaLuaCoercions.MAX_CONVERSION_ENTRIES + " table entries");
      }
   }

   private static LuaValue toLuaRegisteredAdapter(Object value) {
      MLuaLuaCoercions.Adapter<?> adapter = MLuaLuaCoercions.adapterFor(value.getClass());
      if (adapter == null) {
         throw new IllegalStateException("Lua adapter for Java type " + value.getClass().getName()
            + " was removed while converting a service result");
      }
      @SuppressWarnings("unchecked")
      LuaValue converted = ((MLuaLuaCoercions.Adapter<Object>) adapter).toLua(value);
      if (converted == null) {
         throw new IllegalStateException("Lua adapter returned null for " + value.getClass().getName());
      }
      return converted;
   }
}
