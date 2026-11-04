package dev.mlua.modules.javabridge.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Resolves and invokes reflected Java constructors and methods for Lua proxies. */
final class MLuaJavaInvocationBindings {
    private final MLuaJavaService service;

    MLuaJavaInvocationBindings(MLuaJavaService service) {
        this.service = service;
    }

    LuaValue constructorInvoker(LuaValue proxy, Class<?> clazz) {
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

    LuaValue staticMethodInvoker(LuaValue proxy, Class<?> clazz, String methodName) {
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

    LuaValue instanceMethodInvoker(LuaValue proxy, Object instance, String methodName) {
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

    Method resolvePropertySetter(Class<?> type, String name, Object rawValue) {
        MLuaJavaReflectionCatalog.PropertySetterCandidates candidates = service.members.propertySetters(type, name);
        Method setter = service.resolveMethod(candidates.setters(), new Object[]{rawValue});
        if (setter != null) return setter;
        return service.resolveMethod(candidates.namedMethods(), new Object[]{rawValue});
    }
}
