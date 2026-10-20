package dev.mlua.minecraft.scripted;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaValue;

/** Tracks callback replacement and per-instance failure state for scripted objects. */
final class MLuaScriptCallbackState {
    private final Map<String, LuaValue> activeCallbacks = new HashMap<>();
    private final Set<String> disabledCallbacks = new HashSet<>();

    boolean shouldInvoke(String event, LuaValue callback) {
        LuaValue previous = this.activeCallbacks.put(event, callback);
        if (previous != callback) this.disabledCallbacks.remove(event);
        return callback != null && callback.isfunction() && !this.disabledCallbacks.contains(event);
    }

    void disable(String event) {
        this.disabledCallbacks.add(event);
    }

    void clear() {
        this.activeCallbacks.clear();
        this.disabledCallbacks.clear();
    }

    void clearState(String state) {
        String prefix = state == null ? "aiState:" : "aiState:" + state + ":";
        this.disabledCallbacks.removeIf(event -> event.startsWith(prefix));
        this.activeCallbacks.keySet().removeIf(event -> event.startsWith(prefix));
    }
}
