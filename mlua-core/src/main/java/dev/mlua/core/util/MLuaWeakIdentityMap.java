package dev.mlua.core.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A synchronized weak-key map whose keys compare by object identity. */
public final class MLuaWeakIdentityMap<K, V> {
    private final ReferenceQueue<K> collectedKeys = new ReferenceQueue<>();
    private final Map<IdentityReference<K>, V> entries = new HashMap<>();

    public synchronized V get(K key) {
        Objects.requireNonNull(key, "key");
        this.removeCollectedKeys();
        return this.entries.get(new IdentityReference<>(key));
    }

    public synchronized V put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        this.removeCollectedKeys();
        return this.entries.put(new IdentityReference<>(key, this.collectedKeys), value);
    }

    public synchronized V remove(K key) {
        Objects.requireNonNull(key, "key");
        this.removeCollectedKeys();
        return this.entries.remove(new IdentityReference<>(key));
    }

    public synchronized int size() {
        this.removeCollectedKeys();
        return this.entries.size();
    }

    public synchronized List<V> values() {
        this.removeCollectedKeys();
        return new ArrayList<>(this.entries.values());
    }

    public synchronized void clear() {
        this.entries.clear();
        while (this.collectedKeys.poll() != null) { }
    }

    @SuppressWarnings("unchecked")
    private void removeCollectedKeys() {
        IdentityReference<K> reference;
        while ((reference = (IdentityReference<K>) this.collectedKeys.poll()) != null) {
            this.entries.remove(reference);
        }
    }

    private static final class IdentityReference<T> extends WeakReference<T> {
        private final int hash;

        private IdentityReference(T value) {
            super(value);
            this.hash = System.identityHashCode(value);
        }

        private IdentityReference(T value, ReferenceQueue<T> queue) {
            super(value, queue);
            this.hash = System.identityHashCode(value);
        }

        @Override public int hashCode() { return this.hash; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityReference<?> reference)) return false;
            Object value = this.get();
            return value != null && value == reference.get();
        }
    }
}
