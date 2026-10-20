package dev.mlua.modules.javabridge.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.luaj.vm2.LuaError;

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
        String normalizedName = normalize(name);
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) == requireStatic
                    && field.getName().equals(name)
                    && MLuaJavaReflectionCatalog.prepareAccessible(field)) {
                    return field;
                }
            }
        }

        if (requireStatic) {
            List<Field> interfaceFields = interfaceFields(type, name, false);
            if (interfaceFields.size() == 1) return interfaceFields.get(0);
            if (interfaceFields.size() > 1) throw ambiguousInterfaceField(name, type, interfaceFields);
        }

        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Field match = null;
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) == requireStatic
                    && normalize(field.getName()).equals(normalizedName)
                    && MLuaJavaReflectionCatalog.prepareAccessible(field)) {
                    if (match != null) {
                        throw new LuaError("[MLua] Java field name '" + name + "' is ambiguous in "
                            + current.getName() + "; use an exact Java field name");
                    }
                    match = field;
                }
            }
            if (match != null) return match;
        }

        if (requireStatic) {
            List<Field> interfaceFields = interfaceFields(type, name, true);
            if (interfaceFields.size() == 1) return interfaceFields.get(0);
            if (interfaceFields.size() > 1) throw ambiguousInterfaceField(name, type, interfaceFields);
        }
        return null;
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

    private List<Field> interfaceFields(Class<?> type, String name, boolean normalized) {
        String expected = normalized ? normalize(name) : name;
        LinkedHashSet<Class<?>> visited = new LinkedHashSet<>();
        List<Class<?>> pending = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Collections.addAll(pending, current.getInterfaces());
        }

        List<Field> matches = new ArrayList<>();
        for (int index = 0; index < pending.size(); index++) {
            Class<?> iface = pending.get(index);
            if (!visited.add(iface)) continue;
            for (Field field : iface.getDeclaredFields()) {
                String candidate = normalized ? normalize(field.getName()) : field.getName();
                if (Modifier.isStatic(field.getModifiers()) && candidate.equals(expected)
                    && MLuaJavaReflectionCatalog.prepareAccessible(field)) {
                    addInterfaceField(matches, field);
                }
            }
            Collections.addAll(pending, iface.getInterfaces());
        }
        return List.copyOf(matches);
    }

    private static void addInterfaceField(List<Field> fields, Field candidate) {
        for (int index = fields.size() - 1; index >= 0; index--) {
            Field existing = fields.get(index);
            Class<?> existingOwner = existing.getDeclaringClass();
            Class<?> candidateOwner = candidate.getDeclaringClass();
            if (existingOwner == candidateOwner && existing.getName().equals(candidate.getName())) return;
            if (!existing.getName().equals(candidate.getName())) continue;
            if (existingOwner.isAssignableFrom(candidateOwner)) fields.remove(index);
            else if (candidateOwner.isAssignableFrom(existingOwner)) return;
        }
        fields.add(candidate);
    }

    private static LuaError ambiguousInterfaceField(String name, Class<?> type, List<Field> fields) {
        List<String> declarations = fields.stream()
            .map(field -> field.getDeclaringClass().getName() + "." + field.getName())
            .sorted()
            .toList();
        return new LuaError("[MLua] Java interface field '" + name + "' is ambiguous on "
            + type.getName() + "; use Java:GetField with an interface class. Candidates: " + declarations);
    }

    private static String normalize(String name) {
        return name.replace("_", "").toLowerCase(java.util.Locale.ROOT);
    }
}
