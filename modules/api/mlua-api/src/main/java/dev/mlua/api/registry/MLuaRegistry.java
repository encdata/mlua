package dev.mlua.api.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Thread-safe, owner-aware registry for addon and runtime definitions.
 * Keys are global to one registry, so addons should namespace them (for example
 * {@code myaddon:stalker}). The owner is tracked separately for safe cleanup.
 *
 * <pre>{@code
 * MLuaRegistry<String> definitions = new MLuaRegistry<>();
 * definitions.register("myaddon", "myaddon:stalker", "definition");
 * String stalker = definitions.get("myaddon:stalker");
 * definitions.removeOwner("myaddon");
 * }</pre>
 */
public class MLuaRegistry<T> {
    private final Map<String, Entry<T>> entries = new HashMap<>();

    /** Adds a definition. Keys are unique across owners within this registry. */
    public synchronized boolean register(String owner, String key, T value) {
        String normalizedOwner = requirePart(owner, "owner");
        String normalizedKey = requirePart(key, "key");
        return entries.putIfAbsent(normalizedKey,
            new Entry<>(normalizedOwner, Objects.requireNonNull(value, "value"))) == null;
    }

    /**
     * Registers a definition or atomically replaces one already owned by the same addon.
     * Returns {@code false} when another owner already holds the key.
     */
    public synchronized boolean registerOrReplace(String owner, String key, T value) {
        String normalizedOwner = requirePart(owner, "owner");
        String normalizedKey = requirePart(key, "key");
        Entry<T> replacement = new Entry<>(normalizedOwner, Objects.requireNonNull(value, "value"));
        Entry<T> existing = entries.get(normalizedKey);
        if (existing != null && !existing.owner().equals(normalizedOwner)) return false;
        entries.put(normalizedKey, replacement);
        return true;
    }

    /** Returns a value by key, or {@code null} when no definition is registered. */
    public synchronized T get(String key) {
        OwnedValue<T> entry = entry(key);
        return entry == null ? null : entry.value();
    }

    /** Returns the owner and value together when a key exists. */
    public synchronized OwnedValue<T> entry(String key) {
        String normalizedKey = normalizePart(key);
        if (normalizedKey == null) return null;
        Entry<T> entry = entries.get(normalizedKey);
        return entry == null ? null : new OwnedValue<>(entry.owner(), entry.value());
    }

    /** Returns the owner of a key, or {@code null} when it is absent. */
    public synchronized String ownerOf(String key) {
        OwnedValue<T> entry = entry(key);
        return entry == null ? null : entry.owner();
    }

    public synchronized boolean contains(String key) {
        String normalizedKey = normalizePart(key);
        return normalizedKey != null && entries.containsKey(normalizedKey);
    }

    /** Removes a key only when it is still owned by the supplied addon. */
    public synchronized boolean remove(String owner, String key) {
        String normalizedOwner = normalizePart(owner);
        String normalizedKey = normalizePart(key);
        if (normalizedOwner == null || normalizedKey == null) return false;
        Entry<T> entry = entries.get(normalizedKey);
        return entry != null && entry.owner().equals(normalizedOwner) && entries.remove(normalizedKey, entry);
    }

    /** Removes a definition only when its owner and exact registered value still match. */
    public synchronized boolean remove(String owner, String key, T expectedValue) {
        String normalizedOwner = normalizePart(owner);
        String normalizedKey = normalizePart(key);
        if (normalizedOwner == null || normalizedKey == null || expectedValue == null) return false;
        Entry<T> entry = entries.get(normalizedKey);
        return entry != null && entry.owner().equals(normalizedOwner) && entry.value() == expectedValue
            && entries.remove(normalizedKey, entry);
    }

    /** Removes every definition owned by an addon and returns the number removed. */
    public synchronized int removeOwner(String owner) {
        String normalizedOwner = normalizePart(owner);
        if (normalizedOwner == null) return 0;
        int removed = 0;
        Iterator<Map.Entry<String, Entry<T>>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().owner().equals(normalizedOwner)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Returns an immutable key-sorted snapshot of the current values. */
    public synchronized Map<String, T> snapshot() {
        Map<String, T> snapshot = new TreeMap<>();
        entries.forEach((key, entry) -> snapshot.put(key, entry.value()));
        return Collections.unmodifiableMap(snapshot);
    }

    /** Returns an immutable key-sorted snapshot that preserves each definition's owner. */
    public synchronized Map<String, OwnedValue<T>> snapshotEntries() {
        Map<String, OwnedValue<T>> snapshot = new TreeMap<>();
        entries.forEach((key, entry) -> snapshot.put(key, new OwnedValue<>(entry.owner(), entry.value())));
        return Collections.unmodifiableMap(snapshot);
    }

    public synchronized int size() {
        return entries.size();
    }

    private static String requirePart(String value, String name) {
        String normalized = normalizePart(value);
        if (normalized == null) throw new IllegalArgumentException(name + " must be non-empty");
        return normalized;
    }

    private static String normalizePart(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public record OwnedValue<T>(String owner, T value) { }

    private record Entry<T>(String owner, T value) { }
}
