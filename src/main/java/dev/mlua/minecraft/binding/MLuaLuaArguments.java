package dev.mlua.minecraft.binding;

import dev.mlua.math.MLuaCFrame;
import java.util.UUID;
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

    /** Parses UUID arguments with the same Lua-facing failure across services. */
    public static UUID uuid(String value, String method, String parameter) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Non-canonical UUID");
            return parsed;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be a valid UUID string.");
        }
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

    public static double finiteNonNegativeNumber(Varargs args, int index,
                                                  String method, String parameter) {
        return finiteNonNegativeNumber(args.arg(index), method, parameter);
    }

    public static double finiteNonNegativeNumber(LuaValue value, String method, String parameter) {
        if (!value.isnumber()) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be a number.");
        }
        return finiteNonNegativeNumber(value.todouble(), method, parameter);
    }

    public static double finiteNonNegativeNumber(double number, String method, String parameter) {
        if (!Double.isFinite(number) || number < 0.0) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be finite and non-negative.");
        }
        return number;
    }

    public static float optionalFiniteFloat(Varargs args, int index, float fallback,
                                            String method, String parameter) {
        if (args.arg(index).isnil()) return fallback;
        return finiteFloat(args, index, method, parameter);
    }

    public static float optionalNonNegativeFiniteFloat(Varargs args, int index, float fallback,
                                                       String method, String parameter) {
        return optionalNonNegativeFiniteFloat(args.arg(index), fallback, method, parameter);
    }

    public static float optionalNonNegativeFiniteFloat(LuaValue value, float fallback,
                                                       String method, String parameter) {
        return value.isnil() ? fallback : finiteNonNegativeFloat(value, method, parameter);
    }

    public static float finiteNonNegativeFloat(LuaValue value, String method, String parameter) {
        if (!value.isnumber()) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be a number.");
        }
        return finiteNonNegativeFloat(value.todouble(), method, parameter);
    }

    public static float finiteNonNegativeFloat(double number, String method, String parameter) {
        finiteNonNegativeNumber(number, method, parameter);
        float converted = (float)number;
        if (!Float.isFinite(converted)) {
            throw new LuaError("[MLua] " + method + " " + parameter + " exceeds the supported range.");
        }
        return converted;
    }

    public static float unitIntervalFloat(Varargs args, int index, String method, String parameter) {
        LuaValue value = args.arg(index);
        if (!value.isnumber()) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be a number from zero to one.");
        }
        double number = value.todouble();
        if (!Double.isFinite(number) || number < 0.0 || number > 1.0) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be finite and from zero to one.");
        }
        return (float)number;
    }

    public static float finiteFloat(Varargs args, int index, String method, String parameter) {
        return finiteFloat(args.arg(index), method, parameter);
    }

    public static float finiteFloat(LuaValue value, String method, String parameter) {
        if (!value.isnumber()) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be a number.");
        }
        double number = value.todouble();
        if (!Double.isFinite(number)) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must be finite.");
        }
        float converted = (float)number;
        if (!Float.isFinite(converted)) {
            throw new LuaError("[MLua] " + method + " " + parameter + " exceeds the supported range.");
        }
        return converted;
    }

    public static long wholeLong(Varargs args, int index, String method, String parameter) {
        return wholeLong(args.arg(index), method, parameter);
    }

    public static long wholeLong(LuaValue value, String method, String parameter) {
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

    public static int wholeInt(Varargs args, int index, String method, String parameter) {
        return wholeInt(args.arg(index), method, parameter);
    }

    public static int wholeInt(LuaValue value, String method, String parameter) {
        long number = wholeLong(value, method, parameter);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must fit in a 32-bit integer.");
        }
        return (int)number;
    }

    /** Reads an exact integer that may be represented as signed or unsigned 32-bit bits, such as ARGB. */
    public static int wholeInt32Bits(LuaValue value, String method, String parameter) {
        long number = wholeLong(value, method, parameter);
        if (number < Integer.MIN_VALUE || number > 0xFFFF_FFFFL) {
            throw new LuaError("[MLua] " + method + " " + parameter + " must fit in 32-bit integer bits.");
        }
        return (int)number;
    }

    public static int optionalWholeInt(Varargs args, int index, int fallback,
                                       String method, String parameter) {
        return optionalWholeInt(args.arg(index), fallback, method, parameter);
    }

    public static int optionalWholeInt(LuaValue value, int fallback,
                                       String method, String parameter) {
        return value.isnil() ? fallback : wholeInt(value, method, parameter);
    }
}
