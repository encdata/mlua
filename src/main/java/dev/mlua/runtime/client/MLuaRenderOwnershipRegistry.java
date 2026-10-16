package dev.mlua.runtime.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the session and mod that created each render handle. */
public final class MLuaRenderOwnershipRegistry {
    public static final int MAX_RENDER_OBJECTS = 8192;
    public static final int MAX_RENDER_OBJECTS_PER_OWNER = 512;
    private final ConcurrentHashMap<Integer, Owner> owners = new ConcurrentHashMap<>();
    private final Map<Owner, Integer> ownerCounts = new HashMap<>();
    private static final Owner LEGACY_OWNER = new Owner(null, "");

    public synchronized void register(int id, Owner owner) {
        Owner resolved = owner == null ? LEGACY_OWNER : owner;
        Owner previous = owners.put(id, resolved);
        if (previous != null) decrement(previous);
        ownerCounts.merge(resolved, 1, Integer::sum);
    }

    public synchronized boolean tryRegister(int id, Owner owner, int totalLimit, int ownerLimit) {
        Owner resolved = owner == null ? LEGACY_OWNER : owner;
        if (owners.containsKey(id)) throw new IllegalArgumentException("Render object id is already owned: " + id);
        if (totalLimit < 1 || ownerLimit < 1) throw new IllegalArgumentException("Render object limits must be positive");
        if (owners.size() >= totalLimit || ownerCounts.getOrDefault(resolved, 0) >= ownerLimit) return false;
        owners.put(id, resolved);
        ownerCounts.merge(resolved, 1, Integer::sum);
        return true;
    }

    public synchronized int count(Owner owner) {
        return ownerCounts.getOrDefault(owner == null ? LEGACY_OWNER : owner, 0);
    }
    public Owner ownerOf(int id) { return owners.get(id); }
    public boolean owns(int id, Owner owner) { return (owner == null ? LEGACY_OWNER : owner).equals(owners.get(id)); }
    public List<Integer> ids(Owner owner) {
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        return owners.entrySet().stream().filter(entry -> resolvedOwner.equals(entry.getValue()))
                .map(java.util.Map.Entry::getKey).sorted().toList();
    }
    public synchronized List<Integer> clearOwned(Owner owner) {
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        List<Integer> removed = new ArrayList<>();
        owners.forEach((id, actual) -> {
            if (resolvedOwner.equals(actual) && owners.remove(id, actual)) {
                decrement(actual);
                removed.add(id);
            }
        });
        return removed;
    }
    public synchronized List<Integer> clearRuntime(Object runtimeOwner) {
        List<Integer> removed = new ArrayList<>();
        owners.forEach((id, actual) -> {
            if (actual.runtimeOwner == runtimeOwner && owners.remove(id, actual)) {
                decrement(actual);
                removed.add(id);
            }
        });
        return removed;
    }
    public synchronized void remove(int id) {
        Owner previous = owners.remove(id);
        if (previous != null) decrement(previous);
    }

    public synchronized void clear() {
        owners.clear();
        ownerCounts.clear();
    }

    private void decrement(Owner owner) {
        int remaining = ownerCounts.getOrDefault(owner, 0) - 1;
        if (remaining <= 0) ownerCounts.remove(owner);
        else ownerCounts.put(owner, remaining);
    }

    public record Owner(Object runtimeOwner, String ownerModId) {
        public Owner { if (ownerModId == null) ownerModId = ""; }
    }
}
