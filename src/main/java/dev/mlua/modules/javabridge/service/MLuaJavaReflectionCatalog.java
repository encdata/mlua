package dev.mlua.modules.javabridge.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded, deterministic view of the JVM members exposed by the Lua Java bridge. */
final class MLuaJavaReflectionCatalog {
    private static final int MAX_ENTRIES = 4_096;
    private final BoundedCache<Class<?>, List<Method>> methods = new BoundedCache<>(MAX_ENTRIES);
    private final BoundedCache<Class<?>, List<Field>> fields = new BoundedCache<>(MAX_ENTRIES);
    private final BoundedCache<Class<?>, List<Constructor<?>>> constructors = new BoundedCache<>(MAX_ENTRIES);
    private final BoundedCache<MethodLookup, List<Method>> lookups = new BoundedCache<>(MAX_ENTRIES);

    List<Method> methods(Class<?> type) {
        return methods.computeIfAbsent(type, this::discoverMethods);
    }

    List<Field> fields(Class<?> type) {
        return fields.computeIfAbsent(type, this::discoverFields);
    }

    List<Constructor<?>> constructors(Class<?> type) {
        return constructors.computeIfAbsent(type, key -> List.of(key.getConstructors()));
    }

    List<Method> methodsNamed(Class<?> type, String name, boolean requireStatic) {
        return lookups.computeIfAbsent(new MethodLookup(type, name, requireStatic), key -> {
            String normalized = normalize(key.name);
            List<Method> exact = new ArrayList<>();
            List<Method> fallback = new ArrayList<>();
            for (Method method : methods(key.type)) {
                if (Modifier.isStatic(method.getModifiers()) != key.requireStatic) continue;
                if (method.getName().equals(key.name)) exact.add(method);
                else if (normalize(method.getName()).equals(normalized)) fallback.add(method);
            }
            if (!exact.isEmpty()) return List.copyOf(exact);
            return hasSingleName(fallback) ? List.copyOf(fallback) : List.of();
        });
    }

    private List<Method> discoverMethods(Class<?> type) {
        Map<String, Method> found = new LinkedHashMap<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) addMethod(found, method);
        }
        for (Method method : type.getMethods()) addMethod(found, method);
        List<Method> ordered = new ArrayList<>(found.values());
        ordered.sort(Comparator.comparing(MLuaJavaReflectionCatalog::methodSignature));
        return List.copyOf(ordered);
    }

    private void addMethod(Map<String, Method> found, Method method) {
        if (method.isBridge() || method.isSynthetic() || !prepareAccessible(method)) return;
        found.putIfAbsent(executableSignature(method), method);
    }

    private List<Field> discoverFields(Class<?> type) {
        Map<String, Field> found = new LinkedHashMap<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) addField(found, field);
        }
        for (Field field : type.getFields()) addField(found, field);
        List<Field> ordered = new ArrayList<>(found.values());
        ordered.sort(Comparator.comparing(field -> field.getDeclaringClass().getName() + "#" + field.getName()));
        return List.copyOf(ordered);
    }

    private void addField(Map<String, Field> found, Field field) {
        String signature = field.getDeclaringClass().getName() + "#" + field.getName();
        if (prepareAccessible(field)) found.putIfAbsent(signature, field);
    }

    private static String normalize(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static boolean hasSingleName(List<Method> methods) {
        if (methods.isEmpty()) return false;
        String first = methods.get(0).getName();
        return methods.stream().allMatch(method -> method.getName().equals(first));
    }

    private static String executableSignature(Method method) {
        StringBuilder signature = new StringBuilder(method.getName()).append('(');
        for (Class<?> parameter : method.getParameterTypes()) signature.append(parameter.getName()).append(';');
        // Return types do not overload methods; collapsing them keeps the subclass override.
        return signature.append(')').toString();
    }

    private static String methodSignature(Method method) {
        StringBuilder signature = new StringBuilder(method.getName()).append('(');
        Class<?>[] parameters = method.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) signature.append(", ");
            signature.append(parameters[index].getName());
        }
        return signature.append(") -> ").append(method.getReturnType().getName()).toString();
    }

    static boolean prepareAccessible(java.lang.reflect.AccessibleObject member) {
        try {
            member.setAccessible(true);
            return true;
        } catch (RuntimeException inaccessible) {
            if (member instanceof Member reflectiveMember) {
                return Modifier.isPublic(reflectiveMember.getModifiers())
                    && Modifier.isPublic(reflectiveMember.getDeclaringClass().getModifiers());
            }
            return false;
        }
    }

    private record MethodLookup(Class<?> type, String name, boolean requireStatic) { }

    /** Access-ordered cache; arbitrary reflected classes cannot grow memory without bound. */
    private static final class BoundedCache<K, V> {
        private final int maximumSize;
        private final LinkedHashMap<K, V> values = new LinkedHashMap<>(64, 0.75f, true);

        private BoundedCache(int maximumSize) {
            if (maximumSize < 1) throw new IllegalArgumentException("Cache size must be positive");
            this.maximumSize = maximumSize;
        }

        private V computeIfAbsent(K key, java.util.function.Function<? super K, ? extends V> factory) {
            synchronized (this) {
                V cached = values.get(key);
                if (cached != null) return cached;
            }
            V created = factory.apply(key);
            if (created == null) return null;
            synchronized (this) {
                V raced = values.get(key);
                if (raced != null) return raced;
                values.put(key, created);
                while (values.size() > maximumSize) {
                    var oldest = values.keySet().iterator();
                    if (!oldest.hasNext()) break;
                    oldest.next();
                    oldest.remove();
                }
            }
            return created;
        }
    }
}
