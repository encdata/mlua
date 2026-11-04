package dev.mlua.modules.registry.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.mlua.runtime.registry.MLuaContentTypeRegistry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.IdentityHashMap;
import java.util.Map;

/** Converts Lua values and JSON text into bounded Minecraft data definitions. */
final class MLuaLuaJson {
   private static final int MAX_GENERATED_JSON_ENTRIES = 16_384;
   private static final int MAX_GENERATED_JSON_CHARACTERS = 1_048_576;

   private MLuaLuaJson() { }

   static JsonObject objectArg(LuaValue value, String type) {
      JsonElement element;
      if (value.type() == LuaValue.TSTRING) {
         String source = value.checkjstring();
         if (source.length() > MAX_GENERATED_JSON_CHARACTERS) {
            throw new LuaError("[MLua] " + type + " JSON exceeds " + MAX_GENERATED_JSON_CHARACTERS + " characters.");
         }
         validateJsonTextNesting(source);
         try {
            element = JsonParser.parseString(source);
         } catch (JsonParseException error) {
            throw new LuaError("[MLua] Invalid " + type + " JSON: " + error.getMessage());
         }
         validateJsonLimits(element, 0, new JsonConversionBudget());
      } else {
         element = fromLua(value);
      }
      if (!element.isJsonObject()) {
         throw new LuaError("[MLua] " + type + " definition must be a table or JSON object");
      }

      return element.getAsJsonObject();
   }

   /** Rejects excessively nested input before Gson builds a recursive tree. */
   private static void validateJsonTextNesting(String source) {
      int depth = 0;
      char[] delimiters = new char[MLuaContentTypeRegistry.MAX_TABLE_DEPTH];
      boolean inString = false;
      boolean escaped = false;
      for (int index = 0; index < source.length(); index++) {
         char character = source.charAt(index);
         if (inString) {
            if (escaped) escaped = false;
            else if (character == '\\') escaped = true;
            else if (character == '"') inString = false;
            continue;
         }
         if (character == '"') inString = true;
         else if (character == '{' || character == '[') {
            if (++depth > MLuaContentTypeRegistry.MAX_TABLE_DEPTH) {
               throw new LuaError("[MLua] Generated JSON nesting exceeds "
                  + MLuaContentTypeRegistry.MAX_TABLE_DEPTH + " tables.");
            }
            delimiters[depth - 1] = character;
         } else if (character == '}' || character == ']') {
            if (depth > 0 && (character == '}' && delimiters[depth - 1] == '{'
                  || character == ']' && delimiters[depth - 1] == '[')) depth--;
         }
      }
   }

   private static void validateJsonLimits(JsonElement element, int depth, JsonConversionBudget budget) {
      if (element == null || element.isJsonNull()) return;
      if (element.isJsonPrimitive()) {
         if (element.getAsJsonPrimitive().isString()) budget.addCharacters(element.getAsString().length());
         return;
      }
      if (!element.isJsonArray() && !element.isJsonObject()) return;
      if (depth >= MLuaContentTypeRegistry.MAX_TABLE_DEPTH) {
         throw new LuaError("[MLua] Generated JSON nesting exceeds "
            + MLuaContentTypeRegistry.MAX_TABLE_DEPTH + " tables.");
      }
      if (element.isJsonArray()) {
         for (JsonElement child : element.getAsJsonArray()) {
            budget.addEntry();
            validateJsonLimits(child, depth + 1, budget);
         }
      } else {
         for (Map.Entry<String, JsonElement> child : element.getAsJsonObject().entrySet()) {
            budget.addEntry();
            budget.addCharacters(child.getKey().length());
            validateJsonLimits(child.getValue(), depth + 1, budget);
         }
      }
   }

   static JsonElement fromLua(LuaValue value) {
      return fromLua(value, new IdentityHashMap<>(), 0, new JsonConversionBudget());
   }

