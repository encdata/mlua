package dev.mlua.runtime.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks which runtime session and mod created each active sound handle. */
public final class MLuaSoundOwnershipRegistry {
    private final ConcurrentHashMap<Integer, Owner> owners = new ConcurrentHashMap<>();
    private final Map<Owner, Integer> ownerCounts = new HashMap<>();
    private static final Owner LEGACY_OWNER = new Owner(null, "");

    public synchronized void register(int id, Owner owner) {
        Owner resolved = owner == null ? LEGACY_OWNER : owner;
        if (owners.putIfAbsent(id, resolved) != null) {
            throw new IllegalStateException("Sound handle id is already owned: " + id);
        }
        ownerCounts.merge(resolved, 1, Integer::sum);
    }
    public Owner ownerOf(int id) { return owners.get(id); }
    public boolean owns(int id, Owner owner) { return (owner == null ? LEGACY_OWNER : owner).equals(owners.get(id)); }
    public int size() { return owners.size(); }
    public synchronized int countOwned(Owner owner) {
        Owner resolved = owner == null ? LEGACY_OWNER : owner;
        return ownerCounts.getOrDefault(resolved, 0);
    }
    public List<Integer> idsOwned(Owner owner) {
        Owner resolved = owner == null ? LEGACY_OWNER : owner;
        List<Integer> ids = new ArrayList<>();
        owners.forEach((id, actual) -> {
            if (resolved.equals(actual)) ids.add(id);
        });
        ids.sort(Integer::compareTo);
        return List.copyOf(ids);
    }
    public synchronized List<Integer> clearOwned(Owner owner) {
        Owner resolved = owner == null ? LEGACY_OWNER : owner;
        List<Integer> removed = new ArrayList<>();
        owners.forEach((id, actual) -> {
            if (resolved.equals(actual) && owners.remove(id, actual)) {
                decrement(actual);
                removed.add(id);
            }
        });
        removed.sort(Integer::compareTo);
        return removed;
    }
    public synchronized List<Integer> clearRuntime(Object runtimeOwner) {
        if (runtimeOwner == null) return List.of();
        List<Integer> removed = new ArrayList<>();
        owners.forEach((id, actual) -> {
            if (actual.runtimeOwner == runtimeOwner && owners.remove(id, actual)) {
                decrement(actual);
                removed.add(id);
            }
        });
        removed.sort(Integer::compareTo);
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

        @Override public boolean equals(Object other) {
            return other instanceof Owner owner && runtimeOwner == owner.runtimeOwner
                && ownerModId.equals(owner.ownerModId);
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(runtimeOwner) + ownerModId.hashCode();
        }
    }
}
