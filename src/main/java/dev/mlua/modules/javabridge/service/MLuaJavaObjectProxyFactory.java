package dev.mlua.modules.javabridge.service;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

/** Owns the Java class/object Lua proxies and their collection semantics. */
final class MLuaJavaObjectProxyFactory {
    private final MLuaJavaService service;
    private final MLuaJavaCollectionAccess collections;
    private final Object proxyMarker = new Object();

    MLuaJavaObjectProxyFactory(MLuaJavaService service) {
        this.service = service;
        this.collections = new MLuaJavaCollectionAccess(service);
    }

    boolean isProxy(LuaValue value) {
        if (!value.istable()) return false;
        LuaValue meta = value.getmetatable();
        if (meta == null || !meta.istable()) return false;
        LuaValue marker = meta.checktable().rawget("__mlua_java_proxy");
        return marker.isuserdata() && marker.touserdata() == this.proxyMarker;
    }

    LuaValue proxyField(LuaValue value, String name) {
        return isProxy(value) ? value.checktable().rawget(name) : LuaValue.NIL;
    }

    Object unwrapMapKey(LuaValue key) {
        return collections.unwrapMapKey(key);
    }

    LuaValue classProxy(Class<?> clazz) {
        LuaTable proxy = new LuaTable();
        proxy.set("__javaClass", CoerceJavaToLua.coerce(clazz));

        LuaTable meta = new LuaTable();
        meta.set("__mlua_java_proxy", CoerceJavaToLua.coerce(this.proxyMarker));
        meta.set("__index", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                if (key.type() != LuaValue.TSTRING) {
                    return LuaValue.NIL;
                }

                String memberName = key.tojstring();
                if ("new".equals(memberName)) {
                    return constructorInvoker(proxy, clazz);
                }

                Class<?> nestedClass = service.tryResolveClass(clazz.getName() + "$" + memberName);
                if (nestedClass != null) {
                    return service.wrapJavaValue(nestedClass);
                }

                LuaValue staticMethod = staticMethodInvoker(proxy, clazz, memberName);
                if (!staticMethod.isnil()) {
                    return staticMethod;
                }

                Field field = service.members.field(clazz, memberName, true);
                if (field != null) {
                    try {
                        return service.wrapJavaValue(field.get(null), field.getGenericType());
                    } catch (ReflectiveOperationException e) {
                        throw new LuaError("[MLua] Failed to read field " + clazz.getName() + "." + memberName + ": " + service.rootMessage(e));
                    }
                }

                LuaValue classInstanceMethod = instanceMethodInvoker(proxy, clazz, memberName);
                if (!classInstanceMethod.isnil()) {
                    return classInstanceMethod;
                }

                return LuaValue.NIL;
            }
        });
        meta.set("__call", constructorInvoker(proxy, clazz));
        meta.set("__newindex", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                if (key.type() != LuaValue.TSTRING) throw new LuaError("[MLua] Java fields must be addressed by name");
                String fieldName = key.checkjstring();
                Field field = service.members.field(clazz, fieldName, true);
                if (field == null) {
                    throw new LuaError("[MLua] No static field " + clazz.getName() + "." + fieldName);
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new LuaError("[MLua] Field " + clazz.getName() + "." + fieldName + " is final");
                }
                try {
                    field.set(null, service.convertLuaValue(args.arg(3), field.getGenericType()));
                } catch (IllegalAccessException | IllegalArgumentException error) {
                    throw new LuaError("[MLua] Failed to write field " + clazz.getName() + "." + fieldName + ": " + service.rootMessage(error));
                }
                return LuaValue.NONE;
            }
        });
        meta.set("__tostring", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf("JavaClass(" + clazz.getName() + ")");
            }
        });
        proxy.setmetatable(meta);
        return proxy;
    }

    LuaValue objectProxy(Object instance, Type declaredType) {
        LuaTable proxy = new LuaTable();
        proxy.set("__javaObject", CoerceJavaToLua.coerce(instance));
        proxy.set("__javaClass", CoerceJavaToLua.coerce(instance.getClass()));

        LuaTable meta = new LuaTable();
        meta.set("__mlua_java_proxy", CoerceJavaToLua.coerce(this.proxyMarker));
        meta.set("__index", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);

                LuaValue collectionValue = collections.read(instance, declaredType, key);
                if (!collectionValue.isnil()) {
                    return collectionValue;
                }

                if (key.type() != LuaValue.TSTRING) {
                    return LuaValue.NIL;
                }

                String memberName = key.tojstring();
                // Keep Java's built-in array property available through its object proxy.
                // The proxy also supports Lua's # operator, but `array.length` is the
                // familiar read-only form for scripts that are working directly with Java.
                if (instance.getClass().isArray() && "length".equals(memberName)) {
                    return LuaValue.valueOf(Array.getLength(instance));
                }
                LuaValue instanceMethod = instanceMethodInvoker(proxy, instance, memberName);
                if (!instanceMethod.isnil()) {
                    return instanceMethod;
                }

                Method getter = service.members.propertyGetter(instance.getClass(), memberName);
                if (getter != null) {
                    try {
                        return service.wrapJavaValue(getter.invoke(instance), getter.getGenericReturnType());
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        throw new LuaError("[MLua] Failed to read property " + instance.getClass().getName() + "." + memberName + ": " + service.rootMessage(e));
                    }
                }

                Field field = service.members.field(instance.getClass(), memberName, false);
                if (field != null) {
                    try {
                        return service.wrapJavaValue(field.get(instance), field.getGenericType());
                    } catch (ReflectiveOperationException e) {
                        throw new LuaError("[MLua] Failed to read field " + instance.getClass().getName() + "." + memberName + ": " + service.rootMessage(e));
                    }
                }

                return LuaValue.NIL;
            }
        });
        meta.set("__newindex", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);

                if (collections.write(instance, declaredType, key, args.arg(3))) {
                    return LuaValue.NONE;
                }

                if (key.type() != LuaValue.TSTRING) {
                    throw new LuaError("[MLua] Java object fields must be addressed by string name");
                }

                String memberName = key.tojstring();
                if (instance.getClass().isArray() && "length".equals(memberName)) {
                    throw new LuaError("[MLua] Java array length is read-only");
                }
                LuaValue luaValue = args.arg(3);
                Object rawValue = service.isPlainLuaTable(luaValue)
                    ? service.deferLuaTable(luaValue)
                    : service.unwrapJavaValue(luaValue);
                Method setter = resolvePropertySetter(instance.getClass(), memberName, rawValue);
                if (setter != null) {
                    try {
                        setter.invoke(instance, service.convertArguments(new Object[]{rawValue}, setter));
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        throw new LuaError("[MLua] Failed to write property " + instance.getClass().getName()
                            + "." + memberName + ": " + service.rootMessage(e));
                    }
                    return LuaValue.NONE;
                }

                Field field = service.members.field(instance.getClass(), memberName, false);
                if (field == null) {
                    throw new LuaError("[MLua] No writable field or property " + instance.getClass().getName() + "." + memberName);
                }

                if (Modifier.isFinal(field.getModifiers())) {
                    throw new LuaError("[MLua] Field " + instance.getClass().getName() + "." + memberName + " is final");
                }

                try {
                    field.set(instance, service.convertLuaValue(args.arg(3), field.getGenericType()));
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    throw new LuaError("[MLua] Failed to write field " + instance.getClass().getName() + "." + memberName + ": " + service.rootMessage(e));
                }
                return LuaValue.NONE;
            }
        });
        meta.set("__tostring", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf("JavaObject(" + instance.getClass().getName() + ")");
            }
        });
        meta.set("__len", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Integer length = collections.length(instance);
                if (length == null) {
                    throw new LuaError("[MLua] Length is unavailable for " + instance.getClass().getName());
                }
                return LuaValue.valueOf(length);
            }
        });
        proxy.setmetatable(meta);
        return proxy;
    }

    private LuaValue constructorInvoker(LuaValue proxy, Class<?> clazz) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startIndex = args.arg1() == proxy ? 2 : 1;
                Object[] rawArgs = service.extractInvocationArgs(args, startIndex);
                Constructor<?> ctor = service.resolveConstructor(clazz, rawArgs);
                if (ctor == null) {
                    throw new LuaError("[MLua] No matching public constructor found for " + clazz.getName()
                        + " with arguments " + service.describeArgs(rawArgs)
                        + ". Available: " + service.describeConstructors(clazz));
                }

                try {
                    Object instance = ctor.newInstance(service.convertArguments(rawArgs, ctor));
                    return service.wrapJavaValue(instance);
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new LuaError("[MLua] Failed to construct " + clazz.getName() + ": " + service.rootMessage(e));
                }
            }
        };
    }

    private LuaValue staticMethodInvoker(LuaValue proxy, Class<?> clazz, String methodName) {
        List<Method> candidates = service.members.methods(clazz, methodName, true);

        if (candidates.isEmpty()) {
            return LuaValue.NIL;
        }

        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startIndex = args.arg1() == proxy ? 2 : 1;
                Object[] rawArgs = service.extractInvocationArgs(args, startIndex);
                Method method = service.resolveMethod(candidates, rawArgs);
                if (method == null) {
                    throw new LuaError("[MLua] No matching static method " + clazz.getName() + "." + methodName
                        + " for arguments " + service.describeArgs(rawArgs)
                        + ". Candidates: " + service.describeMethods(candidates));
                }

                try {
                    Object result = method.invoke(null, service.convertArguments(rawArgs, method));
                    return service.wrapJavaValue(result, method.getGenericReturnType());
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new LuaError("[MLua] Failed to invoke " + clazz.getName() + "." + methodName + ": " + service.rootMessage(e));
                }
            }
        };
    }

    private LuaValue instanceMethodInvoker(LuaValue proxy, Object instance, String methodName) {
        List<Method> candidates = service.members.methods(instance.getClass(), methodName, false);

        if (candidates.isEmpty()) {
            return LuaValue.NIL;
        }

        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startIndex = args.arg1() == proxy ? 2 : 1;
                Object[] rawArgs = service.extractInvocationArgs(args, startIndex);
                Method method = service.resolveMethod(candidates, rawArgs);
                if (method == null) {
                    throw new LuaError("[MLua] No matching instance method " + instance.getClass().getName() + "." + methodName
                        + " for arguments " + service.describeArgs(rawArgs)
                        + ". Candidates: " + service.describeMethods(candidates));
                }

                try {
                    Object result = method.invoke(instance, service.convertArguments(rawArgs, method));
                    return service.wrapJavaValue(result, method.getGenericReturnType());
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new LuaError("[MLua] Failed to invoke " + instance.getClass().getName() + "." + methodName + ": " + service.rootMessage(e));
                }
            }
        };
    }

    private Method resolvePropertySetter(Class<?> type, String name, Object rawValue) {
        if (name == null || name.isEmpty()) return null;
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String candidateName : List.of("set" + suffix, name)) {
            List<Method> candidates = new ArrayList<>();
            for (Method method : service.members.methods(type, candidateName, false)) {
                if (method.getParameterCount() == 1) candidates.add(method);
            }
            if (!candidates.isEmpty()) {
                Method setter = service.resolveMethod(candidates, new Object[]{rawValue});
                if (setter != null) return setter;
            }
        }
        return null;
    }

}
