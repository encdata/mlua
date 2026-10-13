package dev.mlua.runtime.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the session and mod that created each render handle. */
public final class MLuaRenderOwnershipRegistry {
    private final ConcurrentHashMap<Integer, Owner> owners = new ConcurrentHashMap<>();
    private static final Owner LEGACY_OWNER = new Owner(null, "");

    public void register(int id, Owner owner) { owners.put(id, owner == null ? LEGACY_OWNER : owner); }
    public Owner ownerOf(int id) { return owners.get(id); }
    public boolean owns(int id, Owner owner) { return (owner == null ? LEGACY_OWNER : owner).equals(owners.get(id)); }
    public List<Integer> ids(Owner owner) {
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        return owners.entrySet().stream().filter(entry -> resolvedOwner.equals(entry.getValue()))
                .map(java.util.Map.Entry::getKey).sorted().toList();
    }
    public List<Integer> clearOwned(Owner owner) {
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        List<Integer> removed = new ArrayList<>();
        owners.forEach((id, actual) -> {
            if (resolvedOwner.equals(actual) && owners.remove(id, actual)) removed.add(id);
        });
        return removed;
    }
    public List<Integer> clearRuntime(Object runtimeOwner) {
        List<Integer> removed = new ArrayList<>();
        owners.forEach((id, actual) -> {
            if (actual.runtimeOwner == runtimeOwner && owners.remove(id, actual)) removed.add(id);
        });
        return removed;
    }
    public void remove(int id) { owners.remove(id); }
    public void clear() { owners.clear(); }

    public record Owner(Object runtimeOwner, String ownerModId) {
        public Owner { if (ownerModId == null) ownerModId = ""; }
    }
}
