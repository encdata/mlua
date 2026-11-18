package dev.mlua.loaderapi;

import dev.mlua.common.MLuaFatalErrors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class EventListenerRegistry {
    private final Map<Object, Map<String, List<ListenerRegistration>>> listeners = new IdentityHashMap<>();

    public synchronized void register(String hook, Object owner, Object listener, Runnable registration) {
        Objects.requireNonNull(hook, "hook");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(registration, "registration");
        List<ListenerRegistration> registrations = this.listeners.computeIfAbsent(owner, ignored -> new HashMap<>())
            .computeIfAbsent(hook, ignored -> new ArrayList<>());
        ListenerRegistration listenerRegistration = new ListenerRegistration(listener);
        registrations.add(listenerRegistration);
        try {
            registration.run();
        } catch (RuntimeException | Error failure) {
            Map<String, List<ListenerRegistration>> byHook = this.listeners.get(owner);
            List<ListenerRegistration> hookRegistrations = byHook != null ? byHook.get(hook) : null;
            if (hookRegistrations != null) {
                hookRegistrations.remove(listenerRegistration);
                if (hookRegistrations.isEmpty()) byHook.remove(hook);
                if (byHook.isEmpty()) this.listeners.remove(owner);
            }
            throw failure;
        }
    }

    public void unregister(String hook, Object owner, Consumer<Object> unregister) {
        Objects.requireNonNull(hook, "hook");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(unregister, "unregister");
        List<ListenerRegistration> registrations;
        synchronized (this) {
            Map<String, List<ListenerRegistration>> byHook = this.listeners.get(owner);
            registrations = byHook != null ? byHook.remove(hook) : null;
            if (byHook != null && byHook.isEmpty()) this.listeners.remove(owner);
        }
        if (registrations == null) return;

        Throwable failure = null;
        for (ListenerRegistration listenerRegistration : registrations) {
            try {
                unregister.accept(listenerRegistration.listener);
            } catch (RuntimeException | Error error) {
                MLuaFatalErrors.rethrowIfFatal(error);
                if (failure == null || (error instanceof Error && !(failure instanceof Error))) {
                    if (failure != null && failure != error) error.addSuppressed(failure);
                    failure = error;
                } else if (failure != error) {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }

    private static final class ListenerRegistration {
        private final Object listener;

        private ListenerRegistration(Object listener) {
            this.listener = listener;
        }
    }
}
