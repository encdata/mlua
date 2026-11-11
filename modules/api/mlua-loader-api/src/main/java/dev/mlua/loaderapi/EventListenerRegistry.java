package dev.mlua.loaderapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class EventListenerRegistry {
    private final Map<Object, Map<String, List<Object>>> listeners = new IdentityHashMap<>();

    public synchronized void register(String hook, Object owner, Object listener, Runnable registration) {
        Objects.requireNonNull(hook, "hook");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(registration, "registration");
        this.listeners.computeIfAbsent(owner, ignored -> new HashMap<>())
            .computeIfAbsent(hook, ignored -> new ArrayList<>()).add(listener);
        try {
            registration.run();
        } catch (RuntimeException | Error failure) {
            Map<String, List<Object>> byHook = this.listeners.get(owner);
            List<Object> registered = byHook != null ? byHook.get(hook) : null;
            if (registered != null) {
                for (int index = 0; index < registered.size(); index++) {
                    if (registered.get(index) == listener) {
                        registered.remove(index);
                        break;
                    }
                }
                if (registered.isEmpty()) byHook.remove(hook);
                if (byHook.isEmpty()) this.listeners.remove(owner);
            }
            throw failure;
        }
    }

    public void unregister(String hook, Object owner, Consumer<Object> unregister) {
        Objects.requireNonNull(hook, "hook");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(unregister, "unregister");
        List<Object> registered;
        synchronized (this) {
            Map<String, List<Object>> byHook = this.listeners.get(owner);
            registered = byHook != null ? byHook.remove(hook) : null;
            if (byHook != null && byHook.isEmpty()) this.listeners.remove(owner);
        }
        if (registered == null) return;

        RuntimeException failure = null;
        for (Object listener : registered) {
            try {
                unregister.accept(listener);
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
