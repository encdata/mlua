package dev.mlua.runtime.client;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

/** Allocates positive runtime handles without reusing ids that are still active. */
final class MLuaHandleIdAllocator {
    private MLuaHandleIdAllocator() { }

    static int nextAvailable(AtomicInteger sequence, IntPredicate inUse, int maximumActive) {
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(inUse, "inUse");
        if (maximumActive < 0) throw new IllegalArgumentException("maximumActive must be non-negative");
        // At most maximumActive ids can be occupied, so one extra candidate must be free.
        for (int attempt = 0; attempt <= maximumActive; attempt++) {
            int candidate = sequence.getAndUpdate(current -> current == Integer.MAX_VALUE ? 1 : current + 1);
            if (candidate > 0 && !inUse.test(candidate)) return candidate;
        }
        throw new IllegalStateException("No free positive runtime handle id is available");
    }
}
