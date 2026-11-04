package dev.mlua.loaderapi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClientWorldLifecycleTrackerTest {
    @Test
    void clientWorldTransitionsLoadOnceAndUnloadBeforeTheNextWorldLoads() {
        Object overworld = new Object();
        Object nether = new Object();
        AtomicReference<Object> activeWorld = new AtomicReference<>();
        List<String> transitions = new ArrayList<>();
        ClientWorldLifecycleTracker tracker = new ClientWorldLifecycleTracker(activeWorld::get);
        tracker.onLoad(world -> transitions.add("load:" + name(world, overworld, nether)));
        tracker.onUnload(world -> transitions.add("unload:" + name(world, overworld, nether)));

        tracker.tick();
        assertEquals(List.of(), transitions, "a menu with no active world is not a world transition");

        activeWorld.set(overworld);
        tracker.tick();
        tracker.tick();
        activeWorld.set(nether);
        tracker.tick();
        activeWorld.set(null);
        tracker.tick();

        assertEquals(List.of("load:overworld", "unload:overworld", "load:nether", "unload:nether"), transitions);
    }

    @Test
    void failedListenerDoesNotPreventLaterWorldLifecycleListeners() {
        Object world = new Object();
        AtomicReference<Object> activeWorld = new AtomicReference<>(world);
        List<Object> observed = new ArrayList<>();
        ClientWorldLifecycleTracker tracker = new ClientWorldLifecycleTracker(activeWorld::get);
        tracker.onLoad(ignored -> { throw new IllegalStateException("test listener failure"); });
        tracker.onLoad(observed::add);

        tracker.tick();

        assertEquals(List.of(world), observed);
    }

    private static String name(Object world, Object overworld, Object nether) {
        if (world == overworld) return "overworld";
        if (world == nether) return "nether";
        throw new AssertionError("unexpected world instance");
    }
}
