package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.Side;
import dev.mlua.math.MLuaCFrame;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;

public final class MLuaBonePose {
    private record PoseKey(MLuaEntityScope scope, Object runtimeOwner, Object source) { }
    private record PoseEntry(MLuaCFrame pose, long revision) { }

    private static final Object MANUAL_SOURCE = new Object();
    static final Object ANIMATION_SOURCE = new Object();
    private static final AtomicLong REVISIONS = new AtomicLong();
    private static final ConcurrentHashMap<PoseKey, ConcurrentHashMap<String, PoseEntry>> POSES = new ConcurrentHashMap<>();

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
        setPose(scope, bone, cframe, runtimeOwner, MANUAL_SOURCE);
    }

    static void setAnimatedPose(MLuaEntityScope scope, String bone, MLuaCFrame cframe, Object runtimeOwner) {
        setPose(scope, bone, cframe, runtimeOwner, ANIMATION_SOURCE);
    }

    private static void setPose(MLuaEntityScope scope, String bone, MLuaCFrame cframe, Object runtimeOwner, Object source) {
        PoseKey key = new PoseKey(scope, runtimeOwner, source);
        POSES.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
                .put(bone, new PoseEntry(cframe, REVISIONS.incrementAndGet()));
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

    private static Map<String, MLuaCFrame> mergedPose(MLuaEntityScope scope, Object runtimeOwner, boolean filterOwner) {
        Map<String, PoseEntry> latest = new HashMap<>();
        POSES.forEach((key, pose) -> {
            if (!key.scope().equals(scope) || filterOwner && key.runtimeOwner() != null
                    && !java.util.Objects.equals(key.runtimeOwner(), runtimeOwner)) return;
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

    private static void clearBonePose(MLuaEntityScope scope, String bone, Object runtimeOwner, boolean filterOwner) {
        POSES.forEach((key, pose) -> {
            if (key.scope().equals(scope) && (!filterOwner || java.util.Objects.equals(key.runtimeOwner(), runtimeOwner))) {
                pose.remove(bone);
                if (pose.isEmpty()) POSES.remove(key, pose);
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
    }

    public static void clearPose(MLuaEntityScope scope, Object runtimeOwner) {
        POSES.keySet().removeIf(key -> key.scope().equals(scope)
                && java.util.Objects.equals(key.runtimeOwner(), runtimeOwner));
    }

    static void clearAnimatedPose(MLuaEntityScope scope, Object runtimeOwner) {
        POSES.remove(new PoseKey(scope, runtimeOwner, ANIMATION_SOURCE));
    }

    public static void clearRuntime(Object runtimeOwner, Side side) {
        if (runtimeOwner == null || side == null) return;
        POSES.keySet().removeIf(key -> key.runtimeOwner() == runtimeOwner
                && (side == Side.BOTH || key.scope().side() == Side.BOTH || key.scope().side() == side));
    }

    public static void clearAll() {
        POSES.clear();
    }
}