   private static JsonElement fromLua(LuaValue value, IdentityHashMap<LuaTable, Boolean> activeTables,
                                        int depth, JsonConversionBudget budget) {
      if (value.isnil()) {
         return JsonNull.INSTANCE;
      }
      if (value.type() == LuaValue.TBOOLEAN) {
         return new JsonPrimitive(value.toboolean());
      }
      if (value.type() == LuaValue.TNUMBER) {
         double numeric = value.todouble();
         if (!Double.isFinite(numeric)) throw new LuaError("[MLua] Generated JSON cannot contain NaN or infinite numbers.");
         long integral = value.tolong();
         if (numeric == (double)integral && numeric >= Long.MIN_VALUE && numeric < 0x1.0p63) {
            return new JsonPrimitive(integral);
         }
         return new JsonPrimitive(numeric);
      }
      if (value.type() == LuaValue.TSTRING) {
         budget.addCharacters(value.tojstring().length());
         return new JsonPrimitive(value.tojstring());
      }
      if (!value.istable()) {
         throw new LuaError("[MLua] Unsupported JSON value type: " + value.typename());
      }

      LuaTable table = value.checktable();
      if (depth >= MLuaContentTypeRegistry.MAX_TABLE_DEPTH) {
         throw new LuaError("[MLua] Generated JSON nesting exceeds " + MLuaContentTypeRegistry.MAX_TABLE_DEPTH + " tables.");
      }
      if (activeTables.put(table, Boolean.TRUE) != null) {
         throw new LuaError("[MLua] Generated JSON cannot contain cyclic Lua tables.");
      }
      try {
         int arrayLength = jsonArrayLength(table, budget);
         if (arrayLength >= 0) {
            JsonArray array = new JsonArray();
            for (int i = 1; i <= arrayLength; i++) {
               array.add(fromLua(table.get(i), activeTables, depth + 1, budget));
            }
            return array;
         }

         JsonObject object = new JsonObject();
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) return object;
            if (key.type() != LuaValue.TSTRING) {
               throw new LuaError("[MLua] Generated JSON objects must use string keys; got " + key.typename());
            }
            object.add(key.tojstring(), fromLua(next.arg(2), activeTables, depth + 1, budget));
         }
      } finally {
         activeTables.remove(table);
      }
   }

   /** Returns a dense one-based array length, or -1 for a string-keyed JSON object. */
   static int jsonArrayLength(LuaTable table) {
      return jsonArrayLength(table, new JsonConversionBudget());
   }

   private static int jsonArrayLength(LuaTable table, JsonConversionBudget budget) {
      LuaValue key = LuaValue.NIL;
      int count = 0;
      int maximum = 0;
      boolean hasNumberKeys = false;
      boolean hasStringKeys = false;
      while (true) {
         Varargs entry = table.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         count++;
         budget.addEntry();
         if (key.type() == LuaValue.TSTRING) {
            hasStringKeys = true;
            budget.addCharacters(key.tojstring().length());
         } else if (key.type() == LuaValue.TNUMBER) {
            hasNumberKeys = true;
            double index = key.todouble();
            if (!Double.isFinite(index) || index < 1 || index > Integer.MAX_VALUE || index != Math.rint(index)) {
               throw new LuaError("[MLua] Generated JSON array indexes must be positive whole numbers.");
            }
            maximum = Math.max(maximum, (int)index);
         } else {
            throw new LuaError("[MLua] Generated JSON table keys must be strings or positive array indexes; got "
               + key.typename());
         }
      }
      if (hasNumberKeys && hasStringKeys) {
         throw new LuaError("[MLua] Generated JSON tables cannot mix object keys and array indexes.");
      }
      if (!hasNumberKeys) return -1;
      if (maximum != count) throw new LuaError("[MLua] Generated JSON arrays must be dense and start at index one.");
      return maximum;
   }

   private static final class JsonConversionBudget {
      private int entries;
      private long characters;

      private void addEntry() {
         if (++this.entries > MAX_GENERATED_JSON_ENTRIES) {
            throw new LuaError("[MLua] Generated JSON exceeds the limit of "
               + MAX_GENERATED_JSON_ENTRIES + " total table entries.");
         }
      }

      private void addCharacters(long count) {
         this.characters += count;
         if (this.characters > MAX_GENERATED_JSON_CHARACTERS) {
            throw new LuaError("[MLua] Generated JSON string content exceeds "
               + MAX_GENERATED_JSON_CHARACTERS + " characters.");
         }
      }
   }

}
