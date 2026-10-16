package dev.mlua.runtime.registry;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Shared, thread-safe storage for Java-provided item and block implementations. */
public final class MLuaRegisteredTypeCatalog<T> {
    private final ConcurrentMap<String, Entry<T>> entries = new ConcurrentHashMap<>();

    public void register(String name, String description, T implementation, String kind) {
        this.register("mlua", name, description, implementation, kind);
    }

    public void register(String owner, String name, String description, T implementation, String kind) {
        String key = normalizeName(name, kind);
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("[MLua] " + kind + " type owner must be non-empty");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("[MLua] " + kind + " type description must be non-empty");
        }
        Objects.requireNonNull(implementation, kind + " type implementation");
        Entry<T> entry = new Entry<>(owner.trim(), description.trim(), implementation);
        if (this.entries.putIfAbsent(key, entry) != null) {
            throw new IllegalStateException("[MLua] " + kind + " type '" + key + "' is already registered");
        }
    }

    public T get(String name, String kind) {
        String key = normalizeName(name, kind);
        Entry<T> entry = this.entries.get(key);
        return entry == null ? null : entry.implementation();
    }

    public boolean contains(String name, String kind) {
        return this.entries.containsKey(normalizeName(name, kind));
    }

    public boolean remove(String owner, String name, String kind) {
        String key = normalizeName(name, kind);
        if (owner == null || owner.isBlank()) return false;
        Entry<T> entry = this.entries.get(key);
        return entry != null && entry.owner().equals(owner.trim()) && this.entries.remove(key, entry);
    }

    public int removeOwner(String owner) {
        if (owner == null || owner.isBlank()) return 0;
        String normalizedOwner = owner.trim();
        int removed = 0;
        for (Map.Entry<String, Entry<T>> entry : this.entries.entrySet()) {
            if (entry.getValue().owner().equals(normalizedOwner)
                && this.entries.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        return removed;
    }

    public Map<String, String> descriptions(Map<String, String> builtIns) {
        Map<String, String> result = new TreeMap<>(builtIns);
        this.entries.forEach((name, entry) -> result.put(name, entry.description()));
        return Collections.unmodifiableMap(result);
    }

    public static String normalizeName(String name, String kind) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("[MLua] " + kind + " type name must be non-empty");
        }
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("[MLua] Invalid " + kind.toLowerCase(java.util.Locale.ROOT)
                + " type name: " + name);
        }
        return normalized;
    }

    private record Entry<T>(String owner, String description, T implementation) { }
}
