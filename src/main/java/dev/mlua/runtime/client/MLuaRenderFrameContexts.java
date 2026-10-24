package dev.mlua.runtime.client;

import java.util.WeakHashMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Owns the temporary identity mapping between Lua render contexts and native frame scopes. */
final class MLuaRenderFrameContexts {
    private static final WeakHashMap<LuaTable, MLuaRenderFrameScope> CONTEXTS = new WeakHashMap<>();

    private MLuaRenderFrameContexts() { }

    static void register(LuaTable context, MLuaRenderFrameScope scope) {
        synchronized (CONTEXTS) {
            CONTEXTS.put(context, scope);
        }
    }

    static Binding find(Varargs args) {
        if (args == null) return null;
        for (int index = 1; index <= args.narg(); index++) {
            LuaValue argument = args.arg(index);
            if (argument == null || !argument.istable()) continue;
            LuaTable context = argument.checktable();
            synchronized (CONTEXTS) {
                MLuaRenderFrameScope scope = CONTEXTS.get(context);
                if (scope != null) return new Binding(context, scope);
            }
        }
        return null;
    }

    static MLuaRenderFrameScope get(LuaTable context) {
        synchronized (CONTEXTS) {
            return CONTEXTS.get(context);
        }
    }

    static boolean owns(LuaTable context, MLuaRenderFrameScope scope) {
        synchronized (CONTEXTS) {
            return CONTEXTS.get(context) == scope;
        }
    }

    static void remove(Binding binding) {
        if (binding == null) return;
        synchronized (CONTEXTS) {
            CONTEXTS.remove(binding.context(), binding.scope());
        }
    }

    record Binding(LuaTable context, MLuaRenderFrameScope scope) { }
}
