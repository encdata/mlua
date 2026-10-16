package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.common.enums.Side;
import dev.mlua.math.MLuaCFrame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.world.entity.Entity;

public final class MLuaAnimationController {
    /** Bounds per-addon animation state attached to any one entity. */
    public static final int MAX_TRACKS_PER_ENTITY_OWNER = 128;
    /** Bounds all retained animation state in the client runtime. */
    public static final int MAX_TRACKS = 8192;
    private record TrackKey(MLuaEntityScope scope, Object runtimeOwner, String addonOwner) { }

    private static final ConcurrentHashMap<TrackKey, List<MLuaAnimationTrack>> TRACKS = new ConcurrentHashMap<>();
    private static final AtomicInteger TRACK_COUNT = new AtomicInteger();
    private static final Map<Object, Long> OWNER_ORDER = new WeakHashMap<>();
    private static long nextOwnerOrder;

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
        MLuaAnimationTrack track = new MLuaAnimationTrack(scope.entityId(), animation, priority);
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
        List<Map.Entry<TrackKey, List<MLuaAnimationTrack>>> entries = TRACKS.entrySet().stream()
                .filter(entry -> entry.getKey().scope().equals(scope))
                .sorted(Comparator.comparingLong((Map.Entry<TrackKey, List<MLuaAnimationTrack>> entry) ->
                    ownerOrder(entry.getKey().runtimeOwner())).thenComparing(entry -> entry.getKey().addonOwner()))
                .toList();
        Set<Object> runtimeOwners = new HashSet<>();
        Map<Object, List<MLuaAnimationTrack>> tracksByRuntime = new HashMap<>();
        for (Map.Entry<TrackKey, List<MLuaAnimationTrack>> entry : entries) {
            TrackKey key = entry.getKey();
            runtimeOwners.add(key.runtimeOwner());
            List<MLuaAnimationTrack> owned = entry.getValue();
            synchronized (owned) {
                // Event callbacks can register or stop tracks on this same entity. Tick a
                // stable snapshot so those reentrant API calls cannot mutate this iteration.
                List<MLuaAnimationTrack> tickSnapshot = List.copyOf(owned);
                tickSnapshot.forEach(track -> track.tick(delta));
                int beforeCleanup = owned.size();
                owned.removeIf(track -> !track.isAlive());
                releaseTracks(beforeCleanup - owned.size());
                if (owned.isEmpty()) {
                    TRACKS.remove(key, owned);
                } else {
                    tracksByRuntime.computeIfAbsent(key.runtimeOwner(), ignored -> new ArrayList<>()).addAll(owned);
                }
            }
        }
        // Animation pose storage is shared by all addons in one runtime, so mix every
        // addon's tracks together once after advancing their independent track lists.
        runtimeOwners.forEach(owner -> {
            List<MLuaAnimationTrack> tracks = tracksByRuntime.get(owner);
            if (tracks == null || tracks.isEmpty()) MLuaBonePose.clearAnimatedPose(scope, owner);
            else blendPose(scope, tracks, owner);
        });
    }

    private static synchronized long ownerOrder(Object owner) {
        if (owner == null) return 0L;
        return OWNER_ORDER.computeIfAbsent(owner, ignored -> ++nextOwnerOrder);
    }

    private static void blendPose(MLuaEntityScope scope, List<MLuaAnimationTrack> tracks, Object runtimeOwner) {
        MLuaBonePose.clearAnimatedPose(scope, runtimeOwner);
        List<MLuaAnimationTrack> sorted = tracks.stream()
                .filter(track -> track.getWeight() > 0.001f)
                .sorted(Comparator.comparingInt((MLuaAnimationTrack track) -> track.getPriority().ordinal())
                .thenComparingInt(MLuaAnimationTrack::trackId))
                .toList();
        Map<String, MLuaCFrame> blended = new HashMap<>();
        Set<String> bones = new HashSet<>();
        for (MLuaAnimationTrack track : sorted) {
            bones.addAll(track.sampledPoseView().keySet());
        }

        for (String bone : bones) {
            MLuaCFrame pose = MLuaCFrame.IDENTITY;
            // Apply lower priorities first. Each higher layer blends over it by its total
            // weight, so fading a track reveals the lower-priority pose smoothly.
            for (AnimationPriority priority : AnimationPriority.values()) {
                MLuaCFrame layerPose = null;
                double totalWeight = 0.0;
                for (MLuaAnimationTrack track : sorted) {
                    if (track.getPriority() != priority) continue;
                    MLuaCFrame trackPose = track.sampledPoseView().get(bone);
                    double trackWeight = track.getWeight();
                    if (trackPose == null || trackWeight <= 0.0) continue;
                    double nextWeight = totalWeight + trackWeight;
                    layerPose = layerPose == null ? trackPose
                            : layerPose.lerp(trackPose, trackWeight / nextWeight);
                    totalWeight = nextWeight;
                }
                if (layerPose != null) pose = pose.lerp(layerPose, Math.min(1.0, totalWeight));
            }
            blended.put(bone, pose);
        }
        blended.forEach((bone, pose) -> MLuaBonePose.setAnimatedPose(scope, bone, pose, runtimeOwner));
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

    public static void clearRuntime(Object runtimeOwner, Side side) {
        if (runtimeOwner == null || side == null) return;
        clearTracks(key -> key.runtimeOwner() == runtimeOwner
                && (side == Side.BOTH || key.scope().side() == Side.BOTH || key.scope().side() == side));
        MLuaBonePose.clearRuntime(runtimeOwner, side);
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
        else blendPose(scope, remaining, runtimeOwner);
    }

    private static void clearTracks(java.util.function.Predicate<TrackKey> matches) {
        clearTracks(matches, null);
    }

    private static void clearTracks(java.util.function.Predicate<TrackKey> matches, Set<MLuaEntityScope> changed) {
        TRACKS.forEach((key, tracks) -> {
            if (!matches.test(key) || !TRACKS.remove(key, tracks)) return;
            synchronized (tracks) {
                int count = tracks.size();
                tracks.forEach(MLuaAnimationTrack::stopImmediate);
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
                tracks.forEach(MLuaAnimationTrack::stopImmediate);
                tracks.clear();
                releaseTracks(count);
            }
        });
        MLuaBonePose.clearAll();
    }

    public static List<Integer> getTrackedEntityIds() {
        return TRACKS.keySet().stream().map(key -> key.scope().entityId()).distinct().collect(Collectors.toList());
    }

    public static List<MLuaEntityScope> getTrackedEntities() {
        return TRACKS.keySet().stream().map(TrackKey::scope).distinct().collect(Collectors.toList());
    }
}
