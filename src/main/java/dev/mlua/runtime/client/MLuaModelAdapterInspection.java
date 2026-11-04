package dev.mlua.runtime.client;

import dev.mlua.minecraft.animation.MLuaEntityScope;
import net.minecraft.client.model.geom.ModelPart;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Builds immutable inspection snapshots for model adapters applied to rendered entities. */
final class MLuaModelAdapterInspection {
    private MLuaModelAdapterInspection() { }

    static Set<String> appliedBoneNames(MLuaEntityScope scope) {
        if (scope == null) return Set.of();
        MLuaAppliedModelRegistry.Snapshot applied = MLuaAppliedModelRegistry.get(scope);
        if (applied == null) return Set.of();
        LinkedHashSet<String> current = new LinkedHashSet<>(applied.customBones());
        for (String bone : applied.resolvedBones()) {
            if (applied.mappings().containsKey(bone)) current.add(bone);
        }
        return Collections.unmodifiableSet(current);
    }

    static MLuaModelAdapterRegistry.AdapterSnapshot appliedAdapter(MLuaEntityScope scope) {
        if (scope == null) return null;
        MLuaAppliedModelRegistry.Snapshot applied = MLuaAppliedModelRegistry.get(scope);
        if (applied == null) return null;
        TreeSet<String> resolved = new TreeSet<>(applied.customBones());
        for (String bone : applied.resolvedBones()) {
            if (applied.mappings().containsKey(bone)) resolved.add(bone);
        }
        return new MLuaModelAdapterRegistry.AdapterSnapshot(applied.modelClass().getName(), applied.mappings(),
            Collections.unmodifiableSet(resolved), applied.customBones());
    }

    static Set<String> recordResolvedModel(Object model, MLuaEntityScope scope) {
        if (scope == null) return Set.of();
        if (model == null || !MLuaModelAdapterRegistry.isAdaptedModel(model)) {
            MLuaAppliedModelRegistry.remove(scope);
            return Set.of();
        }

        Map<String, String> mappings = MLuaModelAdapterLookup.findMappingForModel(model);
        if (mappings == null) mappings = Map.of();
        TreeSet<String> resolved = new TreeSet<>();
        Map<String, ModelPart> parts = MLuaModelPartResolver.resolvedParts(model, mappings,
            MLuaModelAdapterCache.generationClock());
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            if (parts.containsKey(entry.getKey())) resolved.add(entry.getKey());
            else MLuaModelPartResolver.reportMissingPath(model.getClass(), entry.getKey(), entry.getValue());
        }
        LinkedHashSet<String> customBones = new LinkedHashSet<>();
        MLuaModelAdapterLookup.customAdaptersForModel(model.getClass()).adapters().values()
            .forEach(customBones::addAll);
        resolved.addAll(customBones);

        Set<String> snapshot = Collections.unmodifiableSet(resolved);
        MLuaAppliedModelRegistry.record(model.getClass(), mappings, snapshot, customBones, scope);
        return snapshot;
    }
}
