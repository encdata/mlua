package dev.mlua.minecraft.animation;

import dev.mlua.minecraft.binding.MLuaLuaArguments;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Shared Lua options for Blockbench model animation playback. */
public record MLuaAnimationOptions(boolean loop, float speed) {
    public static MLuaAnimationOptions fromLua(LuaValue value, String method) {
        LuaTable options;
        if (value.isnil()) {
            options = new LuaTable();
        } else if (value.istable()) {
            options = value.checktable();
        } else {
            throw new LuaError("[MLua] " + method + " options must be a table.");
        }

        LuaValue loopValue = options.rawget("Loop");
        LuaValue lowerLoopValue = options.rawget("loop");
        if (!loopValue.isnil() && !lowerLoopValue.isnil()) {
            throw new LuaError("[MLua] " + method + " Loop and loop are aliases; provide only one.");
        }
        if (loopValue.isnil()) loopValue = lowerLoopValue;
        if (!loopValue.isnil() && !loopValue.isboolean()) {
            throw new LuaError("[MLua] " + method + " Loop must be a boolean.");
        }

        LuaValue speedValue = options.rawget("Speed");
        LuaValue lowerSpeedValue = options.rawget("speed");
        if (!speedValue.isnil() && !lowerSpeedValue.isnil()) {
            throw new LuaError("[MLua] " + method + " Speed and speed are aliases; provide only one.");
        }
        if (speedValue.isnil()) speedValue = lowerSpeedValue;
        float speed = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
            speedValue, 1.0f, method, "Speed");
        return new MLuaAnimationOptions(loopValue.isnil() || loopValue.toboolean(), speed);
    }
}
