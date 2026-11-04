package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Applies registered model-part paths and scoped poses to render attachment matrices. */
final class MLuaModelPartTransforms {
    private MLuaModelPartTransforms() { }

    static boolean applyMapped(Object model, String boneName, String path, PoseStack poseStack,
                               MLuaEntityScope scope, Map<String, String> mapping) {
        if (model == null || path == null || poseStack == null) return false;
        Object current = model;
        List<String> segments = MLuaModelFieldAccess.pathSegments(path);
        IdentityHashMap<ModelPart, String> boneByPart = new IdentityHashMap<>();
        Map<String, MLuaCFrame> animationPose = scope == null ? Map.of()
            : MLuaEntityModelRenderState.renderPose(scope);
        Map<String, MLuaClientModelManager.PartState> partStates = scope == null ? Map.of()
            : MLuaClientModelManager.getMergedPartStates(scope, mapping.keySet());
        if (scope != null) {
            MLuaModelAdapterRegistry.resolvedParts(model, mapping)
                .forEach((bone, part) -> boneByPart.put(part, bone));
        }
        for (String segment : segments) {
            if (segment.isBlank()) {
                MLuaModelPartResolver.reportMissingPath(model.getClass(), boneName, path);
                return false;
            }
            if (current instanceof ModelPart parent) {
                try {
                    current = parent.getChild(segment);
                } catch (RuntimeException missingChild) {
                    MLuaModelPartResolver.reportMissingPath(model.getClass(), boneName, path);
                    return false;
                }
            } else {
                current = MLuaModelFieldAccess.readField(current, segment);
            }
            if (!(current instanceof ModelPart part)) {
                MLuaModelPartResolver.reportMissingPath(model.getClass(), boneName, path);
                return false;
            }
            String mappedBone = scope == null ? null : boneByPart.get(part);
            if (mappedBone == null) {
                part.translateAndRotate(poseStack);
            } else if (!applyBone(model, part, mappedBone, animationPose, partStates, poseStack)) {
                return false;
            }
        }
        return true;
    }

    static boolean applyCurrent(Object model, String boneName, String path, PoseStack poseStack) {
        if (model == null || path == null || poseStack == null) return false;
        Object current = model;
        for (String segment : MLuaModelFieldAccess.pathSegments(path)) {
            if (segment.isBlank()) {
                MLuaModelPartResolver.reportMissingPath(model.getClass(), boneName, path);
                return false;
            }
            if (current instanceof ModelPart parent) {
                try {
                    current = parent.getChild(segment);
                } catch (RuntimeException missingChild) {
                    MLuaModelPartResolver.reportMissingPath(model.getClass(), boneName, path);
                    return false;
                }
            } else {
                current = MLuaModelFieldAccess.readField(current, segment);
            }
            if (!(current instanceof ModelPart part)) {
                MLuaModelPartResolver.reportMissingPath(model.getClass(), boneName, path);
                return false;
            }
            if (!part.visible) return false;
            part.translateAndRotate(poseStack);
        }
        return true;
    }

    private static boolean applyBone(Object model, ModelPart part, String bone,
                                     Map<String, MLuaCFrame> animationPose,
                                     Map<String, MLuaClientModelManager.PartState> partStates,
                                     PoseStack poseStack) {
        MLuaCFrame pose = animationPose.get(bone);
        MLuaClientModelManager.PartState state =
            partStates.get(MLuaClientModelManager.normalizePartName(bone));
        if (state != null && Boolean.FALSE.equals(state.visible())) return false;
        if (pose == null && state == null) {
            part.translateAndRotate(poseStack);
            return true;
        }
        MLuaClientModelManager.rememberOverridePose(model, part);
        try {
            if (pose != null) MLuaModelAdapterRegistry.applyPose(part, pose);
            MLuaClientModelManager.applyState(model, part, state);
            part.translateAndRotate(poseStack);
            if (state != null && state.scale() != null) {
                LuaTable scale = state.scale();
                poseStack.scale((float) dimension(scale, "X"), (float) dimension(scale, "Y"),
                    (float) dimension(scale, "Z"));
            }
        } finally {
            MLuaClientModelManager.restoreOverridePose(model, part);
        }
        return true;
    }

    private static double dimension(LuaTable vector, String component) {
        LuaValue value = vector.get(component);
        if (value.isnil()) value = vector.get(component.toLowerCase(java.util.Locale.ROOT));
        return value.checkdouble();
    }
}
