package dev.mlua.core.classes;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns class instance property, method, and fallback lookup semantics. */
final class MLuaClassInstanceLookup {
    private final MLuaClassMetadata metadata;

    MLuaClassInstanceLookup(MLuaClassMetadata metadata) {
        this.metadata = metadata;
    }

    void compose(LuaTable meta, LuaTable classTable) {
        LuaValue previousIndex = meta.rawget("__index");
        meta.rawset("__metatable", LuaValue.TRUE);
        LuaValue fallback = meta.rawget("__mlua_instance_fallback");
        if (!meta.rawget("__mlua_instance_lookup").toboolean()) {
            fallback = previousIndex.isnil() ? LuaValue.FALSE : previousIndex;
        }
        LuaValue instanceFallback = fallback;
        meta.rawset("__mlua_instance_fallback", instanceFallback);
        meta.rawset("__mlua_instance_lookup", LuaValue.TRUE);
        meta.rawset("__index", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                String className = classTable.rawget("__type").optjstring("");
                if (key.isstring() && key.tojstring().equals("ClassName")) {
                    return LuaValue.valueOf(className);
                }
                LuaTable property = key.isstring()
                    ? MLuaClassInstanceLookup.this.metadata.findPropertyDescriptor(className, key.tojstring()) : null;
                if (property != null) {
                    return property.rawget("get").call(args.arg1());
                }

                LuaValue inherited = classTable.get(key);
                if (!inherited.isnil()) {
                    if (inherited.isfunction()) {
                        LuaValue serviceResolver = meta.rawget("__mlua_service_resolver");
                        if (!meta.rawget("__mlua_service_instance").istable() && serviceResolver.isfunction()) {
                            serviceResolver.invoke(args.arg1());
                        }
                        LuaValue serviceDispatchers = classTable.rawget("__mlua_service_dispatchers");
                        if (classTable.rawget("__mlua_service_class").toboolean()
                                && serviceDispatchers.istable()
                                && serviceDispatchers.checktable().rawget(key).toboolean()) {
                            return bindServiceMethod(inherited, args.arg1().checktable(), className,
                                classTable.rawget("__mlua_service_name").optjstring(""));
                        }
                    }
                    return inherited.isfunction()
                        ? MLuaClassThreadDispatcher.bind(args.arg1().checktable(), inherited)
                        : inherited;
                }

                LuaValue methodsValue = meta.rawget("__mlua_instance_methods");
                if (methodsValue.istable()) {
                    LuaValue method = methodsValue.get(key);
                    if (!method.isnil()) return method;
                }
                if (instanceFallback.istable()) return instanceFallback.get(key);
                if (instanceFallback.isfunction()) return instanceFallback.invoke(args).arg1();
                return LuaValue.NIL;
            }
        });

        LuaValue previousNewIndex = meta.rawget("__newindex");
        LuaValue newIndexFallback = meta.rawget("__mlua_instance_newindex_fallback");
        if (!meta.rawget("__mlua_instance_newindex_lookup").toboolean()) {
            newIndexFallback = previousNewIndex.isnil() ? LuaValue.FALSE : previousNewIndex;
        }
        LuaValue instanceNewIndexFallback = newIndexFallback;
        meta.rawset("__mlua_instance_newindex_fallback", instanceNewIndexFallback);
        meta.rawset("__mlua_instance_newindex_lookup", LuaValue.TRUE);
        meta.rawset("__newindex", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue receiver = args.arg1();
                LuaValue key = args.arg(2);
                LuaValue value = args.arg(3);
                if (key.isstring()) {
                    String name = key.tojstring();
                    if (isReservedMetadata(name)) {
                        throw new LuaError("[MLua] Cannot overwrite reserved class metadata field '" + name + "'.");
                    }
                    if (classTable.get(key).isfunction()) {
                        throw new LuaError("[MLua] Cannot shadow registered class method '" + name
                            + "' on a live instance; register the method on its class with a description.");
                    }
                    LuaTable property = MLuaClassInstanceLookup.this.metadata.findPropertyDescriptor(
                        classTable.rawget("__type").optjstring(""), name);
                    if (property != null) {
                        LuaValue setter = property.rawget("set");
                        if (!setter.isfunction()) throw new LuaError("[MLua] Property '" + name + "' is read-only.");
                        setter.call(receiver, value);
                        return LuaValue.NONE;
                    }
                }
                if (instanceNewIndexFallback.istable()) {
                    instanceNewIndexFallback.set(key, value);
                } else if (instanceNewIndexFallback.isfunction()) {
                    instanceNewIndexFallback.invoke(LuaValue.varargsOf(new LuaValue[]{receiver, key, value}));
                } else if (receiver.istable()) {
                    receiver.checktable().rawset(key, value);
                }
                return LuaValue.NONE;
            }
        });
    }

    private static LuaValue bindServiceMethod(LuaValue dispatcher, LuaTable boundInstance,
            String className, String serviceName) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs callArgs) {
                LuaValue first = callArgs.arg1();
                if (first == boundInstance || isServiceReceiver(first, className, serviceName)) {
                    return dispatcher.invoke(callArgs);
                }
                return dispatcher.invoke(LuaValue.varargsOf(boundInstance, callArgs));
            }
        };
    }

    private static boolean isReservedMetadata(String name) {
        return name.equals("__type") || name.equals("__class") || name.equals("ClassName")
            || name.startsWith("__mlua_");
    }

    private static boolean isServiceReceiver(LuaValue value, String className, String serviceName) {
        if (!value.istable()) return false;
        LuaValue metaValue = value.getmetatable();
        if (!metaValue.istable()) return false;
        LuaTable meta = metaValue.checktable();
        if (serviceName.equals(meta.rawget("__mlua_service_name").optjstring(""))) return true;
        LuaValue type = value.rawget("__type");
        return type.isstring() && className.equals(type.tojstring());
    }
}
