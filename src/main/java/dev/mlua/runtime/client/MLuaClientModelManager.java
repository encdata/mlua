package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaClientModelManager {
    private static final Set<String> DEFAULT_PART_NAMES = Set.of(
        "head", "hat", "body", "rightArm", "leftArm", "rightLeg", "leftLeg",
        "jacket", "rightSleeve", "leftSleeve", "rightPants", "leftPants",
        "cloak", "cape", "ear", "ears"
    );
    private static final Map<String, String> PART_ALIASES = buildAliases();
    private static final ConcurrentHashMap<Owner, ConcurrentHashMap<MLuaEntityScope, ConcurrentHashMap<String, PartState>>> STATES = new ConcurrentHashMap<>();
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
        PartState state = state(scope, normalizePartName(part), owner);
        state.cframe = cframe;
        state.cframeOrder = SEQUENCE.incrementAndGet();
    }

    public static void setOffset(int entityId, String part, LuaValue offset) {
        setOffset(MLuaEntityScope.legacy(entityId), part, offset);
    }

    public static void setOffset(MLuaEntityScope scope, String part, LuaValue offset) {
        setOffset(scope, part, offset, LEGACY_OWNER);
    }
    public static void setOffset(MLuaEntityScope scope, String part, LuaValue offset, Owner owner) {
        PartState state = state(scope, normalizePartName(part), owner);
        state.offset = typedVector(offset, "Offset");
        state.offsetOrder = SEQUENCE.incrementAndGet();
    }

    public static void setScale(int entityId, String part, LuaValue scale) {
        setScale(MLuaEntityScope.legacy(entityId), part, scale);
    }

    public static void setScale(MLuaEntityScope scope, String part, LuaValue scale) {
        setScale(scope, part, scale, LEGACY_OWNER);
    }
    public static void setScale(MLuaEntityScope scope, String part, LuaValue scale, Owner owner) {
        PartState state = state(scope, normalizePartName(part), owner);
        state.scale = typedVector(scale, "Scale");
        state.scaleOrder = SEQUENCE.incrementAndGet();
    }

    private static LuaTable typedVector(LuaValue value, String property) {
        if (!MLuaVector.isVector3(value)) {
            throw new org.luaj.vm2.LuaError("[MLua] Model part " + property + " expects a native Vector3 value.");
        }
        double[] components = MLuaVector.components3(value);
        return MLuaVector.vector3(components[0], components[1], components[2]);
    }

    public static void setVisible(int entityId, String part, Boolean visible) {
        setVisible(MLuaEntityScope.legacy(entityId), part, visible);
    }

    public static void setVisible(MLuaEntityScope scope, String part, Boolean visible) {
        setVisible(scope, part, visible, LEGACY_OWNER);
    }
    public static void setVisible(MLuaEntityScope scope, String part, Boolean visible, Owner owner) {
        PartState state = state(scope, normalizePartName(part), owner);
        state.visible = visible;
        state.visibleOrder = SEQUENCE.incrementAndGet();
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
        LinkedHashSet<String> names = new LinkedHashSet<>(DEFAULT_PART_NAMES);
        for (var ownerState : STATES.values()) {
            var map = ownerState.get(scope);
            if (map != null) names.addAll(map.keySet());
        }
        return names;
    }
    public static Iterable<String> getPartNames(MLuaEntityScope scope, Owner owner) {
        LinkedHashSet<String> names = new LinkedHashSet<>(DEFAULT_PART_NAMES);
        var entities = STATES.get(owner == null ? LEGACY_OWNER : owner);
        var map = entities == null ? null : entities.get(scope);
        if (map != null) names.addAll(map.keySet());
        return names;
    }

    public static void clearPart(int entityId, String part) {
        clearPart(MLuaEntityScope.legacy(entityId), part);
    }

    public static void clearPart(MLuaEntityScope scope, String part) {
        clearPart(scope, part, LEGACY_OWNER);
    }
    public static void clearPart(MLuaEntityScope scope, String part, Owner owner) {
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        var entities = STATES.get(resolvedOwner);
        if (entities == null) return;
        ConcurrentHashMap<String, PartState> map = entities.get(scope);
        if (map == null) {
            return;
        }
        map.remove(normalizePartName(part));
        if (map.isEmpty()) {
            entities.remove(scope, map);
            if (entities.isEmpty()) STATES.remove(resolvedOwner, entities);
        }
    }

    public static void clearEntity(int entityId) {
        clearEntity(MLuaEntityScope.legacy(entityId));
    }

    public static void clearEntity(MLuaEntityScope scope) {
        clearEntityForAllOwners(scope);
    }

    public static void clearEntityForAllOwners(MLuaEntityScope scope) {
        STATES.forEach((owner, entities) -> {
            entities.remove(scope);
            if (entities.isEmpty()) STATES.remove(owner, entities);
        });
    }
    public static void clearEntity(MLuaEntityScope scope, Owner owner) {
        Owner resolvedOwner = owner == null ? LEGACY_OWNER : owner;
        var entities = STATES.get(resolvedOwner);
        if (entities != null) {
            entities.remove(scope);
            if (entities.isEmpty()) STATES.remove(resolvedOwner, entities);
        }
    }

    public static void clearAll() {
        STATES.clear();
        synchronized (OVERRIDE_POSES) {
            OVERRIDE_POSES.forEach((model, poses) -> poses.forEach((part, pose) -> pose.restore(part)));
            OVERRIDE_POSES.clear();
        }
    }
    public static void clearOwned(Owner owner) {
        STATES.remove(owner == null ? LEGACY_OWNER : owner);
    }

    public static void clearRuntime(Object runtimeOwner) {
        STATES.keySet().removeIf(owner -> owner.runtimeOwner == runtimeOwner);
    }

    public static String normalizePartName(String part) {
        if (part == null || part.isBlank()) {
            return "body";
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

    public static void applyState(Object model, ModelPart part, PartState state) {
        if (part == null || state == null) {
            return;
        }
        rememberOverridePose(model, part);

        if (state.cframe != null) {
            double[] euler = state.cframe.toEulerAnglesXYZ();
            // CFrame translation is expressed in blocks; model part pivots use pixels.
            // Keep it relative to the pose prepared by Minecraft, like animation poses.
            part.x += (float) (state.cframe.x() * 16.0);
            part.y += (float) (state.cframe.y() * 16.0);
            part.z += (float) (state.cframe.z() * 16.0);
            part.xRot = (float) euler[0];
            part.yRot = (float) euler[1];
            part.zRot = (float) euler[2];
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

    private static PartState state(MLuaEntityScope scope, String part, Owner owner) {
        return STATES.computeIfAbsent(owner == null ? LEGACY_OWNER : owner, key -> new ConcurrentHashMap<>()).computeIfAbsent(scope, key -> new ConcurrentHashMap<>()).computeIfAbsent(part, key -> new PartState());
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
