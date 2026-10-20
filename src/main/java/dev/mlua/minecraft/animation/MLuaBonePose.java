package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.Side;
import dev.mlua.math.MLuaCFrame;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;

public final class MLuaBonePose {
    /** Runtime sessions and pose sources are ownership tokens and compare by identity. */
    private record PoseKey(MLuaEntityScope scope, Object runtimeOwner, String addonOwner, Object source) {
        @Override public boolean equals(Object other) {
            return other instanceof PoseKey key
                && scope.equals(key.scope)
                && runtimeOwner == key.runtimeOwner
                && addonOwner.equals(key.addonOwner)
                && source == key.source;
        }

        @Override public int hashCode() {
            int result = scope.hashCode();
            result = 31 * result + System.identityHashCode(runtimeOwner);
            result = 31 * result + addonOwner.hashCode();
            result = 31 * result + System.identityHashCode(source);
            return result;
        }
    }
    private record PoseEntry(MLuaCFrame pose, long revision) { }

    private static final Object MANUAL_SOURCE = new Object();
    private static final Object SYNCED_SOURCE = new Object();
    static final Object ANIMATION_SOURCE = new Object();
    private static final AtomicLong REVISIONS = new AtomicLong();
    private static final ConcurrentHashMap<PoseKey, ConcurrentHashMap<String, PoseEntry>> POSES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PoseKey, Map<String, PoseEntry>> PREVIOUS_ANIMATION_POSES = new ConcurrentHashMap<>();

    public static final String BONE_HEAD = "head";
    public static final String BONE_BODY = "body";
    public static final String BONE_RIGHT_ARM = "rightArm";
    public static final String BONE_LEFT_ARM = "leftArm";
    public static final String BONE_RIGHT_LEG = "rightLeg";
    public static final String BONE_LEFT_LEG = "leftLeg";

    private MLuaBonePose() {}

    public static void setPose(int entityId, String bone, MLuaCFrame cframe) {
        setPose(MLuaEntityScope.legacy(entityId), bone, cframe);
    }

    public static void setPose(Entity entity, String bone, MLuaCFrame cframe) {
        setPose(MLuaEntityScope.of(entity), bone, cframe);
    }

    public static void setPose(Entity entity, String bone, MLuaCFrame cframe, Object runtimeOwner) {
        setPose(MLuaEntityScope.of(entity), bone, cframe, runtimeOwner);
    }

    public static void setPose(MLuaEntityScope scope, String bone, MLuaCFrame cframe) {
        setPose(scope, bone, cframe, null);
    }

    public static void setPose(MLuaEntityScope scope, String bone, MLuaCFrame cframe, Object runtimeOwner) {
        setPose(scope, bone, cframe, runtimeOwner, "");
    }

    public static void setPose(MLuaEntityScope scope, String bone, MLuaCFrame cframe,
                               Object runtimeOwner, String addonOwner) {
        setPose(scope, bone, cframe, runtimeOwner, normalizeAddonOwner(addonOwner), MANUAL_SOURCE);
    }

    static void setAnimatedPose(MLuaEntityScope scope, String bone, MLuaCFrame cframe, Object runtimeOwner) {
        setPose(scope, bone, cframe, runtimeOwner, "", ANIMATION_SOURCE);
    }

    /** Captures the completed pose before the next animation tick is blended. */
    static void beginAnimatedUpdate(MLuaEntityScope scope, Object runtimeOwner) {
        PoseKey key = new PoseKey(scope, runtimeOwner, "", ANIMATION_SOURCE);
        Map<String, PoseEntry> current = POSES.get(key);
        if (current == null || current.isEmpty()) PREVIOUS_ANIMATION_POSES.remove(key);
        else PREVIOUS_ANIMATION_POSES.put(key, Map.copyOf(current));
    }

    static void clearCurrentAnimatedPose(MLuaEntityScope scope, Object runtimeOwner) {
        POSES.remove(new PoseKey(scope, runtimeOwner, "", ANIMATION_SOURCE));
    }

    private static void setPose(MLuaEntityScope scope, String bone, MLuaCFrame cframe,
                                Object runtimeOwner, String addonOwner, Object source) {
        PoseKey key = new PoseKey(scope, runtimeOwner, addonOwner, source);
        POSES.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
                .put(bone, new PoseEntry(cframe, REVISIONS.incrementAndGet()));
    }

    /** Replaces the pose snapshot synchronized by the server without touching client-local pose sources. */
    public static void replaceSyncedPose(MLuaEntityScope scope, Map<String, MLuaCFrame> poses) {
        if (scope == null) return;
        PoseKey key = new PoseKey(scope, null, "", SYNCED_SOURCE);
        if (poses == null || poses.isEmpty()) {
            POSES.remove(key);
            return;
        }

        ConcurrentHashMap<String, PoseEntry> snapshot = new ConcurrentHashMap<>();
        poses.forEach((bone, pose) -> {
            if (bone == null || bone.isBlank() || pose == null) return;
            snapshot.put(bone, new PoseEntry(pose, REVISIONS.incrementAndGet()));
        });
        if (snapshot.isEmpty()) POSES.remove(key);
        else POSES.put(key, snapshot);
    }

