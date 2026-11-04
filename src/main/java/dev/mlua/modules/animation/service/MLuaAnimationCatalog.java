package dev.mlua.modules.animation.service;

import dev.mlua.minecraft.animation.MLuaAnimation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.luaj.vm2.LuaError;

/** Runtime-session catalog for addon-owned animation definitions. */
final class MLuaAnimationCatalog {
    static final int MAX_REGISTERED_ANIMATIONS = 4096;
    static final int MAX_REGISTERED_ANIMATIONS_PER_ADDON = 1024;
    private record Entry(String owner, MLuaAnimation animation) { }
    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, Set<String>> idsByOwner = new HashMap<>();

    synchronized boolean register(String id, String owner, MLuaAnimation animation) {
        Entry current = this.entries.get(id);
        if (current != null && !current.owner().equals(owner)) return false;
        if (current == null && this.entries.size() >= MAX_REGISTERED_ANIMATIONS) {
            throw new LuaError("[MLua] Animation:RegisterAnimation reached the runtime limit of "
                + MAX_REGISTERED_ANIMATIONS + " registered animations.");
        }
        Set<String> ownerIds = this.idsByOwner.get(owner);
        if (current == null && ownerIds != null && ownerIds.size() >= MAX_REGISTERED_ANIMATIONS_PER_ADDON) {
            throw new LuaError("[MLua] Animation:RegisterAnimation reached the addon limit of "
                + MAX_REGISTERED_ANIMATIONS_PER_ADDON + " registered animations.");
        }
        Entry installed = new Entry(owner, animation);
        this.entries.put(id, installed);
        this.idsByOwner.computeIfAbsent(owner, ignored -> new TreeSet<>()).add(id);
        return true;
    }

    synchronized MLuaAnimation get(String id) {
        Entry entry = this.entries.get(id);
        return entry == null ? null : entry.animation();
    }

    synchronized boolean contains(String id) { return this.entries.containsKey(id); }

    synchronized boolean remove(String id, String owner) {
        Entry entry = this.entries.get(id);
        if (entry == null || !entry.owner().equals(owner) || !this.entries.remove(id, entry)) return false;
        removeOwnerId(owner, id);
        return true;
    }

    synchronized int clearOwner(String owner) {
        Set<String> ids = this.idsByOwner.remove(owner);
        if (ids == null) return 0;
        ids.forEach(this.entries::remove);
        return ids.size();
    }

    synchronized void clear() {
        this.entries.clear();
        this.idsByOwner.clear();
    }

    synchronized List<String> idsForOwner(String owner) {
        Set<String> ids = this.idsByOwner.get(owner);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    synchronized int countForOwner(String owner) {
        Set<String> ids = this.idsByOwner.get(owner);
        return ids == null ? 0 : ids.size();
    }

    synchronized Map<String, MLuaAnimation> entriesForOwner(String owner) {
        Map<String, MLuaAnimation> result = new TreeMap<>();
        for (String id : idsForOwner(owner)) {
            Entry entry = this.entries.get(id);
            if (entry != null) result.put(id, entry.animation());
        }
        return result;
    }

    private void removeOwnerId(String owner, String id) {
        Set<String> ids = this.idsByOwner.get(owner);
        if (ids == null) return;
        ids.remove(id);
        if (ids.isEmpty()) this.idsByOwner.remove(owner);
    }
}
