package dev.mlua.core;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaThreadHandoffTest {
    @Test
    void runsImmediatelyWhenAlreadyOnOwnerThread() {
        Queue<Runnable> queued = new ArrayDeque<>();
        AtomicInteger calls = new AtomicInteger();

        MLuaThreadHandoff.execute(() -> true, queued::add, calls::incrementAndGet);

        assertEquals(1, calls.get());
        assertEquals(0, queued.size());
    }

    @Test
    void queuesWorkWhenCalledOffOwnerThread() {
        Queue<Runnable> queued = new ArrayDeque<>();
        Executor ownerExecutor = queued::add;
        AtomicInteger calls = new AtomicInteger();

        MLuaThreadHandoff.execute(() -> false, ownerExecutor, calls::incrementAndGet);

        assertEquals(0, calls.get());
        queued.remove().run();
        assertEquals(1, calls.get());
    }
}
