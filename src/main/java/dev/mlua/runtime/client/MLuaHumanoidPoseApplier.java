package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;

public final class MLuaHumanoidPoseApplier {
    private static final ClassValue<ConcurrentMap<String, Optional<Field>>> REFLECTIVE_PART_FIELDS =
        new ClassValue<>() {
            @Override protected ConcurrentMap<String, Optional<Field>> computeValue(Class<?> type) {
                return new ConcurrentHashMap<>();
            }
        };

    private MLuaHumanoidPoseApplier() {}

    public static void apply(HumanoidModel<?> self, int entityId) {
        apply(self, MLuaEntityScope.legacy(entityId));
    }

    public static void apply(HumanoidModel<?> self, Entity entity) {
        apply(self, MLuaEntityScope.of(entity));
    }

    private static void apply(HumanoidModel<?> self, MLuaEntityScope scope) {
        Map<String, String> adapter = MLuaModelAdapterRegistry.findMappingForModel(self);
        java.util.Set<String> adapterBones = adapter == null ? java.util.Set.of() : adapter.keySet();
        Map<String, MLuaCFrame> pose = MLuaBonePose.getRenderPose(scope);
        if (pose == null) pose = Map.of();
        if (!adapterBones.isEmpty()) {
            Map<String, MLuaCFrame> remaining = new java.util.HashMap<>(pose);
            for (String bone : adapterBones) remaining.remove(bone);
            pose = remaining;
        }

        applyPose(self, self.head, pose.get(MLuaBonePose.BONE_HEAD));
        applyPose(self, self.hat, pose.get(MLuaBonePose.BONE_HEAD));
        applyPose(self, self.body, pose.get(MLuaBonePose.BONE_BODY));
        applyPose(self, self.rightArm, pose.get(MLuaBonePose.BONE_RIGHT_ARM));
        applyPose(self, self.leftArm, pose.get(MLuaBonePose.BONE_LEFT_ARM));
        applyPose(self, self.rightLeg, pose.get(MLuaBonePose.BONE_RIGHT_LEG));
        applyPose(self, self.leftLeg, pose.get(MLuaBonePose.BONE_LEFT_LEG));

        applyReflectivePose(self, pose, "jacket", MLuaBonePose.BONE_BODY);
        applyReflectivePose(self, pose, "rightSleeve", MLuaBonePose.BONE_RIGHT_ARM);
        applyReflectivePose(self, pose, "leftSleeve", MLuaBonePose.BONE_LEFT_ARM);
        applyReflectivePose(self, pose, "rightPants", MLuaBonePose.BONE_RIGHT_LEG);
        applyReflectivePose(self, pose, "leftPants", MLuaBonePose.BONE_LEFT_LEG);

        applyModelState(self, self.head, scope, MLuaBonePose.BONE_HEAD, adapterBones);
        applyModelState(self, self.hat, scope, "hat", adapterBones);
        applyModelState(self, self.body, scope, MLuaBonePose.BONE_BODY, adapterBones);
        applyModelState(self, self.rightArm, scope, MLuaBonePose.BONE_RIGHT_ARM, adapterBones);
        applyModelState(self, self.leftArm, scope, MLuaBonePose.BONE_LEFT_ARM, adapterBones);
        applyModelState(self, self.rightLeg, scope, MLuaBonePose.BONE_RIGHT_LEG, adapterBones);
        applyModelState(self, self.leftLeg, scope, MLuaBonePose.BONE_LEFT_LEG, adapterBones);
        applyReflectiveState(self, scope, "jacket", adapterBones);
        applyReflectiveState(self, scope, "rightSleeve", adapterBones);
        applyReflectiveState(self, scope, "leftSleeve", adapterBones);
        applyReflectiveState(self, scope, "rightPants", adapterBones);
        applyReflectiveState(self, scope, "leftPants", adapterBones);
        applyReflectiveState(self, scope, "cloak", adapterBones);
        applyReflectiveState(self, scope, "ear", adapterBones);
        applyReflectiveState(self, scope, "ears", adapterBones);
        for (String partName : MLuaClientModelManager.getPartNames(scope)) {
            if (MLuaBonePose.BONE_HEAD.equals(partName)
                || "hat".equals(partName)
                || MLuaBonePose.BONE_BODY.equals(partName)
                || MLuaBonePose.BONE_RIGHT_ARM.equals(partName)
                || MLuaBonePose.BONE_LEFT_ARM.equals(partName)
                || MLuaBonePose.BONE_RIGHT_LEG.equals(partName)
                || MLuaBonePose.BONE_LEFT_LEG.equals(partName)
                || "jacket".equals(partName)
                || "rightSleeve".equals(partName)
                || "leftSleeve".equals(partName)
                || "rightPants".equals(partName)
                || "leftPants".equals(partName)
                || "cloak".equals(partName)
                || "ear".equals(partName)
                || "ears".equals(partName)) {
                continue;
            }
            applyReflectivePose(self, pose, partName, partName);
            applyReflectiveState(self, scope, partName, adapterBones);
        }
    }

    private static void applyPose(HumanoidModel<?> model, ModelPart part, MLuaCFrame cframe) {
        if (part == null || cframe == null) return;
        MLuaClientModelManager.rememberOverridePose(model, part);
        MLuaClientModelManager.applyCFrameDelta(part, cframe);
    }

    private static void applyReflectivePose(HumanoidModel<?> self, Map<String, MLuaCFrame> pose, String fieldName, String boneName) {
        ModelPart part = resolveReflectivePart(self, fieldName);
        if (part != null) {
            applyPose(self, part, pose.get(boneName));
        }
    }

    private static void applyModelState(HumanoidModel<?> model, ModelPart part, MLuaEntityScope scope,
            String partName, java.util.Set<String> adapterBones) {
        if (!adapterBones.contains(partName)) {
            MLuaClientModelManager.applyState(model, part, MLuaClientModelManager.getPartState(scope, partName));
        }
    }

    private static void applyReflectiveState(HumanoidModel<?> self, MLuaEntityScope scope, String fieldName,
            java.util.Set<String> adapterBones) {
        if (adapterBones.contains(fieldName)) return;
        ModelPart part = resolveReflectivePart(self, fieldName);
        if (part != null) {
            applyModelState(self, part, scope, fieldName, adapterBones);
        }
    }

    private static ModelPart resolveReflectivePart(HumanoidModel<?> self, String fieldName) {
        Optional<Field> resolved = REFLECTIVE_PART_FIELDS.get(self.getClass())
            .computeIfAbsent(fieldName, name -> findReflectivePartField(self.getClass(), name));
        if (resolved.isEmpty()) return null;
        try {
            Object part = resolved.get().get(self);
            return part instanceof ModelPart modelPart ? modelPart : null;
        } catch (IllegalAccessException | RuntimeException inaccessible) {
            return null;
        }
    }

    private static Optional<Field> findReflectivePartField(Class<?> modelClass, String fieldName) {
        for (Class<?> type = modelClass; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                return field.trySetAccessible() ? Optional.of(field) : Optional.empty();
            } catch (NoSuchFieldException ignored) {
                // Keep searching parent model classes.
            } catch (SecurityException inaccessible) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
