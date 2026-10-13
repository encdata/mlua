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
        Map<String, MLuaCFrame> pose = MLuaBonePose.getPose(scope);
        if (pose == null || pose.isEmpty()) {
            return;
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

        applyModelState(self.head, scope, MLuaBonePose.BONE_HEAD);
        applyModelState(self.hat, scope, "hat");
        applyModelState(self.body, scope, MLuaBonePose.BONE_BODY);
        applyModelState(self.rightArm, scope, MLuaBonePose.BONE_RIGHT_ARM);
        applyModelState(self.leftArm, scope, MLuaBonePose.BONE_LEFT_ARM);
        applyModelState(self.rightLeg, scope, MLuaBonePose.BONE_RIGHT_LEG);
        applyModelState(self.leftLeg, scope, MLuaBonePose.BONE_LEFT_LEG);
        applyReflectiveState(self, scope, "jacket");
        applyReflectiveState(self, scope, "rightSleeve");
        applyReflectiveState(self, scope, "leftSleeve");
        applyReflectiveState(self, scope, "rightPants");
        applyReflectiveState(self, scope, "leftPants");
        applyReflectiveState(self, scope, "cloak");
        applyReflectiveState(self, scope, "ear");
        applyReflectiveState(self, scope, "ears");
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
            applyReflectiveState(self, scope, partName);
        }
    }

    private static void applyPose(ModelPart part, MLuaCFrame cframe) {
        if (part == null || cframe == null) return;
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

    private static void applyModelState(ModelPart part, MLuaEntityScope scope, String partName) {
        MLuaClientModelManager.applyState(part, MLuaClientModelManager.getPartState(scope, partName));
    }

    private static void applyReflectiveState(HumanoidModel<?> self, MLuaEntityScope scope, String fieldName) {
        ModelPart part = resolveReflectivePart(self, fieldName);
        if (part != null) {
            applyModelState(part, scope, fieldName);
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
