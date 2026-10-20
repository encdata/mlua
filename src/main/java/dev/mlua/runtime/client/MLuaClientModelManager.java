package dev.mlua.runtime.client;

import net.minecraft.client.Minecraft;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.common.enums.Side;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaClientModelManager {
    public static final int MAX_OVERRIDES = 32_768;
    public static final int MAX_OVERRIDES_PER_OWNER = 4_096;
    public static final int MAX_OVERRIDES_PER_ENTITY = 128;
    public static final int MAX_PART_NAME_LENGTH = 256;
    private static final Set<String> DEFAULT_PART_NAMES = Set.of(
        "head", "hat", "body", "rightArm", "leftArm", "rightLeg", "leftLeg",
        "jacket", "rightSleeve", "leftSleeve", "rightPants", "leftPants",
        "cloak", "cape", "ear", "ears"
    );
    private static final Map<String, String> PART_ALIASES = buildAliases();
    private static final ConcurrentHashMap<Owner, ConcurrentHashMap<MLuaEntityScope, ConcurrentHashMap<String, PartState>>> STATES = new ConcurrentHashMap<>();
    private static final Object STATE_LOCK = new Object();
    private static int overrideCount;
    private static final Owner LEGACY_OWNER = new Owner(null, "");
    private static final java.util.concurrent.atomic.AtomicLong SEQUENCE = new java.util.concurrent.atomic.AtomicLong();
    private static final Map<Object, IdentityHashMap<ModelPart, PartPose>> OVERRIDE_POSES = new WeakHashMap<>();
    private static final Field MODEL_PART_X_SCALE = resolveField("xScale");
    private static final Field MODEL_PART_Y_SCALE = resolveField("yScale");
    private static final Field MODEL_PART_Z_SCALE = resolveField("zScale");

    private MLuaClientModelManager() {
    }

    public static void setCFrame(int entityId, String part, MLuaCFrame cframe) {
        setCFrame(MLuaEntityScope.legacy(entityId), part, cframe);
    }

    public static void setCFrame(MLuaEntityScope scope, String part, MLuaCFrame cframe) {
        setCFrame(scope, part, cframe, LEGACY_OWNER);
    }

    public static void setCFrame(MLuaEntityScope scope, String part, MLuaCFrame cframe, Owner owner) {
        if (cframe == null) throw new LuaError("[MLua] Model part CFrame must not be nil.");
        setPartProperties(scope, part, cframe, null, null, null, owner);
    }

    public static void setOffset(int entityId, String part, LuaValue offset) {
        setOffset(MLuaEntityScope.legacy(entityId), part, offset);
    }

    public static void setOffset(MLuaEntityScope scope, String part, LuaValue offset) {
        setOffset(scope, part, offset, LEGACY_OWNER);
    }
    public static void setOffset(MLuaEntityScope scope, String part, LuaValue offset, Owner owner) {
        if (offset == null) throw new LuaError("[MLua] Model part Offset must not be nil.");
        setPartProperties(scope, part, null, offset, null, null, owner);
    }

    public static void setScale(int entityId, String part, LuaValue scale) {
        setScale(MLuaEntityScope.legacy(entityId), part, scale);
    }

    public static void setScale(MLuaEntityScope scope, String part, LuaValue scale) {
        setScale(scope, part, scale, LEGACY_OWNER);
    }
    public static void setScale(MLuaEntityScope scope, String part, LuaValue scale, Owner owner) {
        if (scale == null) throw new LuaError("[MLua] Model part Scale must not be nil.");
        setPartProperties(scope, part, null, null, scale, null, owner);
    }

    private static LuaTable typedVector(LuaValue value, String property) {
        if (!MLuaVector.isVector3(value)) {
            throw new LuaError("[MLua] Model part " + property + " expects a native Vector3 value.");
        }
        double[] components = MLuaVector.components3(value);
        for (double component : components) {
            if (!Double.isFinite(component)) {
                throw new LuaError("[MLua] Model part " + property + " requires finite Vector3 components.");
            }
        }
        return MLuaVector.vector3(components[0], components[1], components[2]);
    }

    private static double[] cframeComponents(MLuaCFrame cframe) {
        return new double[]{cframe.x(), cframe.y(), cframe.z(), cframe.r00(), cframe.r01(), cframe.r02(),
            cframe.r10(), cframe.r11(), cframe.r12(), cframe.r20(), cframe.r21(), cframe.r22()};
    }

    public static void setVisible(int entityId, String part, Boolean visible) {
        setVisible(MLuaEntityScope.legacy(entityId), part, visible);
    }

    public static void setVisible(MLuaEntityScope scope, String part, Boolean visible) {
        setVisible(scope, part, visible, LEGACY_OWNER);
    }
    public static void setVisible(MLuaEntityScope scope, String part, Boolean visible, Owner owner) {
        if (visible == null) {
            clearPart(scope, part, owner);
            return;
        }
        setPartProperties(scope, part, null, null, null, visible, owner);
    }

    /** Applies one Lua part definition as a single capacity-checked state update. */
    public static void setPartProperties(MLuaEntityScope scope, String part, MLuaCFrame cframe,
                                         LuaValue offset, LuaValue scale, Boolean visible, Owner owner) {
        if (cframe == null && offset == null && scale == null && visible == null) return;
        String normalizedPart = normalizePartName(part);
        if (cframe != null) {
            for (double component : cframeComponents(cframe)) {
                if (!Double.isFinite(component)) throw new LuaError("[MLua] Model part CFrame components must be finite.");
            }
        }
        LuaTable typedOffset = offset == null ? null : typedVector(offset, "Offset");
        LuaTable typedScale = scale == null ? null : typedVector(scale, "Scale");
        if (scope == null) throw new LuaError("[MLua] Model overrides require a live entity scope.");

        synchronized (STATE_LOCK) {
            Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
            ConcurrentHashMap<MLuaEntityScope, ConcurrentHashMap<String, PartState>> entities = STATES.get(resolvedOwner);
            ConcurrentHashMap<String, PartState> parts = entities == null ? null : entities.get(scope);
            PartState state = parts == null ? null : parts.get(normalizedPart);
            if (state == null) {
                int ownerCount = entities == null ? 0 : countOverrides(entities);
                if (overrideCount >= MAX_OVERRIDES) {
                    throw new LuaError("[MLua] Model override capacity reached: " + MAX_OVERRIDES + " active overrides total.");
                }
                if (ownerCount >= MAX_OVERRIDES_PER_OWNER) {
                    throw new LuaError("[MLua] This addon runtime reached its model override limit of " + MAX_OVERRIDES_PER_OWNER + ".");
                }
                if (parts != null && parts.size() >= MAX_OVERRIDES_PER_ENTITY) {
                    throw new LuaError("[MLua] This addon runtime reached its model override limit of "
                        + MAX_OVERRIDES_PER_ENTITY + " parts on one entity.");
                }
                if (entities == null) {
                    entities = new ConcurrentHashMap<>();
                    STATES.put(resolvedOwner, entities);
                }
                if (parts == null) {
                    parts = new ConcurrentHashMap<>();
                    entities.put(scope, parts);
                }
                state = new PartState();
                parts.put(normalizedPart, state);
                overrideCount++;
            }

            if (cframe != null) {
                state.cframe = cframe;
                state.cframeOrder = SEQUENCE.incrementAndGet();
            }
            if (typedOffset != null) {
                state.offset = typedOffset;
                state.offsetOrder = SEQUENCE.incrementAndGet();
            }
            if (typedScale != null) {
                state.scale = typedScale;
                state.scaleOrder = SEQUENCE.incrementAndGet();
            }
            if (visible != null) {
                state.visible = visible;
                state.visibleOrder = SEQUENCE.incrementAndGet();
            }
        }
    }

    public static PartState getPartState(int entityId, String part) {
        return getPartState(MLuaEntityScope.legacy(entityId), part);
    }

    public static PartState getPartState(MLuaEntityScope scope, String part) {
        return getMergedPartState(scope, part);
    }
    public static PartState getMergedPartState(MLuaEntityScope scope, String part) {
        PartState out = new PartState();
        for (var ownerState : STATES.values()) {
            var parts = ownerState.get(scope);
            if (parts != null) merge(out, parts.get(normalizePartName(part)));
        }
        return out.empty() ? null : out;
    }
    public static PartState getPartState(MLuaEntityScope scope, String part, Owner owner) { var entities = STATES.get(owner == null ? LEGACY_OWNER : owner); var parts = entities == null ? null : entities.get(scope); return parts == null ? null : parts.get(normalizePartName(part)); }

    public static Map<String, PartState> getState(int entityId) {
        return getState(MLuaEntityScope.legacy(entityId));
    }

    public static Map<String, PartState> getState(MLuaEntityScope scope) {
        LinkedHashMap<String, PartState> merged = new LinkedHashMap<>();
        for (var ownerState : STATES.values()) {
            var parts = ownerState.get(scope);
            if (parts != null) parts.forEach((name, state) -> merge(merged.computeIfAbsent(name, ignored -> new PartState()), state));
        }
        return Collections.unmodifiableMap(merged);
    }
    public static Map<String, PartState> getState(MLuaEntityScope scope, Owner owner) { var entities = STATES.get(owner == null ? LEGACY_OWNER : owner); var parts = entities == null ? null : entities.get(scope); return parts == null ? Collections.emptyMap() : Collections.unmodifiableMap(parts); }

    public static Iterable<String> getPartNames(int entityId) {
        return getPartNames(MLuaEntityScope.legacy(entityId));
    }

    public static Iterable<String> getPartNames(MLuaEntityScope scope) {
        TreeSet<String> names = new TreeSet<>(DEFAULT_PART_NAMES);
        names.addAll(MLuaModelAdapterRegistry.appliedBoneNames(scope));
        for (var ownerState : STATES.values()) {
            var map = ownerState.get(scope);
            if (map != null) names.addAll(map.keySet());
        }
        return List.copyOf(names);
    }
    public static Iterable<String> getPartNames(MLuaEntityScope scope, Owner owner) {
        TreeSet<String> names = new TreeSet<>(DEFAULT_PART_NAMES);
        names.addAll(MLuaModelAdapterRegistry.appliedBoneNames(scope));
        var entities = STATES.get(owner == null ? LEGACY_OWNER : owner);
        var map = entities == null ? null : entities.get(scope);
        if (map != null) names.addAll(map.keySet());
        return List.copyOf(names);
    }

    public static void clearPart(int entityId, String part) {
        clearPart(MLuaEntityScope.legacy(entityId), part);
    }

    public static void clearPart(MLuaEntityScope scope, String part) {
        clearPart(scope, part, LEGACY_OWNER);
    }
    public static void clearPart(MLuaEntityScope scope, String part, Owner owner) {
        synchronized (STATE_LOCK) {
            Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
            var entities = STATES.get(resolvedOwner);
            if (entities == null) return;
            ConcurrentHashMap<String, PartState> map = entities.get(scope);
            if (map == null) return;
            if (map.remove(normalizePartName(part)) != null) overrideCount--;
            if (map.isEmpty()) {
                entities.remove(scope, map);
                if (entities.isEmpty()) STATES.remove(resolvedOwner, entities);
            }
        }
    }

    public static void clearEntity(int entityId) {
        clearEntity(MLuaEntityScope.legacy(entityId));
    }

    public static void clearEntity(MLuaEntityScope scope) {
        clearEntityForAllOwners(scope);
    }

    public static void clearEntityForAllOwners(MLuaEntityScope scope) {
        MLuaModelAdapterRegistry.clearEntity(scope);
        synchronized (STATE_LOCK) {
            STATES.forEach((owner, entities) -> {
                Map<String, PartState> removed = entities.remove(scope);
                if (removed != null) overrideCount -= removed.size();
                if (entities.isEmpty()) STATES.remove(owner, entities);
            });
        }
    }

    /** Releases model overrides and applied adapter snapshots when a level unloads. */
    public static void clearDimension(String dimension, Side side) {
        if (dimension == null || side == null) return;
        Set<MLuaEntityScope> scopes = new LinkedHashSet<>();
        STATES.values().forEach(entities -> entities.keySet().stream()
            .filter(scope -> scope.side() == side && scope.dimension().equals(dimension))
            .forEach(scopes::add));
        scopes.forEach(MLuaClientModelManager::clearEntityForAllOwners);
        MLuaModelAdapterRegistry.clearDimension(dimension, side);
    }
    public static void clearEntity(MLuaEntityScope scope, Owner owner) {
        synchronized (STATE_LOCK) {
            Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
            var entities = STATES.get(resolvedOwner);
            if (entities != null) {
                Map<String, PartState> removed = entities.remove(scope);
                if (removed != null) overrideCount -= removed.size();
                if (entities.isEmpty()) STATES.remove(resolvedOwner, entities);
            }
        }
    }

    public static void clearAll() {
        synchronized (STATE_LOCK) {
            STATES.clear();
            overrideCount = 0;
        }
        MLuaModelAdapterRegistry.clearAppliedEntities();
        restorePendingOverrides();
    }
    public static void clearOwned(Owner owner) {
        synchronized (STATE_LOCK) {
            var removed = STATES.remove(owner == null ? LEGACY_OWNER : owner);
            if (removed != null) overrideCount -= countOverrides(removed);
        }
        // Pose snapshots do not carry an addon owner: they are transient render state,
        // so no snapshot may remain attached to a model after any owner is unloaded.
        restorePendingOverrides();
    }

    public static void clearRuntime(Object runtimeOwner) {
        if (runtimeOwner == null) return;
        synchronized (STATE_LOCK) {
            STATES.entrySet().removeIf(entry -> {
                if (entry.getKey().runtimeOwner() != runtimeOwner) return false;
                overrideCount -= countOverrides(entry.getValue());
                return true;
            });
        }
        restorePendingOverrides();
    }

    private static void restorePendingOverrides() {
        Runnable restore = () -> {
            IdentityHashMap<Object, IdentityHashMap<ModelPart, PartPose>> pending;
            synchronized (OVERRIDE_POSES) {
                if (OVERRIDE_POSES.isEmpty()) return;
                pending = new IdentityHashMap<>(OVERRIDE_POSES);
                OVERRIDE_POSES.clear();
            }
            pending.values().forEach(poses -> poses.forEach((part, pose) -> pose.restore(part)));
        };
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || dev.mlua.runtime.client.MLuaClientThread.isClientThread()) restore.run();
        else minecraft.execute(restore);
    }

    public static String normalizePartName(String part) {
        if (part == null || part.isBlank()) {
            return "body";
        }
        if (part.length() > MAX_PART_NAME_LENGTH) {
            throw new LuaError("[MLua] Model part names cannot exceed " + MAX_PART_NAME_LENGTH + " characters.");
        }
        String direct = PART_ALIASES.get(part);
        if (direct != null) {
            return direct;
        }
        String collapsed = part.replace("_", "").replace("-", "").replace(" ", "");
        direct = PART_ALIASES.get(collapsed);
        return direct != null ? direct : part;
    }

    public static void restoreOverrides(Object model) {
        if (model == null) return;
        IdentityHashMap<ModelPart, PartPose> poses;
        synchronized (OVERRIDE_POSES) {
            poses = OVERRIDE_POSES.remove(model);
        }
        if (poses == null) return;
        poses.forEach((part, pose) -> pose.restore(part));
    }

    public static void rememberOverridePose(Object model, ModelPart part) {
        if (model == null || part == null) return;
        synchronized (OVERRIDE_POSES) {
            OVERRIDE_POSES.computeIfAbsent(model, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(part, PartPose::capture);
        }
    }

    /** Restores and forgets one temporarily changed part without disturbing other parts in the model. */
    public static void restoreOverridePose(Object model, ModelPart part) {
        if (model == null || part == null) return;
        PartPose pose;
        synchronized (OVERRIDE_POSES) {
            IdentityHashMap<ModelPart, PartPose> poses = OVERRIDE_POSES.get(model);
            if (poses == null) return;
            pose = poses.remove(part);
            if (poses.isEmpty()) OVERRIDE_POSES.remove(model);
        }
        if (pose != null) pose.restore(part);
    }

    public static void applyState(Object model, ModelPart part, PartState state) {
        if (part == null || state == null) {
            return;
        }
        rememberOverridePose(model, part);

        if (state.cframe != null) {
            applyCFrameDelta(part, state.cframe);
        }

        if (state.offset != null) {
            // Script vectors use block units while ModelPart pivots use pixels.
            part.x += (float) (component(state.offset, "X") * 16.0);
            part.y += (float) (component(state.offset, "Y") * 16.0);
            part.z += (float) (component(state.offset, "Z") * 16.0);
        }

        if (state.visible != null) {
            part.visible = state.visible;
        }

        if (state.scale != null) {
            applyScale(part, (float) component(state.scale, "X"), (float) component(state.scale, "Y"), (float) component(state.scale, "Z"));
        }
    }

    /** Adds a script transform to the pose already prepared by Minecraft for this model part. */
    public static void applyCFrameDelta(ModelPart part, MLuaCFrame delta) {
        if (part == null || delta == null) return;

        // CFrame position uses blocks while Minecraft model pivots use pixels.
        part.x += (float) (delta.x() * 16.0);
        part.y += (float) (delta.y() * 16.0);
        part.z += (float) (delta.z() * 16.0);

        // ModelPart applies rotations as Z * Y * X. Compose the script transform
        // after that base pose so an animation does not erase vanilla walking,
        // aiming, or other renderer-prepared rotations.
        double sx = Math.sin(part.xRot), cx = Math.cos(part.xRot);
        double sy = Math.sin(part.yRot), cy = Math.cos(part.yRot);
        double sz = Math.sin(part.zRot), cz = Math.cos(part.zRot);
        double a00 = cz * cy, a01 = cz * sy * sx - sz * cx, a02 = cz * sy * cx + sz * sx;
        double a10 = sz * cy, a11 = sz * sy * sx + cz * cx, a12 = sz * sy * cx - cz * sx;
        double a20 = -sy, a21 = cy * sx, a22 = cy * cx;
        double m00 = a00 * delta.r00() + a01 * delta.r10() + a02 * delta.r20();
        double m01 = a00 * delta.r01() + a01 * delta.r11() + a02 * delta.r21();
        double m10 = a10 * delta.r00() + a11 * delta.r10() + a12 * delta.r20();
        double m11 = a10 * delta.r01() + a11 * delta.r11() + a12 * delta.r21();
        double m20 = a20 * delta.r00() + a21 * delta.r10() + a22 * delta.r20();
        double m21 = a20 * delta.r01() + a21 * delta.r11() + a22 * delta.r21();
        double m22 = a20 * delta.r02() + a21 * delta.r12() + a22 * delta.r22();

        double composedY = Math.asin(clamp(-m20, -1.0, 1.0));
        double cosY = Math.cos(composedY);
        double composedX;
        double composedZ;
        if (Math.abs(cosY) > 1.0e-6) {
            composedX = Math.atan2(m21, m22);
            composedZ = Math.atan2(m10, m00);
        } else {
            composedX = 0.0;
            composedZ = Math.atan2(-m01, m11);
        }
        part.xRot = (float) composedX;
        part.yRot = (float) composedY;
        part.zRot = (float) composedZ;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record PartPose(float x, float y, float z, float xRot, float yRot, float zRot,
                            boolean visible, float xScale, float yScale, float zScale) {
        private static PartPose capture(ModelPart part) {
            return new PartPose(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.visible,
                readScale(MODEL_PART_X_SCALE, part), readScale(MODEL_PART_Y_SCALE, part), readScale(MODEL_PART_Z_SCALE, part));
        }

        private void restore(ModelPart part) {
            part.x = x;
            part.y = y;
            part.z = z;
            part.xRot = xRot;
            part.yRot = yRot;
            part.zRot = zRot;
            part.visible = visible;
            trySet(MODEL_PART_X_SCALE, part, xScale);
            trySet(MODEL_PART_Y_SCALE, part, yScale);
            trySet(MODEL_PART_Z_SCALE, part, zScale);
        }
    }

    private static float readScale(Field field, ModelPart part) {
        if (field == null) return 1.0F;
        try {
            return field.getFloat(part);
        } catch (IllegalAccessException ignored) {
            return 1.0F;
        }
    }

    private static double component(LuaTable vector, String key) {
        LuaValue value = vector.get(key);
        if (value.isnil()) {
            value = vector.get(key.toLowerCase());
        }
        return value.optdouble(0.0);
    }

    private static void applyScale(ModelPart part, float x, float y, float z) {
        trySet(MODEL_PART_X_SCALE, part, x);
        trySet(MODEL_PART_Y_SCALE, part, y);
        trySet(MODEL_PART_Z_SCALE, part, z);
    }

    private static void trySet(Field field, ModelPart part, float value) {
        if (field == null) {
            return;
        }
        try {
            field.setFloat(part, value);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static Field resolveField(String name) {
        try {
            Field field = ModelPart.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static int overrideCount() {
        synchronized (STATE_LOCK) { return overrideCount; }
    }

    public static int overrideCount(Owner owner) {
        synchronized (STATE_LOCK) {
            var entities = STATES.get(owner == null ? LEGACY_OWNER : owner);
            return entities == null ? 0 : countOverrides(entities);
        }
    }

    private static int countOverrides(Map<MLuaEntityScope, ? extends Map<String, PartState>> entities) {
        return entities.values().stream().mapToInt(Map::size).sum();
    }
    private static void merge(PartState out, PartState in) {
        if (in == null) return;
        if (in.cframeOrder > out.cframeOrder) { out.cframe = in.cframe; out.cframeOrder = in.cframeOrder; }
        if (in.offsetOrder > out.offsetOrder) { out.offset = in.offset; out.offsetOrder = in.offsetOrder; }
        if (in.scaleOrder > out.scaleOrder) { out.scale = in.scale; out.scaleOrder = in.scaleOrder; }
        if (in.visibleOrder > out.visibleOrder) { out.visible = in.visible; out.visibleOrder = in.visibleOrder; }
    }
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

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        alias(aliases, "head", "head", "Head");
        alias(aliases, "hat", "hat", "Hat", "headwear", "Headwear");
        alias(aliases, "body", "body", "Body", "torso", "Torso", "chest", "Chest");
        alias(aliases, "rightArm", "rightArm", "RightArm", "right_arm", "Right_Arm", "rarm", "RArm", "rightHand", "RightHand");
        alias(aliases, "leftArm", "leftArm", "LeftArm", "left_arm", "Left_Arm", "larm", "LArm", "leftHand", "LeftHand");
        alias(aliases, "rightLeg", "rightLeg", "RightLeg", "right_leg", "Right_Leg", "rleg", "RLeg", "rightFoot", "RightFoot");
        alias(aliases, "leftLeg", "leftLeg", "LeftLeg", "left_leg", "Left_Leg", "lleg", "LLeg", "leftFoot", "LeftFoot");
        alias(aliases, "jacket", "jacket", "Jacket", "bodyLayer", "BodyLayer", "body_overlay", "BodyOverlay");
        alias(aliases, "rightSleeve", "rightSleeve", "RightSleeve", "right_sleeve", "Right_Sleeve", "rightArmLayer", "RightArmLayer");
        alias(aliases, "leftSleeve", "leftSleeve", "LeftSleeve", "left_sleeve", "Left_Sleeve", "leftArmLayer", "LeftArmLayer");
        alias(aliases, "rightPants", "rightPants", "RightPants", "right_pants", "Right_Pants", "rightLegLayer", "RightLegLayer");
        alias(aliases, "leftPants", "leftPants", "LeftPants", "left_pants", "Left_Pants", "leftLegLayer", "LeftLegLayer");
        alias(aliases, "cloak", "cloak", "Cloak", "cape", "Cape");
        alias(aliases, "ear", "ear", "Ear");
        alias(aliases, "ears", "ears", "Ears");
        return aliases;
    }

    private static void alias(Map<String, String> aliases, String canonical, String... names) {
        for (String name : names) {
            aliases.put(name, canonical);
            aliases.put(name.replace("_", "").replace("-", "").replace(" ", ""), canonical);
        }
    }

    public static final class PartState {
        private MLuaCFrame cframe;
        private LuaTable offset;
        private LuaTable scale;
        private Boolean visible;
        private long cframeOrder, offsetOrder, scaleOrder, visibleOrder;
        private boolean empty() { return cframe == null && offset == null && scale == null && visible == null; }

        public MLuaCFrame cframe() {
            return cframe;
        }

        public LuaTable offset() {
            return offset;
        }

        public LuaTable scale() {
            return scale;
        }

        public Boolean visible() {
            return visible;
        }
    }
}
