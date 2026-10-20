package dev.mlua.modules.coregui.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Cached immutable HUD callback order, rebuilt only when a registration changes. */
final class MLuaHudElementSnapshot {
    private static final Object LOCK = new Object();
    private static final Map<String, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong();

    private MLuaHudElementSnapshot() { }

    static List<MLuaHudElement> get(String position, Supplier<List<MLuaHudElement>> source) {
        while (true) {
            long observedGeneration = GENERATION.get();
            Snapshot cached = SNAPSHOTS.get(position);
            if (cached != null && cached.generation() == observedGeneration) return cached.elements();

            List<MLuaHudElement> ordered = new ArrayList<>(source.get());
            ordered.sort(Comparator.comparingInt((MLuaHudElement element) -> element.priority)
                .thenComparingLong(element -> element.order));
            List<MLuaHudElement> snapshot = List.copyOf(ordered);

            synchronized (LOCK) {
                if (GENERATION.get() != observedGeneration) continue;
                Snapshot existing = SNAPSHOTS.get(position);
                if (existing != null && existing.generation() == observedGeneration) return existing.elements();
                SNAPSHOTS.put(position, new Snapshot(observedGeneration, snapshot));
                return snapshot;
            }
        }
    }

    static void invalidate() {
        synchronized (LOCK) {
            GENERATION.incrementAndGet();
            SNAPSHOTS.clear();
        }
    }

    private record Snapshot(long generation, List<MLuaHudElement> elements) { }
}
