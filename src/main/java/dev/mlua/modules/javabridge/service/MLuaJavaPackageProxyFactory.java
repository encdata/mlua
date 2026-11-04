package dev.mlua.modules.javabridge.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Builds lazy package proxies that resolve Java classes only when scripts access a name. */
final class MLuaJavaPackageProxyFactory {
    private final MLuaJavaService service;

    MLuaJavaPackageProxyFactory(MLuaJavaService service) {
        this.service = service;
    }

    LuaValue create(String path) {
        LuaTable proxy = new LuaTable();
        LuaTable metatable = new LuaTable();
        metatable.set("__index", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                if (key.type() != LuaValue.TSTRING) return LuaValue.NIL;
                String nextPath = path + "." + key.tojstring();
                Class<?> resolved = service.tryResolveClass(nextPath);
                return resolved != null ? service.wrapJavaValue(resolved) : create(nextPath);
            }
        });
        metatable.set("__call", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                throw new LuaError("[MLua] Unresolved Java package path '" + path + "' cannot be called. Resolve a class first.");
            }
        });
        metatable.set("__tostring", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf("JavaPackage(" + path + ")");
            }
        });
        proxy.setmetatable(metatable);
        return proxy;
    }
}
