package dev.mlua.runtime.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Shared id ownership bookkeeping for client resources with addon and runtime lifetimes. */
final class MLuaOwnedIdRegistry<O> {
    private final ConcurrentHashMap<Integer, O> owners = new ConcurrentHashMap<>();
    private final Map<O, Set<Integer>> idsByOwner = new HashMap<>();
    private final Function<O, Object> runtimeOwner;
    private final String resourceName;

    MLuaOwnedIdRegistry(Function<O, Object> runtimeOwner, String resourceName) {
        this.runtimeOwner = runtimeOwner;
        this.resourceName = resourceName;
    }

    synchronized void register(int id, O owner) {
        if (owners.putIfAbsent(id, owner) != null) {
            throw new IllegalStateException(resourceName + " id is already owned: " + id);
        }
        idsByOwner.computeIfAbsent(owner, ignored -> new HashSet<>()).add(id);
    }

    O ownerOf(int id) { return owners.get(id); }
    boolean owns(int id, O owner) { return owner.equals(owners.get(id)); }
    int size() { return owners.size(); }

    synchronized int countOwned(O owner) {
        Set<Integer> ids = idsByOwner.get(owner);
        return ids == null ? 0 : ids.size();
    }

    synchronized List<Integer> idsOwned(O owner) {
        Set<Integer> owned = idsByOwner.get(owner);
        if (owned == null) return List.of();
        List<Integer> ids = new ArrayList<>(owned);
        ids.sort(Integer::compareTo);
        return List.copyOf(ids);
    }

    synchronized List<Integer> clearOwned(O owner) {
        Set<Integer> owned = idsByOwner.remove(owner);
        if (owned == null) return List.of();
        List<Integer> removed = new ArrayList<>(owned);
        for (int id : removed) owners.remove(id, owner);
        return sorted(removed);
    }

    synchronized List<Integer> clearRuntime(Object runtime) {
        if (runtime == null) return List.of();
        List<Integer> removed = new ArrayList<>();
        owners.forEach((id, actual) -> {
            if (runtimeOwner.apply(actual) == runtime && owners.remove(id, actual)) {
                removeFromOwner(actual, id);
                removed.add(id);
            }
        });
        return sorted(removed);
    }

    synchronized void remove(int id) {
        O previous = owners.remove(id);
        if (previous != null) removeFromOwner(previous, id);
    }

    synchronized void clear() {
        owners.clear();
        idsByOwner.clear();
    }

    private void removeFromOwner(O owner, int id) {
        Set<Integer> ids = idsByOwner.get(owner);
        if (ids == null || !ids.remove(id)) return;
        if (ids.isEmpty()) idsByOwner.remove(owner);
    }

    private static List<Integer> sorted(List<Integer> ids) {
        ids.sort(Integer::compareTo);
        return List.copyOf(ids);
    }
}
