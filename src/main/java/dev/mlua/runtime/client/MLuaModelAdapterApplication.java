package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;

/** Applies registered model mappings and animation state during render hooks. */
final class MLuaModelAdapterApplication {
    private MLuaModelAdapterApplication() { }

    static Set<String> apply(Object model, MLuaEntityScope scope) {
        if (model == null || scope == null) return Set.of();
        if (!MLuaClientRenderThread.require("model adapter")) return Set.of();

        Map<String, String> mapping = MLuaModelAdapterRegistry.findMappingForModel(model);
        Map<MLuaModelAdapterRegistry.CustomAdapter, List<String>> customAdapters =
            MLuaModelAdapterRegistry.customAdaptersForModel(model.getClass()).adapters();
        if (mapping == null && customAdapters.isEmpty()) {
            MLuaModelAdapterRegistry.clearAppliedModel(scope);
            return Set.of();
        }
        if (mapping == null) mapping = Map.of();

        Map<String, MLuaCFrame> animation = MLuaBonePose.getRenderPose(scope);
        LinkedHashSet<String> applied = new LinkedHashSet<>();
        Map<String, ModelPart> resolvedParts = MLuaModelAdapterRegistry.resolvedParts(model, mapping);
        Map<String, MLuaClientModelManager.PartState> partStates =
            MLuaClientModelManager.getMergedPartStates(scope, mapping.keySet());
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            ModelPart part = resolvedParts.get(entry.getKey());
            if (part == null) {
                MLuaModelAdapterRegistry.reportMissingPath(model.getClass(), entry.getKey(), entry.getValue());
                continue;
            }
            applied.add(entry.getKey());
            MLuaCFrame pose = animation == null ? null : animation.get(entry.getKey());
            if (pose != null) {
                MLuaClientModelManager.rememberOverridePose(model, part);
                MLuaModelAdapterRegistry.applyPose(part, pose);
            }
            MLuaClientModelManager.applyState(model, part,
                partStates.get(MLuaClientModelManager.normalizePartName(entry.getKey())));
        }

        LinkedHashSet<String> customBones = new LinkedHashSet<>();
        customAdapters.forEach((adapter, bones) -> {
            if (MLuaCustomModelAdapterInvoker.apply(adapter, model, scope, bones)) customBones.addAll(bones);
        });
        applied.addAll(customBones);
        Set<String> resolved = Collections.unmodifiableSet(new LinkedHashSet<>(applied));
        MLuaModelAdapterRegistry.recordAppliedModel(model, scope, mapping, resolved,
            Collections.unmodifiableSet(customBones));
        return resolved;
    }

    static boolean applyPartTransform(Object model, String boneName, PoseStack poseStack, MLuaEntityScope scope) {
        if (!validRequest(model, boneName, poseStack)) return false;
        Map<String, String> mapping = MLuaModelAdapterRegistry.findMappingForModel(model);
        String path = mapping == null ? null : mapping.get(boneName);
        if (path == null) {
            MLuaModelAdapterRegistry.CustomAdapter adapter =
                MLuaModelAdapterRegistry.customAdapterForBone(model.getClass(), boneName);
            if (adapter != null) {
                return MLuaCustomModelAdapterInvoker.applyPose(adapter, model, boneName, poseStack, scope);
            }
            MLuaModelAdapterRegistry.reportMissingPath(model.getClass(), boneName, null);
            return false;
        }
        return MLuaModelPartTransforms.applyMapped(model, boneName, path, poseStack, scope,
            mapping == null ? Map.of() : mapping);
    }

    static boolean applyCurrentPartTransform(Object model, String boneName, PoseStack poseStack,
                                               MLuaEntityScope scope) {
        if (!validRequest(model, boneName, poseStack)) return false;
        Map<String, String> mapping = MLuaModelAdapterRegistry.findMappingForModel(model);
        String path = mapping == null ? null : mapping.get(boneName);
        if (path == null) {
            MLuaModelAdapterRegistry.CustomAdapter adapter =
                MLuaModelAdapterRegistry.customAdapterForBone(model.getClass(), boneName);
            if (adapter != null) {
                return MLuaCustomModelAdapterInvoker.applyPose(adapter, model, boneName, poseStack, scope);
            }
            MLuaModelAdapterRegistry.reportMissingPath(model.getClass(), boneName, path);
            return false;
        }
        return MLuaModelPartTransforms.applyCurrent(model, boneName, path, poseStack);
    }

    private static boolean validRequest(Object model, String boneName, PoseStack poseStack) {
        return model != null && boneName != null && !boneName.isBlank() && poseStack != null;
    }
}
