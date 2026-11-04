package dev.mlua.modules.coregui.service;

import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Owns the per-client-runtime GUI state index without retaining unloaded schedulers. */
final class MLuaGuiRuntimeStateRegistry {
    private final Map<MLuaTaskSchedulerView, MLuaGuiState> states = new WeakHashMap<>();

    MLuaGuiState getOrCreate(MLuaTaskSchedulerView scheduler) {
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        synchronized (this.states) {
            return this.states.computeIfAbsent(scheduler, ignored -> new MLuaGuiState());
        }
    }

    MLuaGuiState get(MLuaTaskSchedulerView scheduler) {
        if (scheduler == null) return null;
        synchronized (this.states) {
            return this.states.get(scheduler);
        }
    }

    MLuaGuiService getService(MLuaTaskSchedulerView scheduler) {
        MLuaGuiState state = this.get(scheduler);
        return state == null ? null : state.runtimeView();
    }

    List<MLuaGuiState> snapshot() {
        synchronized (this.states) {
            return new ArrayList<>(this.states.values());
        }
    }
}
