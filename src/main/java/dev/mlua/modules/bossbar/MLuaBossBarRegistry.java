package dev.mlua.modules.bossbar;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime and mod owned boss bar storage. */
public final class MLuaBossBarRegistry<T> {
    private final ConcurrentHashMap<Key, T> bars = new ConcurrentHashMap<>();

    public T put(Key key, T value) { return bars.put(key, value); }
    public T get(Key key) { return bars.get(key); }
    public T remove(Key key) { return bars.remove(key); }
    public boolean remove(Key key, T value) { return bars.remove(key, value); }

    public Map<Key, T> snapshot(Object runtimeOwner, String ownerModId) {
        Map<Key, T> result = new java.util.HashMap<>();
        bars.forEach((key, value) -> {
            if (key.runtimeOwner == runtimeOwner && key.ownerModId.equals(ownerModId)) result.put(key, value);
        });
        return result;
    }

    public List<T> clearOwned(Object runtimeOwner, String ownerModId) {
        java.util.ArrayList<T> removed = new java.util.ArrayList<>();
        bars.forEach((key, value) -> {
            if (key.runtimeOwner == runtimeOwner && key.ownerModId.equals(ownerModId)
                    && bars.remove(key, value)) removed.add(value);
        });
        return removed;
    }

    public List<T> clearRuntime(Object runtimeOwner) {
        java.util.ArrayList<T> removed = new java.util.ArrayList<>();
        bars.forEach((key, value) -> {
            if (key.runtimeOwner == runtimeOwner && bars.remove(key, value)) removed.add(value);
        });
        return removed;
    }

    public record Key(Object runtimeOwner, String ownerModId, String name) {
        public Key { if (ownerModId == null) ownerModId = ""; }
    }
}
