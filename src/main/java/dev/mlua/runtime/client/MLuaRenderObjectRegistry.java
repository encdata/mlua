package dev.mlua.runtime.client;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class MLuaRenderObjectRegistry {
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, MLuaClientRenderManager.RenderObject> objects =
        new ConcurrentHashMap<>();
    private final MLuaRenderOwnershipRegistry ownership = new MLuaRenderOwnershipRegistry();

    int nextId() {
        return MLuaHandleIdAllocator.nextAvailable(this.nextId,
            candidate -> this.objects.containsKey(candidate) || this.ownership.ownerOf(candidate) != null,
            MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS);
    }

    int publish(MLuaClientRenderManager.RenderObject object, MLuaRenderOwnershipRegistry.Owner owner) {
        int id = object.id();
        if (!this.ownership.tryRegister(id, owner, MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS,
                MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS_PER_OWNER)) {
            throw new IllegalStateException("[MLua] RenderService limit reached ("
                + MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS_PER_OWNER + " live objects per addon runtime, "
                + MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS + " total). Destroy unused render objects before creating more.");
        }
        try {
            if (this.objects.putIfAbsent(id, object) != null) {
                throw new IllegalStateException("[MLua] Render object handle was allocated while already in use: " + id);
            }
            return id;
        } catch (RuntimeException | Error failure) {
            this.objects.remove(id, object);
            this.ownership.remove(id);
            throw failure;
        }
    }

    MLuaClientRenderManager.RenderObject get(int id) {
        return this.objects.get(id);
    }

    boolean contains(int id) {
        return this.objects.containsKey(id);
    }

    boolean contains(int id, MLuaClientRenderManager.RenderObject object) {
        return this.objects.get(id) == object;
    }

    java.util.Collection<MLuaClientRenderManager.RenderObject> values() {
        return this.objects.values();
    }

    List<Integer> ids() {
        return this.objects.keySet().stream().sorted().toList();
    }

    MLuaRenderOwnershipRegistry.Owner ownerOf(int id) {
        return this.ownership.ownerOf(id);
    }

    boolean owns(int id, MLuaRenderOwnershipRegistry.Owner owner) {
        return this.ownership.owns(id, owner);
    }

    List<Integer> ids(MLuaRenderOwnershipRegistry.Owner owner) {
        return this.ownership.ids(owner);
    }

    List<Integer> clearOwned(MLuaRenderOwnershipRegistry.Owner owner) {
        return this.ownership.clearOwned(owner);
    }

    List<Integer> clearRuntime(Object runtimeOwner) {
        return this.ownership.clearRuntime(runtimeOwner);
    }

    MLuaClientRenderManager.RenderObject remove(int id) {
        MLuaClientRenderManager.RenderObject removed = this.objects.remove(id);
        this.ownership.remove(id);
        return removed;
    }

    void clearOwners() {
        this.ownership.clear();
    }
}
