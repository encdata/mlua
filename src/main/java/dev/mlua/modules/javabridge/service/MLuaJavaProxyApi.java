package dev.mlua.modules.javabridge.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs Java interface proxy creation and proxy limits. */
final class MLuaJavaProxyApi {
    private MLuaJavaProxyApi() { }

    static void install(LuaTable table, MLuaJavaService service) {
        LuaValue proxyFn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startIndex = 2;
                int n = args.narg() - startIndex + 1;
                if (n < 2) {
                    throw new LuaError("[MLua] Java:Proxy requires at least one interface and a Lua table");
                }

                LuaTable implementation = args.checktable(startIndex + n - 1);
                Class<?>[] interfaces = new Class<?>[n - 1];
                for (int i = 0; i < interfaces.length; i++) {
                    Class<?> iface = service.resolveClassArg(args.arg(startIndex + i));
                    if (!iface.isInterface()) {
                        throw new LuaError("[MLua] Java:Proxy only supports interfaces in v1: " + iface.getName());
                    }
                    interfaces[i] = iface;
                }

                return service.wrapJavaValue(service.createLuaInterfaceProxy(interfaces, implementation, service.activeProxyContext()));
            }
        };
        table.set("Proxy", proxyFn);
        table.set("createProxy", proxyFn);
        table.set("GetProxyLimits", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return service.proxyLimits();
            }
        });
    }
}
