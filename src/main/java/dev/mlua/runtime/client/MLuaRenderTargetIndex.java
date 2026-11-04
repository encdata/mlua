package dev.mlua.runtime.client;

import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Owns the indexes used to find render objects attached to entities and held items. */
final class MLuaRenderTargetIndex {
    private record HeldItemKey(MLuaClientRenderManager.RenderTarget target, String itemId) { }

    private final ConcurrentMap<HeldItemKey, Set<Integer>> heldItems = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, HeldItemKey> heldItemsByObject = new ConcurrentHashMap<>();
    private final ConcurrentMap<MLuaEntityScope, Set<Integer>> entityModels = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, MLuaEntityScope> entitiesByObject = new ConcurrentHashMap<>();
    private final ConcurrentMap<HeldItemKey, OrderedObjects> orderedHeldItems = new ConcurrentHashMap<>();
    private final ConcurrentMap<MLuaEntityScope, OrderedObjects> orderedEntities = new ConcurrentHashMap<>();
    private final AtomicLong orderVersion = new AtomicLong();

    private record OrderedObjects(long version, List<MLuaClientRenderManager.RenderObject> objects) { }

    void updateHeldItem(MLuaRenderObject object) {
        int id = object.id();
        HeldItemKey previous = heldItemsByObject.get(id);

        MLuaClientRenderManager.RenderTarget target = object.target();
        String itemId = object.heldItemId();
        HeldItemKey next = !object.destroyed() && itemId != null
                && (object.type() == MLuaClientRenderManager.RenderType.MESH
                    || object.type() == MLuaClientRenderManager.RenderType.BBMODEL)
                && (target == MLuaClientRenderManager.RenderTarget.FIRST_PERSON_ITEM
                    || target == MLuaClientRenderManager.RenderTarget.THIRD_PERSON_ITEM)
            ? new HeldItemKey(target, itemId) : null;
        if (java.util.Objects.equals(previous, next)) return;
        if (previous != null && heldItemsByObject.remove(id, previous)) {
            remove(heldItems, previous, id);
        }
        if (next != null) {
            heldItems.computeIfAbsent(next, ignored -> ConcurrentHashMap.newKeySet()).add(id);
            heldItemsByObject.put(id, next);
        }
        invalidateOrder();
    }

    List<MLuaClientRenderManager.RenderObject> orderedHeldItemModels(
            MLuaClientRenderManager.RenderTarget target, String itemId) {
        HeldItemKey key = new HeldItemKey(target, itemId);
        Set<Integer> ids = heldItems.get(key);
        if (ids == null || ids.isEmpty()) return List.of();
        for (;;) {
            long version = orderVersion.get();
            OrderedObjects cached = orderedHeldItems.get(key);
            if (cached != null && cached.version() == version) return cached.objects();
            List<MLuaClientRenderManager.RenderObject> objects = orderedObjects(ids);
            if (orderVersion.get() != version) continue;
            List<MLuaClientRenderManager.RenderObject> snapshot = List.copyOf(objects);
            orderedHeldItems.put(key, new OrderedObjects(version, snapshot));
            return snapshot;
        }
    }

    List<MLuaClientRenderManager.RenderObject> orderedEntityModels(MLuaEntityScope scope) {
        if (scope == null) return List.of();
        Set<Integer> ids = entityModelIds(scope);
        if (ids == null || ids.isEmpty()) return List.of();
        for (;;) {
            long version = orderVersion.get();
            OrderedObjects cached = orderedEntities.get(scope);
            if (cached != null && cached.version() == version) return cached.objects();
            List<MLuaClientRenderManager.RenderObject> objects = orderedObjects(ids);
            if (orderVersion.get() != version) continue;
            List<MLuaClientRenderManager.RenderObject> snapshot = List.copyOf(objects);
            orderedEntities.put(scope, new OrderedObjects(version, snapshot));
            return snapshot;
        }
    }

    private static List<MLuaClientRenderManager.RenderObject> orderedObjects(Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<MLuaClientRenderManager.RenderObject> objects = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            MLuaClientRenderManager.RenderObject object = MLuaClientRenderManager.get(id);
            if (object != null) objects.add(object);
        }
        objects.sort(Comparator.comparingInt(MLuaClientRenderManager.RenderObject::zIndex)
            .thenComparingInt(MLuaClientRenderManager.RenderObject::id));
        return objects;
    }

    void invalidateOrder() {
        orderVersion.incrementAndGet();
        orderedHeldItems.clear();
        orderedEntities.clear();
    }

    Set<Integer> entityModelIds(MLuaEntityScope scope) {
        return scope == null ? null : entityModels.get(scope);
    }

    Set<Integer> removeEntityModels(MLuaEntityScope scope) {
        if (scope == null) return null;
        Set<Integer> removed = entityModels.remove(scope);
        if (removed == null) return null;
        invalidateOrder();
        for (Integer id : removed) entitiesByObject.remove(id, scope);
        return Set.copyOf(removed);
    }

    void registerEntityModel(MLuaEntityScope scope, int id) {
        if (scope == null) return;
        MLuaEntityScope previous = entitiesByObject.put(id, scope);
        if (previous != null && !previous.equals(scope)) remove(entityModels, previous, id);
        entityModels.computeIfAbsent(scope, ignored -> ConcurrentHashMap.newKeySet()).add(id);
        if (!scope.equals(previous)) invalidateOrder();
    }

    void unregisterEntityModel(MLuaEntityScope scope, int id) {
        if (scope == null || !entitiesByObject.remove(id, scope)) return;
        remove(entityModels, scope, id);
        invalidateOrder();
    }

    void removeObject(int id) {
        HeldItemKey heldItem = heldItemsByObject.remove(id);
        if (heldItem != null) remove(heldItems, heldItem, id);
        MLuaEntityScope entity = entitiesByObject.remove(id);
        if (entity != null) remove(entityModels, entity, id);
        if (heldItem != null || entity != null) invalidateOrder();
    }

    void clear() {
        heldItems.clear();
        heldItemsByObject.clear();
        entityModels.clear();
        entitiesByObject.clear();
        invalidateOrder();
    }

    private static <K> void remove(ConcurrentMap<K, Set<Integer>> index, K key, int id) {
        index.computeIfPresent(key, (ignored, ids) -> {
            ids.remove(id);
            return ids.isEmpty() ? null : ids;
        });
    }
}
