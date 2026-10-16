package dev.mlua.runtime.registry;

import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.TreeMap;
import dev.mlua.api.registry.MLuaRegistry;

/** Shared, thread-safe storage for Java-provided item and block implementations. */
public final class MLuaRegisteredTypeCatalog<T> {
    public static final int MAX_REGISTERED_TYPES = 4_096;
    private final MLuaRegistry<Entry<T>> entries = new MLuaRegistry<>();

    public synchronized void register(String name, String description, T implementation, String kind) {
        this.register("mlua", name, description, implementation, kind);
    }

    public synchronized void register(String owner, String name, String description, T implementation, String kind) {
        String key = normalizeName(name, kind);
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("[MLua] " + kind + " type owner must be non-empty");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("[MLua] " + kind + " type description must be non-empty");
        }
        Objects.requireNonNull(implementation, kind + " type implementation");
        if (this.entries.contains(key)) {
            throw new IllegalStateException("[MLua] " + kind + " type '" + key + "' is already registered");
        }
        if (this.entries.size() >= MAX_REGISTERED_TYPES) {
            throw new IllegalStateException("[MLua] " + kind + " type limit reached (" + MAX_REGISTERED_TYPES + ")");
        }
        Entry<T> entry = new Entry<>(description.trim(), implementation);
        if (!this.entries.register(owner.trim(), key, entry)) {
            throw new IllegalStateException("[MLua] " + kind + " type '" + key + "' is already registered");
        }
    }

    public T get(String name, String kind) {
        String key = normalizeName(name, kind);
        Entry<T> entry = this.entries.get(key);
        return entry == null ? null : entry.implementation();
    }

    public boolean contains(String name, String kind) {
        return this.entries.contains(normalizeName(name, kind));
    }

    public synchronized boolean remove(String owner, String name, String kind) {
        String key = normalizeName(name, kind);
        if (owner == null || owner.isBlank()) return false;
        return this.entries.remove(owner.trim(), key);
    }

    public synchronized int removeOwner(String owner) {
        return this.entries.removeOwner(owner);
    }

    public Map<String, String> descriptions(Map<String, String> builtIns) {
        Map<String, String> result = new TreeMap<>(builtIns);
        this.entries.snapshot().forEach((name, entry) -> result.put(name, entry.description()));
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

    private record Entry<T>(String description, T implementation) { }
}
