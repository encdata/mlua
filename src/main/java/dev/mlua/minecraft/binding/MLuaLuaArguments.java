package dev.mlua.minecraft.binding;

import dev.mlua.math.MLuaCFrame;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Shared strict readers for optional Lua arguments used by Minecraft bindings. */
public final class MLuaLuaArguments {
    private MLuaLuaArguments() {
    }

    public static boolean optionalBoolean(Varargs args, int index, boolean fallback,
                                          String method, String parameter) {
        LuaValue value = args.arg(index);
        if (value.isnil()) return fallback;
        if (!value.isboolean()) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be a boolean.");
        }
        return value.toboolean();
    }

    public static double[] finiteVector3(LuaValue value, String argumentName) {
        double[] coordinates = MLuaCFrame.vector(value);
        for (double coordinate : coordinates) {
            if (!Double.isFinite(coordinate)) {
                throw new LuaError("[MLua] " + argumentName + " coordinates must be finite.");
            }
        }
        return coordinates;
    }

    public static double optionalFiniteNumber(Varargs args, int index, double fallback,
                                              String method, String parameter) {
        LuaValue value = args.arg(index);
        if (value.isnil()) return fallback;
        if (!value.isnumber()) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be a number.");
        }
        double number = value.todouble();
        if (!Double.isFinite(number)) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be finite.");
        }
        return number;
    }

    public static float optionalFiniteFloat(Varargs args, int index, float fallback,
                                            String method, String parameter) {
        double number = optionalFiniteNumber(args, index, fallback, method, parameter);
        float converted = (float)number;
        if (!Float.isFinite(converted)) {
            throw new LuaError("[MLua] " + method + " " + parameter + " exceeds the supported range.");
        }
        return converted;
    }

    public static long wholeLong(Varargs args, int index, String method, String parameter) {
        LuaValue value = args.arg(index);
        if (!value.isnumber()) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be an integer number.");
        }
        double number = value.todouble();
        if (!Double.isFinite(number) || number != Math.rint(number)
            || number < Long.MIN_VALUE || number >= 0x1.0p63) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be a whole number in the 64-bit integer range.");
        }
        return (long)number;
    }
}
