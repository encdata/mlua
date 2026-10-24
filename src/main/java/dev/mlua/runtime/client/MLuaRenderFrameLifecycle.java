package dev.mlua.runtime.client;

import dev.mlua.core.MLuaLogger;
import dev.mlua.loaderapi.LoaderApi;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns work that must advance or be released at a client frame boundary. */
final class MLuaRenderFrameLifecycle {
    /** Fallback release for collector submissions Minecraft drops before invoking their renderer. */
    private static final ConcurrentLinkedQueue<Runnable> DEFERRED_CLEANUPS = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean CLEANUP_FAILURE_REPORTED = new AtomicBoolean();
    private static boolean animationClockRegistered;

    private MLuaRenderFrameLifecycle() {
    }

    static synchronized void registerAnimationClock() {
        if (animationClockRegistered) return;
        animationClockRegistered = true;
        LoaderApi.lifecycle().onClientTickEnd(MLuaRenderFrameLifecycle::onClientTickEnd);
    }

    static void deferCleanup(Runnable cleanup) {
        if (cleanup != null) DEFERRED_CLEANUPS.add(cleanup);
    }

    static void drainDeferredCleanups() {
        Runnable cleanup;
        while ((cleanup = DEFERRED_CLEANUPS.poll()) != null) {
            try {
                cleanup.run();
            } catch (RuntimeException failure) {
                if (CLEANUP_FAILURE_REPORTED.compareAndSet(false, true)) {
                    MLuaLogger.LOGGER.warn("[MLua] Deferred render resource cleanup failed", failure);
                }
            }
        }
    }

    private static void onClientTickEnd() {
        drainDeferredCleanups();
        MLuaClientRenderManager.tickAnimations();
    }
}
