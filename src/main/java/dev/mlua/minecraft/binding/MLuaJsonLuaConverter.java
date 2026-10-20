package dev.mlua.minecraft.binding;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Converts a detached Gson JSON tree into fresh Lua tables and values. */
public final class MLuaJsonLuaConverter {
    private MLuaJsonLuaConverter() { }

    public static LuaTable toLua(JsonObject object) {
        return (LuaTable) toLuaValue(object);
    }

    private static LuaValue toLuaValue(JsonElement element) {
        if (element == null || element.isJsonNull()) return LuaValue.NIL;
        if (element.isJsonPrimitive()) {
            JsonPrimitive value = element.getAsJsonPrimitive();
            if (value.isBoolean()) return LuaValue.valueOf(value.getAsBoolean());
            if (value.isNumber()) return LuaValue.valueOf(value.getAsNumber().doubleValue());
            return LuaValue.valueOf(value.getAsString());
        }
        if (element.isJsonArray()) {
            LuaTable result = new LuaTable();
            var values = element.getAsJsonArray();
            for (int index = 0; index < values.size(); index++) {
                result.set(index + 1, toLuaValue(values.get(index)));
            }
            return result;
        }
        LuaTable result = new LuaTable();
        element.getAsJsonObject().entrySet().forEach(entry -> result.set(entry.getKey(), toLuaValue(entry.getValue())));
        return result;
    }
}
