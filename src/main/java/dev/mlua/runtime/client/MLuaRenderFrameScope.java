package dev.mlua.runtime.client;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Lifetime guard shared by native objects exposed during one render callback. */
public final class MLuaRenderFrameScope {
    private volatile Thread renderThread = Thread.currentThread();
    private volatile boolean active = true;
    private final List<WeakReference<MLuaScopedRenderObject>> references = new ArrayList<>();

    public View view() {
        return new View(this);
    }

    Object reference(Object value) {
        if (value == null) return null;
        synchronized (references) {
            ensureUsable();
            MLuaScopedRenderObject reference = new MLuaScopedRenderObject(this, value);
            references.add(new WeakReference<>(reference));
            return reference;
        }
    }

    private Object unwrap(Object value) {
        ensureUsable();
        return value;
    }

    private void ensureUsable() {
        if (!active) {
            throw new IllegalStateException("This Minecraft render object is no longer valid; use it only inside its render callback");
        }
        if (Thread.currentThread() != renderThread) {
            throw new IllegalStateException("This Minecraft render object can only be used on the render thread that created it");
        }
    }

    void close() {
        synchronized (references) {
            active = false;
            renderThread = null;
            for (WeakReference<MLuaScopedRenderObject> reference : references) {
                MLuaScopedRenderObject scoped = reference.get();
                if (scoped != null) scoped.release();
            }
            references.clear();
        }
    }

    public static final class View {
        private final MLuaRenderFrameScope scope;

        private View(MLuaRenderFrameScope scope) {
            this.scope = scope;
        }

        public boolean isActive() {
            return scope.active && Thread.currentThread() == scope.renderThread;
        }

        public MLuaRenderFrameScope scope() {
            return scope;
        }
    }

    /** Lua userdata understood by the Java bridge; the wrapped native reference is never exposed directly. */
    public static final class MLuaScopedRenderObject implements dev.mlua.runtime.java.MLuaScopedJavaReference {
        private final MLuaRenderFrameScope scope;
        private volatile Object value;

        private MLuaScopedRenderObject(MLuaRenderFrameScope scope, Object value) {
            this.scope = scope;
            this.value = value;
        }

        public Object unwrap() {
            Object current = scope.unwrap(value);
            if (current == null) throw new IllegalStateException("This Minecraft render object has expired");
            return current;
        }

        private void release() { value = null; }
    }
}
