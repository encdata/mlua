package dev.mlua.modules.javabridge.service;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import dev.mlua.common.mixin.MLuaClassViewer;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;

/** Creates and tracks Java interface proxies implemented by Lua tables. */
final class MLuaJavaInterfaceProxyFactory {
    private MLuaJavaInterfaceProxyFactory() { }

    static Object create(MLuaJavaService service, MLuaJavaProxyRegistry registry, Class<?>[] interfaces,
                         LuaTable implementation, MLuaContext ownerContext) {
        validateInterfaceImplementation(interfaces, implementation);
        for (Class<?> iface : interfaces) {
            if (!iface.isInterface()) {
                throw new LuaError("[MLua] Java:Proxy only supports interfaces in v1: " + iface.getName());
            }
        }
        ClassLoader loader = proxyClassLoader(service, interfaces);
        if (loader == null && java.util.Arrays.stream(interfaces).anyMatch(iface -> iface.getClassLoader() != null)) {
            throw new LuaError("[MLua] Could not create Java interface proxy: no class loader can see all requested interfaces");
        }
        try {
            LuaInvocationHandler handler = new LuaInvocationHandler(service, implementation, ownerContext);
            Object proxy = Proxy.newProxyInstance(loader, interfaces, handler);
            registry.register(ownerContext, handler);
            return proxy;
        } catch (IllegalArgumentException error) {
            throw new LuaError("[MLua] Could not create Java interface proxy: " + error.getMessage());
        }
    }

    static void validateInterfaceImplementation(Class<?>[] interfaces, LuaTable implementation) {
        Map<String, Method> requiredMethods = new LinkedHashMap<>();
        for (Class<?> iface : interfaces) {
            if (!iface.isInterface()) {
                throw new LuaError("[MLua] Java:Proxy only supports interfaces in v1: " + iface.getName());
            }
            for (Method method : iface.getMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.isDefault()
                    || method.getDeclaringClass() == Object.class) {
                    continue;
                }
                requiredMethods.putIfAbsent(proxyMethodKey(method), method);
            }
        }

        List<String> missing = new ArrayList<>();
        for (Method method : requiredMethods.values()) {
            LuaValue implementationMethod = proxyFunction(implementation, method);
            if (!implementationMethod.isfunction()) {
                missing.add(method.toGenericString());
            }
        }
        if (!missing.isEmpty()) {
            missing.sort(String::compareTo);
            throw new LuaError("[MLua] Java:Proxy is missing Lua functions for interface methods: " + missing);
        }
    }

    private static String proxyMethodKey(Method method) {
        return method.getName() + MLuaClassViewer.descriptor(method);
    }

    private static LuaValue proxyFunction(LuaTable implementation, Method method) {
        LuaValue exact = implementation.get(proxyMethodKey(method));
        return exact.isfunction() ? exact : implementation.get(method.getName());
    }

    static ClassLoader proxyClassLoader(MLuaJavaService service, Class<?>[] interfaces) {
        java.util.Set<ClassLoader> candidates = new java.util.LinkedHashSet<>();
        for (Class<?> iface : interfaces) {
            candidates.add(iface.getClassLoader());
        }
        candidates.add(Thread.currentThread().getContextClassLoader());
        candidates.add(service.serviceContextClassLoader());
        candidates.add(service.getClass().getClassLoader());
        candidates.add(ClassLoader.getSystemClassLoader());

        for (ClassLoader candidate : candidates) {
            boolean visible = true;
            for (Class<?> iface : interfaces) {
                try {
                    Class<?> resolved = Class.forName(iface.getName(), false, candidate);
                    if (resolved != iface) {
                        visible = false;
                        break;
                    }
                } catch (ClassNotFoundException | LinkageError error) {
                    visible = false;
                    break;
                }
            }
            if (visible) return candidate;
        }
        return null;
    }

    private static final class LuaInvocationHandler implements InvocationHandler, MLuaJavaProxyRegistry.Handle {
        private volatile MLuaJavaService service;
        private volatile LuaTable implementation;
        private volatile MLuaContext ownerContext;

        private LuaInvocationHandler(MLuaJavaService service, LuaTable implementation, MLuaContext ownerContext) {
            this.service = service;
            this.implementation = implementation;
            this.ownerContext = ownerContext;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) return invokeObjectMethod(proxy, method, args);
            MLuaContext context = this.ownerContext;
            MLuaJavaService bridge = this.service;
            LuaTable functions = this.implementation;
            if (context == null || bridge == null || functions == null) {
                throw new IllegalStateException("[MLua] This Lua-backed Java proxy was invalidated when its script runtime unloaded.");
            }
            return MLuaLuaBindings.withContext(context, () -> {
                if (this.ownerContext != context || this.service != bridge || this.implementation != functions) {
                    throw new IllegalStateException("[MLua] This Lua-backed Java proxy was invalidated when its script runtime unloaded.");
                }
                return invokeLocked(proxy, method, args, bridge, functions, context);
            });
        }

        private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> "LuaProxy(" + proxy.getClass().getInterfaces()[0].getName() + ")";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                default -> throw new UnsupportedOperationException("Unsupported Object method: " + method.getName());
            };
        }

        private static Object invokeLocked(Object proxy, Method method, Object[] args,
                                            MLuaJavaService service, LuaTable implementation,
                                            MLuaContext context) {
            LuaValue function = proxyFunction(implementation, method);
            if (function.isnil()) {
                if (method.isDefault()) {
                    try {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }
                throw new UnsupportedOperationException("Lua proxy does not implement " + method.toGenericString());
            }

            LuaValue[] luaArgs = new LuaValue[args == null ? 0 : args.length];
            Type[] parameterTypes = method.getGenericParameterTypes();
            for (int i = 0; i < luaArgs.length; i++) {
                luaArgs[i] = service.wrapJavaValue(args[i], parameterTypes[i]);
            }

            LuaValue result = MLuaCallbackBudget.run(context,
                () -> function.invoke(LuaValue.varargsOf(luaArgs)).arg1());
            if (method.getReturnType() == void.class) return null;
            if (result.isnil() && method.getReturnType().isPrimitive()) {
                throw new LuaError("[MLua] Lua proxy method " + method.getName() + " must return " + method.getReturnType().getName());
            }
            return service.convertLuaValue(result, method.getGenericReturnType());
        }

        @Override
        public void close() {
            MLuaContext context = this.ownerContext;
            if (context == null) return;
            MLuaLuaBindings.withExecutionLock(context, () -> {
                if (this.ownerContext != context) return null;
                this.ownerContext = null;
                this.implementation = null;
                this.service = null;
                return null;
            });
        }
    }

}
