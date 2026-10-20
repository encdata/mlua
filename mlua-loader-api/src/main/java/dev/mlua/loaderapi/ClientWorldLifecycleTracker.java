package dev.mlua.loaderapi;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Delivers client-world transitions after the client has installed the active world. */
public final class ClientWorldLifecycleTracker {
    private static final System.Logger LOGGER = System.getLogger(ClientWorldLifecycleTracker.class.getName());

    private final Supplier<?> activeWorld;
    private final List<Consumer<Object>> loadListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Object>> unloadListeners = new CopyOnWriteArrayList<>();
    private Object observedWorld;

    public ClientWorldLifecycleTracker(Supplier<?> activeWorld) {
        this.activeWorld = Objects.requireNonNull(activeWorld, "activeWorld");
    }

    public void onLoad(Consumer<Object> listener) {
        loadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void onUnload(Consumer<Object> listener) {
        unloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Call once at the end of each client tick. Unload is delivered before load on a dimension swap. */
    public void tick() {
        Object previous;
        Object current = activeWorld.get();
        synchronized (this) {
            if (current == observedWorld) return;
            previous = observedWorld;
            observedWorld = current;
        }
        if (previous != null) dispatch(unloadListeners, previous, "client world unload");
        if (current != null) dispatch(loadListeners, current, "client world load");
    }

    private static void dispatch(List<Consumer<Object>> listeners, Object world, String eventName) {
        for (Consumer<Object> listener : listeners) {
            try {
                listener.accept(world);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                LOGGER.log(System.Logger.Level.ERROR, "A listener failed during " + eventName, failure);
            }
        }
    }
}
