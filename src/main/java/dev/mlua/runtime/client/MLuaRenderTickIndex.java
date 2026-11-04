package dev.mlua.runtime.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Publishes the small set of render objects that need per-client-tick updates. */
final class MLuaRenderTickIndex<T> {
    private final Map<Integer, T> members = new HashMap<>();
    private volatile List<T> snapshot = List.of();

    synchronized void update(int id, T object, boolean required) {
        T current = members.get(id);
        if (required) {
            if (current == object) return;
            members.put(id, object);
        } else {
            if (current == null) return;
            members.remove(id);
        }
        publish();
    }

    synchronized void remove(int id) {
        if (members.remove(id) != null) publish();
    }

    List<T> snapshot() { return snapshot; }

    private void publish() {
        snapshot = List.copyOf(members.values());
    }
}
