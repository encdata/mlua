package dev.mlua.modules.world;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Converts Lua writes to the value type currently stored by a Minecraft gamerule. */
final class MLuaWorldGameRuleValue {
    private MLuaWorldGameRuleValue() { }

    static Object convert(String name, Object current, LuaValue value) {
        if (current instanceof Boolean) {
            if (!value.isboolean()) {
                throw new LuaError("[MLua] Gamerule " + name + " requires a boolean value.");
            }
            return value.toboolean();
        }
        if (current instanceof Number) {
            if (!value.isnumber()) {
                throw new LuaError("[MLua] Gamerule " + name + " requires a number value.");
            }
            double number = value.todouble();
            if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new LuaError("[MLua] Gamerule " + name
                    + " requires a whole number within the 32-bit integer range.");
            }
            return (int) number;
        }
        if (current instanceof String) {
            if (!value.isstring()) {
                throw new LuaError("[MLua] Gamerule " + name + " requires a string value.");
            }
            return value.tojstring();
        }
        throw new LuaError("[MLua] Unsupported gamerule type: " + name);
    }
}
