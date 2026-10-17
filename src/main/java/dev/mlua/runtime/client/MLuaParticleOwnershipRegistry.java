package dev.mlua.runtime.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks runtime session and mod ownership for persistent particle emitters. */
public final class MLuaParticleOwnershipRegistry {
    private final ConcurrentHashMap<Integer, Owner> owners = new ConcurrentHashMap<>();
    private static final Owner LEGACY_OWNER = new Owner(null, "");

    public void register(int id, Owner owner) { owners.put(id, owner == null ? LEGACY_OWNER : owner); }
    public Owner ownerOf(int id) { return owners.get(id); }
    public boolean owns(int id, Owner owner) { return (owner == null ? LEGACY_OWNER : owner).equals(owners.get(id)); }
    public List<Integer> clearOwned(Owner owner) {
        Owner resolved = owner == null ? LEGACY_OWNER : owner;
        List<Integer> removed = new ArrayList<>();
        owners.forEach((id, actual) -> {
            if (resolved.equals(actual) && owners.remove(id, actual)) removed.add(id);
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

        @Override public boolean equals(Object other) {
            return other instanceof Owner owner && runtimeOwner == owner.runtimeOwner
                && ownerModId.equals(owner.ownerModId);
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(runtimeOwner) + ownerModId.hashCode();
        }
    }
}
