package dev.mlua.modules.javabridge.service;

import dev.mlua.loaderapi.LoaderApi;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Resolves Lua-facing Java class names through the active loader's mappings. */
final class MLuaJavaClassResolver {
    private static final int MAX_RESOLVED_CLASSES = 4_096;
    private final Map<String, Class<?>> resolved = new LinkedHashMap<>(64, 0.75f, true);
    private final Supplier<ClassLoader[]> preferredLoaders;

    MLuaJavaClassResolver(Supplier<ClassLoader[]> preferredLoaders) {
        this.preferredLoaders = preferredLoaders;
    }

    Class<?> resolve(String name) {
        if (name == null || name.isBlank()) return null;
        Class<?> cached;
        synchronized (this.resolved) {
            cached = this.resolved.get(name);
        }
        if (cached != null) return cached;

        Class<?> type;
        if (name.startsWith("[")) {
            type = this.resolveJvmArrayName(name);
        } else if (name.endsWith("[]")) {
            Class<?> component = this.resolve(name.substring(0, name.length() - 2));
            if (component == null || component == void.class) return null;
            type = Array.newInstance(component, 0).getClass();
        } else {
            type = primitiveType(name);
            if (type == null) {
                String runtimeName = LoaderApi.mappings().mapClassName(name);
                ClassLoader[] loaders = this.preferredLoaders.get();
                type = LoaderApi.javaClasses().resolve(runtimeName, loaders);
                if (type == null && !runtimeName.equals(name)) {
                    type = LoaderApi.javaClasses().resolve(name, loaders);
                }
            }
        }

        if (type != null) {
            synchronized (this.resolved) {
                Class<?> raced = this.resolved.get(name);
                if (raced != null) return raced;
                this.resolved.put(name, type);
                if (this.resolved.size() > MAX_RESOLVED_CLASSES) {
                    Iterator<String> oldest = this.resolved.keySet().iterator();
                    if (oldest.hasNext()) {
                        oldest.next();
                        oldest.remove();
                    }
                }
            }
        }
        return type;
    }

    /** Resolves the array names returned by Class.getName(), including JVM descriptors. */
    private Class<?> resolveJvmArrayName(String name) {
        int dimensions = 0;
        while (dimensions < name.length() && name.charAt(dimensions) == '[') dimensions++;
        if (dimensions == 0 || dimensions > 255 || dimensions == name.length()) return null;

        Class<?> component;
        String descriptor = name.substring(dimensions);
        if (descriptor.length() == 1) {
            component = switch (descriptor.charAt(0)) {
                case 'Z' -> boolean.class;
                case 'B' -> byte.class;
                case 'C' -> char.class;
                case 'S' -> short.class;
                case 'I' -> int.class;
                case 'J' -> long.class;
                case 'F' -> float.class;
                case 'D' -> double.class;
                default -> null;
            };
        } else if (descriptor.length() > 2 && descriptor.charAt(0) == 'L'
                && descriptor.charAt(descriptor.length() - 1) == ';') {
            String componentName = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
            if (componentName.isBlank() || componentName.indexOf('[') >= 0 || componentName.indexOf(';') >= 0) {
                return null;
            }
            component = this.resolve(componentName);
        } else {
            return null;
        }
        if (component == null || component == void.class) return null;
        return Array.newInstance(component, new int[dimensions]).getClass();
    }

    private static Class<?> primitiveType(String name) {
        return switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "void" -> void.class;
            default -> null;
        };
    }
}
