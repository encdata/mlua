package dev.mlua.runtime.client;

import java.util.List;

/** Tracks runtime session and mod ownership for persistent particle emitters. */
public final class MLuaParticleOwnershipRegistry {
    private static final Owner LEGACY_OWNER = new Owner(null, "");
    private final MLuaOwnedIdRegistry<Owner> registry = new MLuaOwnedIdRegistry<>(
        Owner::runtimeOwner, "Particle emitter");

    public void register(int id, Owner owner) { registry.register(id, resolve(owner)); }
    public Owner ownerOf(int id) { return registry.ownerOf(id); }
    public boolean owns(int id, Owner owner) { return registry.owns(id, resolve(owner)); }
    public int size() { return registry.size(); }
    public int countOwned(Owner owner) { return registry.countOwned(resolve(owner)); }
    public List<Integer> idsOwned(Owner owner) { return registry.idsOwned(resolve(owner)); }
    public List<Integer> clearOwned(Owner owner) { return registry.clearOwned(resolve(owner)); }
    public List<Integer> clearRuntime(Object runtimeOwner) { return registry.clearRuntime(runtimeOwner); }
    public void remove(int id) { registry.remove(id); }
    public void clear() { registry.clear(); }

    private static Owner resolve(Owner owner) { return owner == null ? LEGACY_OWNER : owner; }

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
