package dev.mlua.modules.network.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.mlua.modules.network.MLuaJsonNull;
import java.util.IdentityHashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Converts Lua values to bounded JSON values and back for the Network service. */
final class MLuaNetworkJsonCodec {
   private static final Gson GSON = new Gson();
   private static final int MAX_JSON_DEPTH = 64;
   private static final int MAX_JSON_NODES = 100_000;
   private static final int MAX_JSON_SOURCE_LENGTH = 1_048_576;

   private MLuaNetworkJsonCodec() {
   }

   static String encode(LuaValue value) {
      try {
         return GSON.toJson(luaToJson(value, new IdentityHashMap<>(), 0, new int[]{0}));
      } catch (IllegalArgumentException failure) {
         throw new LuaError("[MLua] Network:JSONEncode " + failure.getMessage());
      }
   }

   static LuaValue decode(String text) {
      if (text == null) throw new LuaError("[MLua] Network:JSONDecode expects a JSON string");
      if (text.isBlank()) throw new LuaError("[MLua] Network:JSONDecode received empty JSON input");
      if (text.length() > MAX_JSON_SOURCE_LENGTH) {
         throw new LuaError("[MLua] Network:JSONDecode input exceeds the maximum size of "
            + MAX_JSON_SOURCE_LENGTH + " characters");
      }
      validateJsonNesting(text);
      try {
         return jsonToLua(com.google.gson.JsonParser.parseString(text), 0, new int[]{0});
      } catch (JsonParseException | IllegalStateException failure) {
         throw new LuaError("[MLua] Network JSONDecode received invalid JSON: " + failure.getMessage());
      }
   }

   private static LuaValue jsonToLua(JsonElement value, int depth, int[] nodes) {
      countJsonNode(depth, nodes, "JSONDecode");
      if (value == null || value.isJsonNull()) return LuaValue.userdataOf(MLuaJsonNull.INSTANCE);
      if (value.isJsonPrimitive()) {
         var primitive = value.getAsJsonPrimitive();
         if (primitive.isBoolean()) return LuaValue.valueOf(primitive.getAsBoolean());
         if (primitive.isNumber()) {
            double number = primitive.getAsDouble();
            if (!Double.isFinite(number)) throw new LuaError("[MLua] Network:JSONDecode number is outside Lua's finite number range");
            return LuaValue.valueOf(number);
         }
         return LuaValue.valueOf(primitive.getAsString());
      }
      if (value.isJsonArray()) {
         JsonArray array = value.getAsJsonArray();
         LuaTable result = new LuaTable(array.size(), 0);
         for (int index = 0; index < array.size(); index++) {
            result.set(index + 1, jsonToLua(array.get(index), depth + 1, nodes));
         }
         return result;
      }
      JsonObject object = value.getAsJsonObject();
      LuaTable result = new LuaTable(0, object.size());
      for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
         result.set(entry.getKey(), jsonToLua(entry.getValue(), depth + 1, nodes));
      }
      return result;
   }

   private static JsonElement luaToJson(LuaValue value, IdentityHashMap<LuaTable, Boolean> active,
         int depth, int[] nodes) {
      countJsonNode(depth, nodes, "JSONEncode");
      if (value == null || value.isnil()) return JsonNull.INSTANCE;
      int valueType = value.type();
      if (valueType == LuaValue.TUSERDATA && value.isuserdata(MLuaJsonNull.class)) return JsonNull.INSTANCE;
      if (valueType == LuaValue.TBOOLEAN) return new com.google.gson.JsonPrimitive(value.toboolean());
      if (valueType == LuaValue.TNUMBER) {
         double number = value.todouble();
         if (!Double.isFinite(number)) throw new IllegalArgumentException("cannot encode a non-finite number");
         return new com.google.gson.JsonPrimitive(number);
      }
      if (valueType == LuaValue.TSTRING) return new com.google.gson.JsonPrimitive(value.tojstring());
      if (valueType != LuaValue.TTABLE) {
         throw new IllegalArgumentException("cannot encode Lua " + value.typename()
            + "; expected nil, JSONNull, boolean, finite number, string, or table");
      }

      LuaTable table = value.checktable();
      if (active.put(table, Boolean.TRUE) != null) {
         throw new IllegalArgumentException("cannot encode a cyclic Lua table");
      }
      try {
         boolean numericKeys = true;
         int count = 0;
         int largestIndex = 0;
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            count++;
            if (count > MAX_JSON_NODES) {
               throw new IllegalArgumentException("table exceeds the maximum of " + MAX_JSON_NODES + " entries");
            }
            if (key.type() != LuaValue.TNUMBER) {
               numericKeys = false;
               continue;
            }
            double number = key.todouble();
            if (!Double.isFinite(number) || number < 1 || number > Integer.MAX_VALUE
                  || number != Math.rint(number)) {
               throw new IllegalArgumentException("table numeric keys must be positive consecutive integers");
            }
            largestIndex = Math.max(largestIndex, (int)number);
         }

         if (count == 0 || numericKeys && count == largestIndex) {
            JsonArray array = new JsonArray();
            for (int index = 1; index <= largestIndex; index++) {
               array.add(luaToJson(table.rawget(index), active, depth + 1, nodes));
            }
            return array;
         }
         if (numericKeys) {
            throw new IllegalArgumentException("array table keys must be consecutive integers starting at 1");
         }

         JsonObject object = new JsonObject();
         key = LuaValue.NIL;
         while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (key.type() != LuaValue.TSTRING) {
               throw new IllegalArgumentException("object table keys must all be strings");
            }
            object.add(key.tojstring(), luaToJson(entry.arg(2), active, depth + 1, nodes));
         }
         return object;
      } finally {
         active.remove(table);
      }
   }

   private static void countJsonNode(int depth, int[] nodes, String operation) {
      if (depth > MAX_JSON_DEPTH) {
         throw new LuaError("[MLua] Network:" + operation + " nesting exceeds " + MAX_JSON_DEPTH + " levels");
      }
      if (++nodes[0] > MAX_JSON_NODES) {
         throw new LuaError("[MLua] Network:" + operation + " exceeds " + MAX_JSON_NODES + " values");
      }
   }

   private static void validateJsonNesting(String text) {
      int depth = 0;
      boolean inString = false;
      boolean escaped = false;
      for (int index = 0; index < text.length(); index++) {
         char current = text.charAt(index);
         if (inString) {
            if (escaped) escaped = false;
            else if (current == '\\') escaped = true;
            else if (current == '"') inString = false;
            continue;
         }
         if (current == '"') inString = true;
         else if (current == '{' || current == '[') {
            if (++depth > MAX_JSON_DEPTH) {
               throw new LuaError("[MLua] Network:JSONDecode nesting exceeds " + MAX_JSON_DEPTH + " levels");
            }
         } else if ((current == '}' || current == ']') && depth > 0) {
            depth--;
         }
      }
   }

}