    public static Map<String, MLuaCFrame> getPose(int entityId) {
        return getPose(MLuaEntityScope.legacy(entityId));
    }

    public static Map<String, MLuaCFrame> getPose(Entity entity) {
        return getPose(MLuaEntityScope.of(entity));
    }

    public static Map<String, MLuaCFrame> getPose(Entity entity, Object runtimeOwner) {
        return getPose(MLuaEntityScope.of(entity), runtimeOwner);
    }

    public static Map<String, MLuaCFrame> getPose(MLuaEntityScope scope) {
        return mergedPose(scope, null, false);
    }

    public static Map<String, MLuaCFrame> getPose(MLuaEntityScope scope, Object runtimeOwner) {
        return mergedPose(scope, runtimeOwner, true);
    }

    /** Returns the render pose interpolated between the last two animation ticks. */
    public static Map<String, MLuaCFrame> getRenderPose(MLuaEntityScope scope) {
        Float partialTick = dev.mlua.runtime.client.MLuaRenderCallbacks.currentClientPartialTick();
        return getRenderPose(scope, partialTick == null ? 1.0f : partialTick);
    }

    public static Map<String, MLuaCFrame> getRenderPose(MLuaEntityScope scope, float partialTick) {
        float alpha = Float.isFinite(partialTick) ? Math.max(0.0f, Math.min(1.0f, partialTick)) : 1.0f;
        Map<String, PoseEntry> latest = new HashMap<>();
        POSES.forEach((key, poses) -> {
            if (!key.scope().equals(scope)) return;
            Map<String, PoseEntry> previous = key.source() == ANIMATION_SOURCE
                ? PREVIOUS_ANIMATION_POSES.getOrDefault(key, Map.of()) : Map.of();
            poses.forEach((bone, current) -> {
                PoseEntry old = previous.get(bone);
                MLuaCFrame renderPose = old == null ? current.pose() : old.pose().lerp(current.pose(), alpha);
                PoseEntry interpolated = new PoseEntry(renderPose, current.revision());
                latest.merge(bone, interpolated,
                    (left, right) -> left.revision() >= right.revision() ? left : right);
            });
        });
        Map<String, MLuaCFrame> result = new HashMap<>();
        latest.forEach((bone, entry) -> result.put(bone, entry.pose()));
        return Map.copyOf(result);
    }

    private static Map<String, MLuaCFrame> mergedPose(MLuaEntityScope scope, Object runtimeOwner, boolean filterOwner) {
        Map<String, PoseEntry> latest = new HashMap<>();
        POSES.forEach((key, pose) -> {
            if (!key.scope().equals(scope) || filterOwner && key.runtimeOwner() != null
                    && key.runtimeOwner() != runtimeOwner) return;
            pose.forEach((bone, entry) -> latest.merge(bone, entry,
                    (left, right) -> left.revision() >= right.revision() ? left : right));
        });
        Map<String, MLuaCFrame> result = new HashMap<>();
        latest.forEach((bone, entry) -> result.put(bone, entry.pose()));
        return Map.copyOf(result);
    }

    public static MLuaCFrame getBonePose(int entityId, String bone) {
        return getBonePose(MLuaEntityScope.legacy(entityId), bone);
    }

    public static MLuaCFrame getBonePose(Entity entity, String bone) {
        return getBonePose(MLuaEntityScope.of(entity), bone);
    }

    public static MLuaCFrame getBonePose(Entity entity, String bone, Object runtimeOwner) {
        return getBonePose(MLuaEntityScope.of(entity), bone, runtimeOwner);
    }

    public static MLuaCFrame getBonePose(MLuaEntityScope scope, String bone) {
        return getPose(scope).get(bone);
    }

    public static MLuaCFrame getBonePose(MLuaEntityScope scope, String bone, Object runtimeOwner) {
        return getPose(scope, runtimeOwner).get(bone);
    }

    public static void clearBonePose(int entityId, String bone) {
        clearBonePose(MLuaEntityScope.legacy(entityId), bone);
    }

    public static void clearBonePose(Entity entity, String bone) {
        clearBonePose(MLuaEntityScope.of(entity), bone);
    }

    public static void clearBonePose(Entity entity, String bone, Object runtimeOwner) {
        clearBonePose(MLuaEntityScope.of(entity), bone, runtimeOwner);
    }

    public static void clearBonePose(MLuaEntityScope scope, String bone) {
        clearBonePose(scope, bone, null, false);
    }

    public static void clearBonePose(MLuaEntityScope scope, String bone, Object runtimeOwner) {
        clearBonePose(scope, bone, runtimeOwner, true);
    }

    public static void clearBonePose(MLuaEntityScope scope, String bone, Object runtimeOwner, String addonOwner) {
        String owner = normalizeAddonOwner(addonOwner);
        POSES.forEach((key, pose) -> {
            if (key.scope().equals(scope) && key.runtimeOwner() == runtimeOwner
                    && key.addonOwner().equals(owner) && key.source() == MANUAL_SOURCE) {
                pose.remove(bone);
                if (pose.isEmpty()) POSES.remove(key, pose);
            }
        });
    }

