package dev.mlua.runtime.client;

import dev.mlua.loaderapi.FrameClock;

/** Owns the latest render-frame delta shared by Lua render callbacks. */
public final class MLuaRenderFrameClock {
    private static volatile Snapshot current = Snapshot.EMPTY;

    private MLuaRenderFrameClock() { }

    /** Publishes a bounded frame duration; a non-positive sample means unavailable. */
    public static void publish(double deltaTime) {
        double bounded = FrameClock.boundedSeconds(deltaTime);
        current = bounded > 0.0D ? new Snapshot(bounded, true) : Snapshot.EMPTY;
    }

    /** Returns one immutable timing sample for a complete render callback. */
    public static Snapshot current() {
        return current;
    }

    /** Clears the sample when a world or client runtime is replaced. */
    public static void reset() {
        current = Snapshot.EMPTY;
    }

    public record Snapshot(double deltaTime, boolean available) {
        private static final Snapshot EMPTY = new Snapshot(0.0D, false);
    }
}
