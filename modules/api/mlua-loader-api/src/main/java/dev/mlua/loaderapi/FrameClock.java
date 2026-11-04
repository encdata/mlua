package dev.mlua.loaderapi;

/** Measures bounded elapsed wall time between client render callbacks. */
public final class FrameClock {
    public static final double MAX_DELTA_SECONDS = 1.0D;
    private long previousNanos;

    /** Normalizes frame durations shared by render contexts and RunService. */
    public static double boundedSeconds(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0D) return 0.0D;
        return Math.min(deltaSeconds, MAX_DELTA_SECONDS);
    }

    /**
     * Returns elapsed seconds, capped at one second to avoid large resume jumps.
     * The first sample is zero because no complete frame interval exists yet.
     */
    public synchronized double sampleSeconds() {
        long now = System.nanoTime();
        if (previousNanos == 0L) {
            previousNanos = now;
            return 0.0D;
        }

        long elapsedNanos = now - previousNanos;
        previousNanos = now;
        return boundedSeconds(elapsedNanos > 0L ? elapsedNanos / 1_000_000_000.0D : 0.0D);
    }
}
