package dev.mlua.runtime.registry;

import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.TreeMap;
import dev.mlua.api.registry.MLuaRegistry;

/** Shared, thread-safe storage for Java-provided item and block implementations. */
public final class MLuaRegisteredTypeCatalog<T> {
    public static final int MAX_REGISTERED_TYPES = 4_096;
    public static final int MAX_NAME_LENGTH = 256;
    public static final int MAX_OWNER_LENGTH = 128;
    public static final int MAX_DESCRIPTION_LENGTH = 2_048;
    private final MLuaRegistry<Entry<T>> entries = new MLuaRegistry<>();

    public synchronized void register(String name, String description, T implementation, String kind) {
        this.register("mlua", name, description, implementation, kind);
    }

    public synchronized void register(String owner, String name, String description, T implementation, String kind) {
        String key = normalizeName(name, kind);
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("[MLua] " + kind + " type owner must be non-empty");
        }
        String normalizedOwner = owner.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizedOwner.length() > MAX_OWNER_LENGTH
            || !normalizedOwner.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("[MLua] Invalid " + kind.toLowerCase(java.util.Locale.ROOT)
                + " type owner: " + owner);
        }
        int separator = key.indexOf(':');
        if (separator >= 0 && !normalizedOwner.equals(key.substring(0, separator))) {
            throw new IllegalArgumentException("[MLua] Namespaced " + kind.toLowerCase(java.util.Locale.ROOT)
                + " type '" + key + "' must use its registering addon's namespace ('" + normalizedOwner + "')");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("[MLua] " + kind + " type description must be non-empty");
        }
        if (description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("[MLua] " + kind + " type description cannot exceed "
                + MAX_DESCRIPTION_LENGTH + " characters");
        }
        Objects.requireNonNull(implementation, kind + " type implementation");
        if (this.entries.contains(key)) {
            throw new IllegalStateException("[MLua] " + kind + " type '" + key + "' is already registered");
        }
        if (this.entries.size() >= MAX_REGISTERED_TYPES) {
            throw new IllegalStateException("[MLua] " + kind + " type limit reached (" + MAX_REGISTERED_TYPES + ")");
        }
        Entry<T> entry = new Entry<>(description.trim(), implementation);
        if (!this.entries.register(normalizedOwner, key, entry)) {
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
        return this.entries.remove(normalizeOwner(owner), key);
    }

    /** Removes a type only if the registration handle still owns this exact provider. */
    public synchronized boolean remove(String owner, String name, String kind, T expectedProvider) {
        String key = normalizeName(name, kind);
        if (owner == null || owner.isBlank() || expectedProvider == null) return false;
        String normalizedOwner = normalizeOwner(owner);
        Entry<T> entry = this.entries.get(key);
        return entry != null && entry.implementation() == expectedProvider
            && this.entries.remove(normalizedOwner, key, entry);
    }

    public synchronized int removeOwner(String owner) {
        return owner == null || owner.isBlank() ? 0 : this.entries.removeOwner(normalizeOwner(owner));
    }

    private static String normalizeOwner(String owner) {
        return owner.trim().toLowerCase(java.util.Locale.ROOT);
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
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("[MLua] " + kind + " type name cannot exceed "
                + MAX_NAME_LENGTH + " characters");
        }
        if (!normalized.matches("[a-z][a-z0-9_]*")
            && !normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("[MLua] Invalid " + kind.toLowerCase(java.util.Locale.ROOT)
                + " type name: " + name);
        }
        return normalized;
    }

    private record Entry<T>(String description, T implementation) { }
}
