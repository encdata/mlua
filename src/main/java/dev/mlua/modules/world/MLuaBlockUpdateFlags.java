package dev.mlua.modules.world;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Shared parser for Minecraft's block update flag bitmask. */
public final class MLuaBlockUpdateFlags {
    public static final int DEFAULT = 3;

    private MLuaBlockUpdateFlags() {
    }

    public static int read(LuaValue value, String operation) {
        if (value == null || value.isnil()) return DEFAULT;
        if (!value.isnumber()) {
            throw new LuaError("[MLua] " + operation + " updateFlags must be a whole number in the integer range.");
        }
        double number = value.checkdouble();
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new LuaError("[MLua] " + operation + " updateFlags must be a whole number in the integer range.");
        }
        return (int) number;
    }
}