    private static void clearBonePose(MLuaEntityScope scope, String bone, Object runtimeOwner, boolean filterOwner) {
        POSES.forEach((key, pose) -> {
            if (key.scope().equals(scope) && (!filterOwner || key.runtimeOwner() == runtimeOwner)) {
                pose.remove(bone);
                if (pose.isEmpty()) POSES.remove(key, pose);
                if (key.source() == ANIMATION_SOURCE) {
                    PREVIOUS_ANIMATION_POSES.computeIfPresent(key, (ignored, previous) -> {
                        if (!previous.containsKey(bone)) return previous;
                        Map<String, PoseEntry> remaining = new HashMap<>(previous);
                        remaining.remove(bone);
                        return remaining.isEmpty() ? null : Map.copyOf(remaining);
                    });
                }
            }
        });
    }

    public static void clearPose(int entityId) {
        clearPose(MLuaEntityScope.legacy(entityId));
    }

    public static void clearPose(Entity entity) {
        clearPose(MLuaEntityScope.of(entity));
    }

    public static void clearPose(Entity entity, Object runtimeOwner) {
        clearPose(MLuaEntityScope.of(entity), runtimeOwner);
    }

    public static void clearPose(MLuaEntityScope scope) {
        POSES.keySet().removeIf(key -> key.scope().equals(scope));
        PREVIOUS_ANIMATION_POSES.keySet().removeIf(key -> key.scope().equals(scope));
    }

    public static void clearPose(MLuaEntityScope scope, Object runtimeOwner) {
        POSES.keySet().removeIf(key -> key.scope().equals(scope)
                && key.runtimeOwner() == runtimeOwner);
        PREVIOUS_ANIMATION_POSES.keySet().removeIf(key -> key.scope().equals(scope)
                && key.runtimeOwner() == runtimeOwner);
    }

    /** Removes pose state for every entity scope in an unloaded dimension. */
    public static void clearDimension(String dimension, Side side) {
        if (dimension == null || dimension.isBlank() || side == null) return;
        java.util.function.Predicate<PoseKey> belongsToDimension = key -> {
            MLuaEntityScope scope = key.scope();
            return scope.dimension().equals(dimension)
                && (side == Side.BOTH || scope.side() == Side.BOTH || scope.side() == side);
        };
        POSES.keySet().removeIf(belongsToDimension);
        PREVIOUS_ANIMATION_POSES.keySet().removeIf(belongsToDimension);
    }

    /** Removes pose state for one logical side without touching the integrated server's entities. */
    public static void clearSide(Side side) {
        if (side == null) return;
        java.util.function.Predicate<PoseKey> belongsToSide = key -> side == Side.BOTH
            || key.scope().side() == side;
        POSES.keySet().removeIf(belongsToSide);
        PREVIOUS_ANIMATION_POSES.keySet().removeIf(belongsToSide);
    }

    /** Clears only one addon's manual overrides, retaining its animation tracks and every other owner's pose. */
    public static void clearPose(MLuaEntityScope scope, Object runtimeOwner, String addonOwner) {
        String owner = normalizeAddonOwner(addonOwner);
        POSES.keySet().removeIf(key -> key.scope().equals(scope) && key.runtimeOwner() == runtimeOwner
            && key.addonOwner().equals(owner) && key.source() == MANUAL_SOURCE);
    }

    static void clearAnimatedPose(MLuaEntityScope scope, Object runtimeOwner) {
        PoseKey key = new PoseKey(scope, runtimeOwner, "", ANIMATION_SOURCE);
        POSES.remove(key);
        PREVIOUS_ANIMATION_POSES.remove(key);
    }

    public static void clearRuntime(Object runtimeOwner, Side side) {
        if (runtimeOwner == null || side == null) return;
        java.util.function.Predicate<PoseKey> matches = key -> key.runtimeOwner() == runtimeOwner
                && (side == Side.BOTH || key.scope().side() == Side.BOTH || key.scope().side() == side);
        POSES.keySet().removeIf(matches);
        PREVIOUS_ANIMATION_POSES.keySet().removeIf(matches);
    }

    /** Removes an addon's manual overrides on its runtime side during script cleanup. */
    public static void clearOwned(Object runtimeOwner, String addonOwner, Side side) {
        if (runtimeOwner == null || addonOwner == null || addonOwner.isBlank() || side == null) return;
        POSES.keySet().removeIf(key -> key.runtimeOwner() == runtimeOwner && key.addonOwner().equals(addonOwner)
            && key.source() == MANUAL_SOURCE
            && (side == Side.BOTH || key.scope().side() == Side.BOTH || key.scope().side() == side));
    }

    private static String normalizeAddonOwner(String addonOwner) {
        return addonOwner == null ? "" : addonOwner;
    }

    public static void clearAll() {
        POSES.clear();
        PREVIOUS_ANIMATION_POSES.clear();
    }
}
