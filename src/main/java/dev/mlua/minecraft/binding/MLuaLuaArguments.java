package dev.mlua.minecraft.binding;

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
}
