package dev.mlua.runtime.client;

import java.util.List;

/** Tracks the session and mod that created each render handle. */
public final class MLuaRenderOwnershipRegistry {
    public static final int MAX_RENDER_OBJECTS = 8192;
    public static final int MAX_RENDER_OBJECTS_PER_OWNER = 512;
    private static final Owner LEGACY_OWNER = new Owner(null, "");
    private final MLuaOwnedIdRegistry<Owner> registry = new MLuaOwnedIdRegistry<>(
        Owner::runtimeOwner, "Render object");

    public void register(int id, Owner owner) {
        if (!tryRegister(id, owner, MAX_RENDER_OBJECTS, MAX_RENDER_OBJECTS_PER_OWNER)) {
            throw new IllegalStateException("Render object ownership limit reached ("
                + MAX_RENDER_OBJECTS_PER_OWNER + " per owner, " + MAX_RENDER_OBJECTS + " total)");
        }
    }

    public boolean tryRegister(int id, Owner owner, int totalLimit, int ownerLimit) {
        return registry.tryRegister(id, resolve(owner), totalLimit, ownerLimit);
    }

    public int count(Owner owner) { return registry.countOwned(resolve(owner)); }
    public Owner ownerOf(int id) { return registry.ownerOf(id); }
    public boolean owns(int id, Owner owner) { return registry.owns(id, resolve(owner)); }
    public List<Integer> ids(Owner owner) { return registry.idsOwned(resolve(owner)); }
    public List<Integer> clearOwned(Owner owner) { return registry.clearOwned(resolve(owner)); }
    public List<Integer> clearRuntime(Object runtimeOwner) { return registry.clearRuntime(runtimeOwner); }
    public void remove(int id) { registry.remove(id); }
    public void clear() { registry.clear(); }

    private static Owner resolve(Owner owner) { return owner == null ? LEGACY_OWNER : owner; }

    public record Owner(Object runtimeOwner, String ownerModId) {
        public Owner { if (ownerModId == null) ownerModId = ""; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Owner owner
                && runtimeOwner == owner.runtimeOwner()
                && ownerModId.equals(owner.ownerModId());
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(runtimeOwner) + ownerModId.hashCode();
        }
    }
}
