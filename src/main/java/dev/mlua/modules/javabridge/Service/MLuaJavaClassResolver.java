package dev.mlua.modules.javabridge.Service;

import dev.mlua.loaderapi.LoaderApi;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Resolves Lua-facing Java class names through the active loader's mappings. */
final class MLuaJavaClassResolver {
    private final Map<String, Class<?>> resolved = new ConcurrentHashMap<>();
    private final Supplier<ClassLoader[]> preferredLoaders;

    MLuaJavaClassResolver(Supplier<ClassLoader[]> preferredLoaders) {
        this.preferredLoaders = preferredLoaders;
    }

    Class<?> resolve(String name) {
        if (name == null || name.isBlank()) return null;
        Class<?> cached = this.resolved.get(name);
        if (cached != null) return cached;

        Class<?> type;
        if (name.endsWith("[]")) {
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

        if (type != null) this.resolved.putIfAbsent(name, type);
        return type;
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
            default -> null;
        };
    }
}
