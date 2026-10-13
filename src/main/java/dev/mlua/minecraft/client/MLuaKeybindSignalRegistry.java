package dev.mlua.minecraft.client;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.signal.MLuaSignal;
import java.util.HashMap;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Objects;
import org.luaj.vm2.Varargs;

/** Keeps Lua listeners for one physical keybind separate for each runtime session. */
final class MLuaKeybindSignalRegistry {
    private final Map<String, Map<MLuaTaskSchedulerView, MLuaSignal>> signals = new HashMap<>();

    synchronized MLuaSignal signal(String id, MLuaTaskSchedulerView scheduler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scheduler, "scheduler");
        Map<MLuaTaskSchedulerView, MLuaSignal> bySession = this.signals.computeIfAbsent(id, ignored -> new IdentityHashMap<>());
        return bySession.computeIfAbsent(scheduler, ignored -> new MLuaSignal());
    }

    synchronized boolean hasSignal(String id) {
        Map<MLuaTaskSchedulerView, MLuaSignal> bySession = this.signals.get(id);
        return bySession != null && !bySession.isEmpty();
    }

    void fireAll(String id, Side side, Varargs args) {
        java.util.List<MLuaSignal> snapshot;
        synchronized (this) {
            Map<MLuaTaskSchedulerView, MLuaSignal> bySession = this.signals.get(id);
            snapshot = bySession == null ? java.util.List.of() : java.util.List.copyOf(bySession.values());
        }
        snapshot.forEach(signal -> signal.fireOnSide(side, args));
    }

    synchronized void clearSession(MLuaRuntimeSession session, Side side) {
        if (session == null || side == null) return;
        for (Map<MLuaTaskSchedulerView, MLuaSignal> bySession : this.signals.values()) {
            bySession.entrySet().removeIf(entry -> {
                MLuaTaskSchedulerView scheduler = entry.getKey();
                boolean matches = side == Side.BOTH
                    ? session.ownsScheduler(scheduler)
                    : side == Side.CLIENT && scheduler == session.clientScheduler()
                        || side == Side.SERVER && scheduler == session.serverScheduler();
                if (matches) entry.getValue().destroySide(side);
                return matches;
            });
        }
        this.signals.values().removeIf(Map::isEmpty);
    }
}
