package dev.mlua.core.classes;

import dev.mlua.module.MLuaRegistration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns behavior-mixin definitions and their documented Lua functions. */
final class MLuaClassMixinRegistry {
    interface Host {
        boolean classExists(String name);
        void validateMethodRegistration(String mixinName, String methodName);
        void refreshAppliedClasses(String mixinName);
    }

    private final Host host;
    private final Map<String, LuaTable> definitions = new HashMap<>();
    private final Set<String> pendingRemovals = new java.util.HashSet<>();

    MLuaClassMixinRegistry(Host host) {
        this.host = host;
    }

    LuaTable register(String name, String description) {
        MLuaClassRegistry.requireClassName(name);
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Lua mixin description must be non-empty");
        }
        LuaTable existing = this.definitions.get(name);
        if (existing != null) {
            LuaValue oldDescription = existing.rawget("__mlua_doc_description");
            if (oldDescription.isstring() && !description.equals(oldDescription.tojstring())) {
                throw new IllegalStateException("Lua mixin '" + name + "' is already registered with a different description");
            }
            this.pendingRemovals.remove(name);
            return existing;
        }
        if (this.host.classExists(name)) {
            throw new IllegalStateException("Lua class already uses mixin name: " + name);
        }
        LuaTable mixin = new LuaTable();
        mixin.rawset("__mixin_name", LuaValue.valueOf(name));
        mixin.rawset("__mixin", LuaValue.TRUE);
        mixin.rawset("__mlua_doc_description", LuaValue.valueOf(description));
        mixin.rawset("__mlua_doc_methods", new LuaTable());
        this.installWriteGuard(mixin);
        this.definitions.put(name, mixin);
        return mixin;
    }

    LuaTable get(String name) {
        return this.definitions.get(name);
    }

    boolean contains(String name) {
        return this.definitions.containsKey(name);
    }

    Set<String> names() {
        return Collections.unmodifiableSet(new TreeSet<>(this.definitions.keySet()));
    }

    LuaTable registerFunction(String mixinName, String functionName, LuaValue function, String description) {
        LuaTable mixin = this.definitions.get(mixinName);
        if (mixin == null) throw new IllegalStateException("Lua mixin is not registered: " + mixinName);
        if (functionName == null || functionName.isBlank()) throw new IllegalArgumentException("Function name must be non-empty");
        if (MLuaRegistration.isReservedFunctionName(functionName)) {
            throw new IllegalArgumentException("Mixin function name is reserved for MLua metadata: " + functionName);
        }
        if (function == null || !function.isfunction()) throw new IllegalArgumentException("Registered function must be a Lua function");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Function description must be non-empty");
        LuaTable methods = this.methods(mixin);
        LuaValue existing = methods.rawget(functionName);
        if (!existing.isnil() && existing != function) {
            throw new IllegalStateException("Lua mixin '" + mixinName + "' already defines function '" + functionName + "'");
        }
        LuaValue docsValue = mixin.rawget("__mlua_doc_methods");
        if (!docsValue.istable()) throw new IllegalStateException("Lua mixin '" + mixinName + "' has invalid method descriptions");
        LuaTable docs = docsValue.checktable();
        LuaValue oldDescription = docs.rawget(functionName);
        if (oldDescription.isstring() && !description.equals(oldDescription.tojstring()) && existing != function) {
            throw new IllegalStateException("Lua mixin '" + mixinName + "' function '" + functionName
                + "' is already registered with a different description");
        }
        if (!oldDescription.isnil() && !oldDescription.isstring()) {
            throw new IllegalStateException("Lua mixin '" + mixinName + "' function '" + functionName
                + "' has invalid description metadata");
        }
        if (existing.isnil()) this.host.validateMethodRegistration(mixinName, functionName);
        this.pendingRemovals.remove(mixinName);
        methods.rawset(functionName, function);
        docs.rawset(functionName, LuaValue.valueOf(description));
        this.host.refreshAppliedClasses(mixinName);
        return mixin;
    }

    boolean unregisterFunction(String mixinName, String functionName, LuaValue expectedFunction) {
        LuaTable mixin = this.definitions.get(mixinName);
        if (mixin == null || functionName == null || expectedFunction == null
            || this.methods(mixin).rawget(functionName) != expectedFunction) return false;
        this.methods(mixin).rawset(functionName, LuaValue.NIL);
        LuaValue docs = mixin.rawget("__mlua_doc_methods");
        if (docs.istable()) docs.checktable().rawset(functionName, LuaValue.NIL);
        this.host.refreshAppliedClasses(mixinName);
        return true;
    }

    boolean markPendingRemoval(String name) {
        return this.pendingRemovals.add(name);
    }

    boolean isPendingRemoval(String name) {
        return this.pendingRemovals.contains(name);
    }

    void cancelPendingRemoval(String name) {
        this.pendingRemovals.remove(name);
    }

    LuaTable remove(String name) {
        this.pendingRemovals.remove(name);
        return this.definitions.remove(name);
    }

    private void installWriteGuard(LuaTable mixin) {
        LuaValue current = mixin.getmetatable();
        LuaTable meta = current != null && current.istable() ? current.checktable() : new LuaTable();
        LuaTable methods = new LuaTable();
        meta.rawset("__mlua_mixin_methods", methods);
        LuaValue previousIndex = meta.rawget("__index");
        meta.rawset("__mlua_mixin_index_fallback", previousIndex.isnil() ? LuaValue.FALSE : previousIndex);
        LuaValue indexFallback = meta.rawget("__mlua_mixin_index_fallback");
        meta.rawset("__index", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                LuaValue method = methods.get(key);
                if (!method.isnil()) return method;
                if (indexFallback.istable()) return indexFallback.get(key);
                if (indexFallback.isfunction()) return indexFallback.invoke(args).arg1();
                return LuaValue.NIL;
            }
        });
        meta.rawset("__mlua_mixin_newindex", LuaValue.TRUE);
        meta.rawset("__newindex", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                LuaValue value = args.arg(3);
                if (!methods.rawget(key).isnil()) {
                    throw new LuaError("[MLua] Cannot replace registered behavior mixin function '"
                        + key.tojstring() + "' directly; use Minecraft:RegisterBehaviorMixinFunction.");
                }
                if (key.isstring()) {
                    String field = key.tojstring();
                    if (field.equals("__mixin") || field.equals("__mixin_name") || field.startsWith("__mlua_")) {
                        throw new LuaError("[MLua] Cannot assign behavior mixin metadata field '" + field + "'.");
                    }
                    if (value.isfunction()) {
                        throw new LuaError("[MLua] Register behavior mixin function '" + field
                            + "' with Minecraft:RegisterBehaviorMixinFunction and a non-empty description.");
                    }
                }
                throw new LuaError("[MLua] Behavior mixin tables are managed by MLua; register functions with "
                    + "Minecraft:RegisterBehaviorMixinFunction.");
            }
        });
        if (current == null || !current.istable()) mixin.setmetatable(meta);
    }

    static LuaTable methods(LuaTable mixin) {
        LuaValue meta = mixin == null ? LuaValue.NIL : mixin.getmetatable();
        LuaValue methods = meta.istable() ? meta.checktable().rawget("__mlua_mixin_methods") : LuaValue.NIL;
        if (!methods.istable()) throw new IllegalStateException("Lua behavior mixin has invalid method storage");
        return methods.checktable();
    }
}
