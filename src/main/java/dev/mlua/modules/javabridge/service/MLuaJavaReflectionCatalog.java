package dev.mlua.modules.javabridge.service;

import dev.mlua.common.mixin.MLuaClassViewer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.luaj.vm2.LuaError;

/** Bounded, deterministic view of the JVM members exposed by the Lua Java bridge. */
final class MLuaJavaReflectionCatalog {
    private static final int MAX_ENTRIES = 4_096;
    private final BoundedCache<Class<?>, List<Method>> methods = new BoundedCache<>(MAX_ENTRIES);
    private final BoundedCache<Class<?>, List<Field>> fields = new BoundedCache<>(MAX_ENTRIES);
    private final BoundedCache<Class<?>, List<Constructor<?>>> constructors = new BoundedCache<>(MAX_ENTRIES);
    private final BoundedCache<MethodLookup, List<Method>> lookups = new BoundedCache<>(MAX_ENTRIES);
    private final BoundedCache<ExactMethodLookup, Optional<Method>> exactMethodLookups = new BoundedCache<>(MAX_ENTRIES);
    private final BoundedCache<FieldLookup, Optional<Field>> fieldLookups = new BoundedCache<>(MAX_ENTRIES);

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

    Method exactMethod(Class<?> type, String name, String descriptor, boolean requireStatic) {
        return exactMethodLookups.computeIfAbsent(new ExactMethodLookup(type, name, descriptor, requireStatic), lookup -> {
            for (Method method : methods(lookup.type)) {
                if (method.getName().equals(lookup.name)
                    && Modifier.isStatic(method.getModifiers()) == lookup.requireStatic
                    && MLuaClassViewer.descriptor(method).equals(lookup.descriptor)) {
                    return Optional.of(method);
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    Field field(Class<?> type, String name, boolean requireStatic) {
        return fieldLookups.computeIfAbsent(new FieldLookup(type, name, requireStatic), this::resolveField).orElse(null);
    }

    private Optional<Field> resolveField(FieldLookup lookup) {
        Class<?> type = lookup.type;
        String name = lookup.name;
        boolean requireStatic = lookup.requireStatic;
        String normalizedName = normalize(name);
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) == requireStatic
                    && field.getName().equals(name)
                    && prepareAccessible(field)) {
                    return Optional.of(field);
                }
            }
        }

        if (requireStatic) {
            List<Field> exactInterfaceFields = interfaceFields(type, name, false);
            if (exactInterfaceFields.size() == 1) return Optional.of(exactInterfaceFields.get(0));
            if (exactInterfaceFields.size() > 1) throw ambiguousInterfaceField(name, type, exactInterfaceFields);
        }

        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Field match = null;
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) == requireStatic
                    && normalize(field.getName()).equals(normalizedName)
                    && prepareAccessible(field)) {
                    if (match != null) {
                        throw new LuaError("[MLua] Java field name '" + name + "' is ambiguous in "
                            + current.getName() + "; use an exact Java field name");
                    }
                    match = field;
                }
            }
            if (match != null) return Optional.of(match);
        }

        if (requireStatic) {
            List<Field> normalizedInterfaceFields = interfaceFields(type, name, true);
            if (normalizedInterfaceFields.size() == 1) return Optional.of(normalizedInterfaceFields.get(0));
            if (normalizedInterfaceFields.size() > 1) throw ambiguousInterfaceField(name, type, normalizedInterfaceFields);
        }
        return Optional.empty();
    }

    private List<Field> interfaceFields(Class<?> type, String name, boolean normalized) {
        String expected = normalized ? normalize(name) : name;
        Set<Class<?>> visited = new LinkedHashSet<>();
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
                    && prepareAccessible(field)) {
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
    private record ExactMethodLookup(Class<?> type, String name, String descriptor, boolean requireStatic) { }
    private record FieldLookup(Class<?> type, String name, boolean requireStatic) { }

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
