package dev.mlua.modules.render.service;

import dev.mlua.api.MLuaLuaErrors;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Shared exception boundary for Lua callbacks exposed by render-object bindings. */
final class MLuaRenderLuaSupport {
    private MLuaRenderLuaSupport() { }

    static VarArgFunction operation(String operationName, Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw MLuaLuaErrors.javaFailure(operationName, failure);
                }
            }
        };
    }
}
