package dev.mlua.runtime.client;

import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Owns the last model-adapter snapshot observed for each rendered entity. */
final class MLuaAppliedModelRegistry {
    record Snapshot(Class<?> modelClass, Map<String, String> mappings,
                    Set<String> resolvedBones, Set<String> customBones) { }

    private static final ConcurrentMap<MLuaEntityScope, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private MLuaAppliedModelRegistry() { }

    static Snapshot get(MLuaEntityScope scope) {
        return SNAPSHOTS.get(scope);
    }

    /** Callers pass immutable mapping and bone-set snapshots to avoid per-frame copies. */
    static void record(Class<?> modelClass, Map<String, String> mappings,
                       Set<String> resolvedBones, Set<String> customBones, MLuaEntityScope scope) {
        if (modelClass == null || scope == null) return;
        SNAPSHOTS.put(scope, new Snapshot(modelClass, mappings, resolvedBones, customBones));
    }

    static void remove(MLuaEntityScope scope) {
        if (scope != null) SNAPSHOTS.remove(scope);
    }

    static void clearDimension(String dimension, Side side) {
        if (dimension == null || side == null) return;
        SNAPSHOTS.keySet().removeIf(scope -> scope.side() == side && scope.dimension().equals(dimension));
    }

    static void clear() {
        SNAPSHOTS.clear();
    }
}
