package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;

public final class MLuaHumanoidPoseApplier {
    private MLuaHumanoidPoseApplier() {}

    public static void apply(HumanoidModel<?> self, int entityId) {
        apply(self, MLuaEntityScope.legacy(entityId));
    }

    public static void apply(HumanoidModel<?> self, Entity entity) {
        apply(self, MLuaEntityScope.of(entity));
    }

    private static void apply(HumanoidModel<?> self, MLuaEntityScope scope) {
        Map<String, String> adapter = MLuaModelAdapterRegistry.findMapping(self.getClass());
        java.util.Set<String> adapterBones = adapter == null ? java.util.Set.of() : adapter.keySet();
        Map<String, MLuaCFrame> pose = MLuaBonePose.getPose(scope);
        if (pose == null || pose.isEmpty()) {
            return;
        }
        if (!adapterBones.isEmpty()) {
            Map<String, MLuaCFrame> remaining = new java.util.HashMap<>(pose);
            for (String bone : adapterBones) remaining.remove(bone);
            pose = remaining;
        }

        applyPose(self.head, pose.get(MLuaBonePose.BONE_HEAD));
        applyPose(self.hat, pose.get(MLuaBonePose.BONE_HEAD));
        applyPose(self.body, pose.get(MLuaBonePose.BONE_BODY));
        applyPose(self.rightArm, pose.get(MLuaBonePose.BONE_RIGHT_ARM));
        applyPose(self.leftArm, pose.get(MLuaBonePose.BONE_LEFT_ARM));
        applyPose(self.rightLeg, pose.get(MLuaBonePose.BONE_RIGHT_LEG));
        applyPose(self.leftLeg, pose.get(MLuaBonePose.BONE_LEFT_LEG));

        applyReflectivePose(self, pose, "jacket", MLuaBonePose.BONE_BODY);
        applyReflectivePose(self, pose, "rightSleeve", MLuaBonePose.BONE_RIGHT_ARM);
        applyReflectivePose(self, pose, "leftSleeve", MLuaBonePose.BONE_LEFT_ARM);
        applyReflectivePose(self, pose, "rightPants", MLuaBonePose.BONE_RIGHT_LEG);
        applyReflectivePose(self, pose, "leftPants", MLuaBonePose.BONE_LEFT_LEG);

        applyModelState(self.head, scope, MLuaBonePose.BONE_HEAD, adapterBones);
        applyModelState(self.hat, scope, "hat", adapterBones);
        applyModelState(self.body, scope, MLuaBonePose.BONE_BODY, adapterBones);
        applyModelState(self.rightArm, scope, MLuaBonePose.BONE_RIGHT_ARM, adapterBones);
        applyModelState(self.leftArm, scope, MLuaBonePose.BONE_LEFT_ARM, adapterBones);
        applyModelState(self.rightLeg, scope, MLuaBonePose.BONE_RIGHT_LEG, adapterBones);
        applyModelState(self.leftLeg, scope, MLuaBonePose.BONE_LEFT_LEG, adapterBones);
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

    private static void applyPose(ModelPart part, MLuaCFrame cframe) {
        if (part == null || cframe == null) return;
        // CFrame positions use Minecraft blocks; model part pivots use pixels.
        part.x += (float) (cframe.x() * 16.0);
        part.y += (float) (cframe.y() * 16.0);
        part.z += (float) (cframe.z() * 16.0);
        double[] euler = cframe.toEulerAnglesXYZ();
        part.xRot = (float) euler[0];
        part.yRot = (float) euler[1];
        part.zRot = (float) euler[2];
    }

    private static void applyReflectivePose(HumanoidModel<?> self, Map<String, MLuaCFrame> pose, String fieldName, String boneName) {
        ModelPart part = resolveReflectivePart(self, fieldName);
        if (part != null) {
            applyPose(part, pose.get(boneName));
        }
    }

    private static void applyModelState(ModelPart part, MLuaEntityScope scope, String partName, java.util.Set<String> adapterBones) {
        if (!adapterBones.contains(partName)) {
            MLuaClientModelManager.applyState(part, MLuaClientModelManager.getPartState(scope, partName));
        }
    }

    private static void applyReflectiveState(HumanoidModel<?> self, MLuaEntityScope scope, String fieldName,
            java.util.Set<String> adapterBones) {
        if (adapterBones.contains(fieldName)) return;
        ModelPart part = resolveReflectivePart(self, fieldName);
        if (part != null) {
            applyModelState(part, scope, fieldName, adapterBones);
        }
    }

    private static ModelPart resolveReflectivePart(HumanoidModel<?> self, String fieldName) {
        Class<?> type = self.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object part = field.get(self);
                if (part instanceof ModelPart mp) {
                    return mp;
                }
            } catch (Exception ignored) {
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
