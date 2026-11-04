package dev.mlua.loaderapi;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;
import java.util.function.Consumer;

/** Frame-scoped pose, buffer, and camera values exposed by loader world-render hooks. */
public final class ClientWorldRenderContext {
    private final Thread renderThread = Thread.currentThread();
    private volatile Object nativeContext;
    private volatile Object poseStack;
    private volatile Object renderSource;
    private volatile Object camera;
    private volatile Runnable finish;
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicBoolean dispatched = new AtomicBoolean();

    public ClientWorldRenderContext(Object nativeContext, Object poseStack, Object renderSource,
                                    Object camera, Runnable finish) {
        this.nativeContext = nativeContext;
        this.poseStack = poseStack;
        this.renderSource = renderSource;
        this.camera = camera;
        this.finish = finish == null ? () -> { } : finish;
    }

    public Object nativeContext() { requireActive(); return nativeContext; }
    public Object poseStack() { requireActive(); return poseStack; }
    public Object renderSource() { requireActive(); return renderSource; }
    public Object camera() { requireActive(); return camera; }

    /** Whether this context can still expose its frame-owned Minecraft objects. */
    public boolean isActive() {
        return !finished.get() && Thread.currentThread() == renderThread;
    }

    /** Runs this context's single loader callback and expires it even when the callback fails. */
    public void invoke(Consumer<ClientWorldRenderContext> callback) {
        Objects.requireNonNull(callback, "callback");
        requireActive();
        if (!dispatched.compareAndSet(false, true)) {
            throw new IllegalStateException("This world render context has already dispatched its callback");
        }
        Throwable callbackFailure = null;
        try {
            callback.accept(this);
        } catch (RuntimeException | Error failure) {
            callbackFailure = failure;
            throw failure;
        } finally {
            try {
                finish();
            } catch (RuntimeException | Error finishFailure) {
                if (callbackFailure == null) throw finishFailure;
                if (callbackFailure != finishFailure) callbackFailure.addSuppressed(finishFailure);
            }
        }
    }

    /** Flushes frame geometry once after all callbacks have submitted their vertices. */
    public void finish() {
        requireRenderThread();
        if (!finished.compareAndSet(false, true)) return;
        Runnable flush = finish;
        try {
            if (flush != null) flush.run();
        } finally {
            nativeContext = null;
            poseStack = null;
            renderSource = null;
            camera = null;
            finish = null;
        }
    }

    private void requireActive() {
        requireRenderThread();
        if (finished.get()) {
            throw new IllegalStateException("This world render context expired after its frame was flushed");
        }
    }

    private void requireRenderThread() {
        if (Thread.currentThread() != renderThread) {
            throw new IllegalStateException("This world render context can only be used on its creating render thread");
        }
    }
}
