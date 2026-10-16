package dev.mlua.runtime.client;

/** Lifetime guard shared by native objects exposed during one render callback. */
public final class MLuaRenderFrameScope {
    private volatile Thread renderThread = Thread.currentThread();
    private volatile boolean active = true;

    public View view() {
        return new View(this);
    }

    Object reference(Object value) {
        return value == null ? null : new MLuaScopedRenderObject(this, value);
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
        active = false;
        renderThread = null;
    }

    public static final class View {
        private final MLuaRenderFrameScope scope;

        private View(MLuaRenderFrameScope scope) {
            this.scope = scope;
        }

        public boolean isActive() {
            return scope.active;
        }

        MLuaRenderFrameScope scope() {
            return scope;
        }
    }

    /** Lua userdata understood by the Java bridge; the wrapped native reference is never exposed directly. */
    public static final class MLuaScopedRenderObject implements dev.mlua.runtime.java.MLuaScopedJavaReference {
        private final MLuaRenderFrameScope scope;
        private final Object value;

        private MLuaScopedRenderObject(MLuaRenderFrameScope scope, Object value) {
            this.scope = scope;
            this.value = value;
        }

        public Object unwrap() {
            return scope.unwrap(value);
        }
    }
}
