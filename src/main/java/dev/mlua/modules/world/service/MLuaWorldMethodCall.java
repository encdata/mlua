package dev.mlua.modules.world.service;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

final class MLuaWorldMethodCall {
    private MLuaWorldMethodCall() { }

    static Varargs invoke(LuaTable world, String methodName, Varargs arguments, int firstArgument) {
        LuaValue method = world.get(methodName);
        if (!method.isfunction()) {
            throw new LuaError("[MLua] World instance does not implement '" + methodName + "'.");
        }
        return method.invoke(world, arguments.subargs(firstArgument));
    }
}
