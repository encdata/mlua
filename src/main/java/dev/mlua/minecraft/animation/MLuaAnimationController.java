package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.common.enums.Side;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.world.entity.Entity;

public final class MLuaAnimationController {
    /** Bounds per-addon animation state attached to any one entity. */
    public static final int MAX_TRACKS_PER_ENTITY_OWNER = 128;
    /** Bounds all retained animation state in the client runtime. */
    public static final int MAX_TRACKS = 8192;
    /** Runtime sessions are ownership tokens, so keys must compare them by identity. */
    private record TrackKey(MLuaEntityScope scope, Object runtimeOwner, String addonOwner) {
        @Override public boolean equals(Object other) {
            return other instanceof TrackKey key
                && scope.equals(key.scope)
                && runtimeOwner == key.runtimeOwner
                && addonOwner.equals(key.addonOwner);
        }

        @Override public int hashCode() {
            int result = scope.hashCode();
            result = 31 * result + System.identityHashCode(runtimeOwner);
            result = 31 * result + addonOwner.hashCode();
            return result;
        }
    }

    private static final ConcurrentHashMap<TrackKey, List<MLuaAnimationTrack>> TRACKS = new ConcurrentHashMap<>();
    private static final AtomicInteger TRACK_COUNT = new AtomicInteger();
    private static final AtomicLong EVENT_BUDGET_ROTATION = new AtomicLong();
    private static final AtomicLong EVENT_SCOPE_ROTATION = new AtomicLong();
    private static final ReferenceQueue<Object> COLLECTED_OWNERS = new ReferenceQueue<>();
    private static final Map<IdentityWeakReference, Long> OWNER_ORDER = new HashMap<>();
    private static long nextOwnerOrder;

    /** Weak keys with identity semantics, matching TrackKey and runtime cleanup. */
    private static final class IdentityWeakReference extends WeakReference<Object> {
        private final int hash;

        private IdentityWeakReference(Object value) {
            super(value);
            hash = System.identityHashCode(value);
        }

