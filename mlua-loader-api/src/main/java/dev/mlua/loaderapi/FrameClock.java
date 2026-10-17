package dev.mlua.loaderapi;

/** Measures elapsed wall time between client render callbacks. */
public final class FrameClock {
    private long previousNanos;

    /**
     * Returns elapsed seconds since the previous sample. The first sample is
     * zero because no complete frame interval has been observed yet.
     */
    public synchronized double sampleSeconds() {
        long now = System.nanoTime();
        if (previousNanos == 0L) {
            previousNanos = now;
            return 0.0D;
        }

        long elapsedNanos = now - previousNanos;
        previousNanos = now;
        return elapsedNanos > 0L ? elapsedNanos / 1_000_000_000.0D : 0.0D;
    }
}
