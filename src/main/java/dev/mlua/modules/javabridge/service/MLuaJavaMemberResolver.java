package dev.mlua.modules.javabridge.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Resolves Java members exposed by Java proxies while preserving exact-name precedence. */
final class MLuaJavaMemberResolver {
    private final MLuaJavaReflectionCatalog reflection;

    MLuaJavaMemberResolver(MLuaJavaReflectionCatalog reflection) {
        this.reflection = reflection;
    }

    List<Method> methods(Class<?> type, String name, boolean requireStatic) {
        List<Method> methods = reflection.methodsNamed(type, name, requireStatic);
        if (!methods.isEmpty() || !requireStatic || !"getInstance".equals(name)) return methods;

        List<Method> instanceFactories = new ArrayList<>();
        for (Method method : reflection.methods(type)) {
            if (Modifier.isStatic(method.getModifiers())
                && method.getParameterCount() == 0
                && method.getReturnType().equals(type)) {
                instanceFactories.add(method);
            }
        }
        return List.copyOf(instanceFactories);
    }

    Field field(Class<?> type, String name, boolean requireStatic) {
        return reflection.field(type, name, requireStatic);
    }

    Method propertyGetter(Class<?> type, String name) {
        if (name == null || name.isEmpty()) return null;
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String candidate : List.of("get" + suffix, "is" + suffix, name)) {
            for (Method method : methods(type, candidate, false)) {
                if (method.getParameterCount() == 0) return method;
            }
        }
        return null;
    }

}
