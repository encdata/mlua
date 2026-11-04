package dev.mlua.modules.chat;

import dev.mlua.core.signal.MLuaSignal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Per-runtime chat state shared by Chat services and the game event bridge. */
public final class MLuaChatStateRegistry {
    private static final Object LEGACY_OWNER = new Object();
    private static final ConcurrentHashMap<Object, State> STATES = new ConcurrentHashMap<>();

    private MLuaChatStateRegistry() {}

    public static State get(Object runtimeOwner) {
        return STATES.computeIfAbsent(runtimeOwner == null ? LEGACY_OWNER : runtimeOwner, ignored -> new State());
    }

    public static void clear(Object runtimeOwner) {
        State state = STATES.remove(runtimeOwner == null ? LEGACY_OWNER : runtimeOwner);
        if (state != null) state.onMessage.Destroy();
    }

    public static final class State {
        public final MLuaSignal onMessage = new MLuaSignal();
        public final ConcurrentHashMap<String, Long> mutedPlayers = new ConcurrentHashMap<>();
        public final ConcurrentHashMap<String, ConcurrentLinkedDeque<String>> messageHistory = new ConcurrentHashMap<>();
    }
}