        private IdentityWeakReference(Object value, ReferenceQueue<Object> queue) {
            super(value, queue);
            hash = System.identityHashCode(value);
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityWeakReference reference)) return false;
            Object value = get();
            return value != null && value == reference.get();
        }
    }

    private MLuaAnimationController() {}

    public static MLuaAnimationTrack loadAnimation(int entityId, MLuaAnimation animation, AnimationPriority priority) {
        return loadAnimation(MLuaEntityScope.legacy(entityId), animation, priority);
    }

    public static MLuaAnimationTrack loadAnimation(Entity entity, MLuaAnimation animation, AnimationPriority priority) {
        return loadAnimation(MLuaEntityScope.of(entity), animation, priority);
    }

    public static MLuaAnimationTrack loadAnimation(Entity entity, MLuaAnimation animation,
            AnimationPriority priority, Object runtimeOwner) {
        return loadAnimation(MLuaEntityScope.of(entity), animation, priority, runtimeOwner);
    }

    public static MLuaAnimationTrack loadAnimation(Entity entity, MLuaAnimation animation,
            AnimationPriority priority, Object runtimeOwner, String addonOwner) {
        return loadAnimation(MLuaEntityScope.of(entity), animation, priority, runtimeOwner, addonOwner);
    }

    public static MLuaAnimationTrack loadAnimation(MLuaEntityScope scope, MLuaAnimation animation, AnimationPriority priority) {
        return loadAnimation(scope, animation, priority, null);
    }

    public static MLuaAnimationTrack loadAnimation(MLuaEntityScope scope, MLuaAnimation animation,
            AnimationPriority priority, Object runtimeOwner) {
        return loadAnimation(scope, animation, priority, runtimeOwner, "");
    }

    public static MLuaAnimationTrack loadAnimation(MLuaEntityScope scope, MLuaAnimation animation,
            AnimationPriority priority, Object runtimeOwner, String addonOwner) {
        if (scope == null) throw new IllegalArgumentException("Animation needs an entity scope");
        String resolvedAddon = addonOwner == null ? "" : addonOwner.trim();
        ownerOrder(runtimeOwner);
        MLuaAnimationTrack track = new MLuaAnimationTrack(scope.entityId(), animation, priority,
            runtimeOwner, resolvedAddon);
        TrackKey key = new TrackKey(scope, runtimeOwner, resolvedAddon);
        while (true) {
            List<MLuaAnimationTrack> tracks = TRACKS.computeIfAbsent(key, ignored -> new ArrayList<>());
            synchronized (tracks) {
                // A tick or cleanup can remove an empty list between lookup and lock acquisition.
                if (TRACKS.get(key) != tracks) continue;
                if (tracks.size() >= MAX_TRACKS_PER_ENTITY_OWNER) {
                    throw new IllegalStateException("Animation track limit reached for addon '" + resolvedAddon
                        + "' and entity ("
                        + MAX_TRACKS_PER_ENTITY_OWNER + ")");
                }
                if (!reserveTrack()) {
                    // computeIfAbsent may have installed this empty bucket just before the
                    // global cap check. Do not retain one dead bucket per failed load attempt.
                    if (tracks.isEmpty()) TRACKS.remove(key, tracks);
                    throw new IllegalStateException("Client animation track limit reached (" + MAX_TRACKS + ")");
                }
                tracks.add(track);
                return track;
            }
        }
    }

    private static boolean reserveTrack() {
        while (true) {
            int count = TRACK_COUNT.get();
            if (count >= MAX_TRACKS) return false;
            if (TRACK_COUNT.compareAndSet(count, count + 1)) return true;
        }
    }

    private static void releaseTracks(int count) {
        if (count > 0) TRACK_COUNT.addAndGet(-count);
    }

    public static void tickEntity(int entityId, float delta) {
        tickEntity(MLuaEntityScope.legacy(entityId), delta);
    }

    public static void tickEntity(Entity entity, float delta) {
        tickEntity(MLuaEntityScope.of(entity), delta);
    }

    public static void tickEntity(MLuaEntityScope scope, float delta) {
        if (scope == null) return;
        List<Map.Entry<TrackKey, List<MLuaAnimationTrack>>> entries = new ArrayList<>();
        TRACKS.forEach((key, tracks) -> {
            if (key.scope().equals(scope)) entries.add(Map.entry(key, tracks));
        });
        tickScope(scope, entries, delta, null, new IdentityHashMap<>());
    }

    /** Ticks all supplied entities after grouping their tracks in one runtime-wide map pass. */
    public static void tickEntities(Collection<MLuaEntityScope> scopes, float delta) {
        if (scopes == null || scopes.isEmpty()) return;
        Set<MLuaEntityScope> requested = new HashSet<>();
        for (MLuaEntityScope scope : scopes) if (scope != null) requested.add(scope);
        if (requested.isEmpty()) return;

        Map<MLuaEntityScope, List<Map.Entry<TrackKey, List<MLuaAnimationTrack>>>> grouped = new HashMap<>();
        TRACKS.forEach((key, tracks) -> {
            if (requested.contains(key.scope())) {
                grouped.computeIfAbsent(key.scope(), ignored -> new ArrayList<>()).add(Map.entry(key, tracks));
            }
        });
        List<MLuaEntityScope> orderedScopes = requested.stream().sorted(Comparator.comparing(MLuaEntityScope::dimension)
                .thenComparing(scope -> scope.side().name())
                .thenComparing(MLuaEntityScope::uuid)
                .thenComparingInt(MLuaEntityScope::entityId)).toList();
        if (orderedScopes.size() > 1) {
            int start = (int) Math.floorMod(EVENT_SCOPE_ROTATION.getAndIncrement(), orderedScopes.size());
            if (start > 0) orderedScopes = new ArrayList<>(orderedScopes);
            if (start > 0) java.util.Collections.rotate(orderedScopes, -start);
        }
        MLuaAnimationEventState.EventBudget batchBudget = new MLuaAnimationEventState.EventBudget(
            MLuaAnimationTrack.MAX_EVENT_CALLBACKS_PER_BATCH_TICK,
            MLuaAnimationTrack.MAX_EVENT_CALLBACKS_PER_ADDON_BATCH_TICK, null);
        Map<Object, Long> ownerOrders = new IdentityHashMap<>();
        for (MLuaEntityScope scope : orderedScopes) {
            tickScope(scope, grouped.getOrDefault(scope, List.of()), delta, batchBudget, ownerOrders);
        }
    }

    private static void tickScope(MLuaEntityScope scope,
            List<Map.Entry<TrackKey, List<MLuaAnimationTrack>>> entries, float delta,
            MLuaAnimationEventState.EventBudget batchBudget, Map<Object, Long> ownerOrders) {
        if (entries.isEmpty()) return;
        for (Map.Entry<TrackKey, List<MLuaAnimationTrack>> entry : entries) {
            Object owner = entry.getKey().runtimeOwner();
            ownerOrders.computeIfAbsent(owner, MLuaAnimationController::ownerOrder);
        }
        entries.sort(Comparator.comparingLong((Map.Entry<TrackKey, List<MLuaAnimationTrack>> entry) ->
            ownerOrders.get(entry.getKey().runtimeOwner())).thenComparing(entry -> entry.getKey().addonOwner()));
        Map<String, List<Map.Entry<TrackKey, List<MLuaAnimationTrack>>>> entriesByAddon = new LinkedHashMap<>();
        entries.stream().map(entry -> entry.getKey().addonOwner()).distinct().sorted()
            .forEach(addon -> entriesByAddon.put(addon, new ArrayList<>()));
        for (Map.Entry<TrackKey, List<MLuaAnimationTrack>> entry : entries) {
            entriesByAddon.get(entry.getKey().addonOwner()).add(entry);
        }
        List<List<Map.Entry<TrackKey, List<MLuaAnimationTrack>>>> addonGroups = new ArrayList<>(entriesByAddon.values());
        if (addonGroups.size() > 1) {
            int start = (int) Math.floorMod(EVENT_BUDGET_ROTATION.getAndIncrement(), addonGroups.size());
            if (start > 0) java.util.Collections.rotate(addonGroups, -start);
        }
        Set<Object> runtimeOwners = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Object, List<MLuaAnimationTrack>> tracksByRuntime = new IdentityHashMap<>();
        MLuaAnimationEventState.EventBudget eventBudget = new MLuaAnimationEventState.EventBudget(
            MLuaAnimationTrack.MAX_EVENT_CALLBACKS_PER_ENTITY_TICK,
            MLuaAnimationTrack.MAX_EVENT_CALLBACKS_PER_ADDON_TICK, batchBudget);
        for (List<Map.Entry<TrackKey, List<MLuaAnimationTrack>>> addonEntries : addonGroups) {
          for (Map.Entry<TrackKey, List<MLuaAnimationTrack>> entry : addonEntries) {
            TrackKey key = entry.getKey();
            if (TRACKS.get(key) != entry.getValue()) continue;
            runtimeOwners.add(key.runtimeOwner());
            List<MLuaAnimationTrack> owned = entry.getValue();
            synchronized (owned) {
                // Event callbacks can register or stop tracks on this same entity. Tick a
                // stable snapshot so those reentrant API calls cannot mutate this iteration.
                List<MLuaAnimationTrack> tickSnapshot = List.copyOf(owned);
                tickSnapshot.forEach(track -> track.tick(delta, eventBudget, key.addonOwner()));
                int released = 0;
                for (var iterator = owned.iterator(); iterator.hasNext();) {
                    MLuaAnimationTrack track = iterator.next();
                    if (track.isAlive()) continue;
                    track.release();
                    iterator.remove();
                    released++;
                }
                releaseTracks(released);
                if (owned.isEmpty()) {
                    TRACKS.remove(key, owned);
                } else {
                    tracksByRuntime.computeIfAbsent(key.runtimeOwner(), ignored -> new ArrayList<>()).addAll(owned);
                }
            }
          }
        }
        // Animation pose storage is shared by all addons in one runtime, so mix every
        // addon's tracks together once after advancing their independent track lists.
        runtimeOwners.forEach(owner -> {
            List<MLuaAnimationTrack> tracks = tracksByRuntime.get(owner);
            if (tracks == null || tracks.isEmpty()) MLuaBonePose.clearAnimatedPose(scope, owner);
            else MLuaAnimationPoseBlender.blend(scope, tracks, owner);
        });
    }

    private static synchronized long ownerOrder(Object owner) {
        if (owner == null) return 0L;
        drainCollectedOwners();
        IdentityWeakReference lookup = new IdentityWeakReference(owner);
        Long order = OWNER_ORDER.get(lookup);
        if (order != null) return order;
        long assigned = ++nextOwnerOrder;
        OWNER_ORDER.put(new IdentityWeakReference(owner, COLLECTED_OWNERS), assigned);
        return assigned;
    }

    private static void drainCollectedOwners() {
        IdentityWeakReference collected;
        while ((collected = (IdentityWeakReference) COLLECTED_OWNERS.poll()) != null) {
            OWNER_ORDER.remove(collected);
        }
    }

    private static synchronized void removeOwnerOrder(Object owner) {
        if (owner == null) return;
        drainCollectedOwners();
        OWNER_ORDER.remove(new IdentityWeakReference(owner));
    }

    public static List<MLuaAnimationTrack> getTracks(int entityId) {
        return getTracks(MLuaEntityScope.legacy(entityId));
    }

    public static List<MLuaAnimationTrack> getTracks(Entity entity) {
        return getTracks(MLuaEntityScope.of(entity));
    }

    public static List<MLuaAnimationTrack> getTracks(Entity entity, Object runtimeOwner) {
        return getTracks(MLuaEntityScope.of(entity), runtimeOwner);
    }

    public static List<MLuaAnimationTrack> getTracks(MLuaEntityScope scope) {
        return tracksForScope(scope);
    }

    public static List<MLuaAnimationTrack> getTracks(MLuaEntityScope scope, Object runtimeOwner) {
        List<MLuaAnimationTrack> result = new ArrayList<>();
        TRACKS.forEach((key, tracks) -> {
            if (!key.scope().equals(scope) || key.runtimeOwner() != runtimeOwner) return;
            synchronized (tracks) { result.addAll(tracks); }
        });
        result.sort(Comparator.comparingInt(MLuaAnimationTrack::trackId));
        return result;
    }

    public static List<MLuaAnimationTrack> getTracks(MLuaEntityScope scope, Object runtimeOwner,
            String addonOwner) {
        if (scope == null || runtimeOwner == null || addonOwner == null) return List.of();
        String resolvedAddon = addonOwner.trim();
        TrackKey key = new TrackKey(scope, runtimeOwner, resolvedAddon);
        List<MLuaAnimationTrack> tracks = TRACKS.get(key);
        if (tracks == null) return List.of();
        synchronized (tracks) {
            return List.copyOf(tracks);
        }
    }

    private static List<MLuaAnimationTrack> tracksForScope(MLuaEntityScope scope) {
        List<MLuaAnimationTrack> result = new ArrayList<>();
        TRACKS.forEach((key, tracks) -> {
            if (key.scope().equals(scope)) synchronized (tracks) { result.addAll(tracks); }
        });
        return result;
    }

    public static List<MLuaAnimationTrack> getTracksByPriority(int entityId, AnimationPriority priority) {
        return getTracksByPriority(MLuaEntityScope.legacy(entityId), priority);
    }

    public static List<MLuaAnimationTrack> getTracksByPriority(Entity entity, AnimationPriority priority) {
        return getTracksByPriority(MLuaEntityScope.of(entity), priority);
    }

    private static List<MLuaAnimationTrack> getTracksByPriority(MLuaEntityScope scope, AnimationPriority priority) {
        return tracksForScope(scope).stream().filter(track -> track.getPriority() == priority).collect(Collectors.toList());
    }

    public static void stopAll(int entityId) {
        stopAll(MLuaEntityScope.legacy(entityId));
    }

    public static void stopAll(Entity entity) {
        stopAll(MLuaEntityScope.of(entity));
    }

    public static void stopAll(Entity entity, Object runtimeOwner) {
        stopAll(MLuaEntityScope.of(entity), runtimeOwner);
    }

    public static void stopAll(MLuaEntityScope scope) {
        clearTracks(key -> key.scope().equals(scope));
        MLuaBonePose.clearPose(scope);
    }

    public static void stopAll(MLuaEntityScope scope, Object runtimeOwner) {
        clearTracks(key -> key.scope().equals(scope) && key.runtimeOwner() == runtimeOwner);
        MLuaBonePose.clearAnimatedPose(scope, runtimeOwner);
    }

    /** Stops one addon's tracks on this entity without disturbing another addon's tracks or poses. */
    public static void stopAll(MLuaEntityScope scope, Object runtimeOwner, String addonOwner) {
        if (scope == null || runtimeOwner == null || addonOwner == null) return;
        String resolvedAddon = addonOwner.trim();
        clearTracks(key -> key.scope().equals(scope) && key.runtimeOwner() == runtimeOwner
            && key.addonOwner().equals(resolvedAddon));
        reblend(scope, runtimeOwner);
        MLuaBonePose.clearPose(scope, runtimeOwner, resolvedAddon);
    }

    /** Immediately releases one track owned by an addon and rebuilds that runtime's entity pose. */
    public static boolean unloadTrack(MLuaEntityScope scope, int trackId, Object runtimeOwner,
                                     String addonOwner) {
        if (scope == null || runtimeOwner == null || addonOwner == null) return false;
        TrackKey key = new TrackKey(scope, runtimeOwner, addonOwner);
        List<MLuaAnimationTrack> tracks = TRACKS.get(key);
        if (tracks == null) return false;
        MLuaAnimationTrack removed = null;
        synchronized (tracks) {
            if (TRACKS.get(key) != tracks) return false;
            for (var iterator = tracks.iterator(); iterator.hasNext();) {
                MLuaAnimationTrack candidate = iterator.next();
                if (candidate.trackId() != trackId) continue;
                iterator.remove();
                removed = candidate;
                break;
            }
            if (removed == null) return false;
            removed.release();
            releaseTracks(1);
            if (tracks.isEmpty()) TRACKS.remove(key, tracks);
        }
        reblend(scope, runtimeOwner);
        return true;
    }

    public static void clearRuntime(Object runtimeOwner, Side side) {
        if (runtimeOwner == null || side == null) return;
        clearTracks(key -> key.runtimeOwner() == runtimeOwner
                && (side == Side.BOTH || key.scope().side() == Side.BOTH || key.scope().side() == side));
        MLuaBonePose.clearRuntime(runtimeOwner, side);
        if (TRACKS.keySet().stream().noneMatch(key -> key.runtimeOwner() == runtimeOwner)) {
            removeOwnerOrder(runtimeOwner);
        }
    }

    /** Stops one addon's tracks while retaining other addon's animation layers in the runtime. */
    public static void clearOwned(Object runtimeOwner, String addonOwner, Side side) {
        if (runtimeOwner == null || addonOwner == null || addonOwner.isBlank() || side == null) return;
        Set<MLuaEntityScope> changed = new HashSet<>();
        clearTracks(key -> key.runtimeOwner() == runtimeOwner && key.addonOwner().equals(addonOwner)
            && (side == Side.BOTH || key.scope().side() == Side.BOTH || key.scope().side() == side), changed);
        changed.forEach(scope -> reblend(scope, runtimeOwner));
    }

    private static void reblend(MLuaEntityScope scope, Object runtimeOwner) {
        List<MLuaAnimationTrack> remaining = getTracks(scope, runtimeOwner);
        if (remaining.isEmpty()) MLuaBonePose.clearAnimatedPose(scope, runtimeOwner);
        else MLuaAnimationPoseBlender.blend(scope, remaining, runtimeOwner);
    }

    private static void clearTracks(java.util.function.Predicate<TrackKey> matches) {
        clearTracks(matches, null);
    }

    private static void clearTracks(java.util.function.Predicate<TrackKey> matches, Set<MLuaEntityScope> changed) {
        TRACKS.forEach((key, tracks) -> {
            if (!matches.test(key) || !TRACKS.remove(key, tracks)) return;
            synchronized (tracks) {
                int count = tracks.size();
                tracks.forEach(MLuaAnimationTrack::release);
                tracks.clear();
                releaseTracks(count);
            }
            MLuaBonePose.clearAnimatedPose(key.scope(), key.runtimeOwner());
            if (changed != null) changed.add(key.scope());
        });
    }

    public static void removeEntity(int entityId) {
        Set<MLuaEntityScope> removed = new HashSet<>();
        clearTracks(key -> key.scope().entityId() == entityId, removed);
        removed.forEach(MLuaBonePose::clearPose);
        MLuaBonePose.clearPose(entityId);
    }

    public static void removeEntity(Entity entity) {
        removeEntity(MLuaEntityScope.of(entity));
    }

    public static void removeEntity(MLuaEntityScope scope) {
        clearTracks(key -> key.scope().equals(scope));
        MLuaBonePose.clearPose(scope);
    }

    public static void clearAll() {
        TRACKS.forEach((key, tracks) -> {
            if (!TRACKS.remove(key, tracks)) return;
            synchronized (tracks) {
                int count = tracks.size();
                tracks.forEach(MLuaAnimationTrack::release);
                tracks.clear();
                releaseTracks(count);
            }
        });
        MLuaBonePose.clearAll();
        synchronized (MLuaAnimationController.class) {
            OWNER_ORDER.clear();
            while (COLLECTED_OWNERS.poll() != null) { }
            nextOwnerOrder = 0L;
        }
    }

    /** Releases animation state for one logical side, preserving other live worlds and sessions. */
    public static void clearSide(Side side) {
        if (side == null) return;
        java.util.function.Predicate<TrackKey> belongsToSide = key -> side == Side.BOTH
            || key.scope().side() == side;
        Set<Object> affectedOwners = Collections.newSetFromMap(new IdentityHashMap<>());
        TRACKS.keySet().stream().filter(belongsToSide).map(TrackKey::runtimeOwner)
            .filter(java.util.Objects::nonNull).forEach(affectedOwners::add);
        clearTracks(belongsToSide);
        MLuaBonePose.clearSide(side);
        for (Object owner : affectedOwners) {
            if (TRACKS.keySet().stream().noneMatch(key -> key.runtimeOwner() == owner)) {
                removeOwnerOrder(owner);
            }
        }
    }

    public static List<Integer> getTrackedEntityIds() {
        return TRACKS.keySet().stream().map(key -> key.scope().entityId()).distinct().collect(Collectors.toList());
    }

    public static List<MLuaEntityScope> getTrackedEntities() {
        return TRACKS.keySet().stream().map(TrackKey::scope).distinct().collect(Collectors.toList());
    }
}
