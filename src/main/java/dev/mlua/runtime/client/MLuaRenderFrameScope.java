package dev.mlua.runtime.client;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.List;

/** Lifetime guard shared by native objects exposed during one render callback. */
public final class MLuaRenderFrameScope implements AutoCloseable {
    /** Bounds temporary Java wrappers a single render callback can create. */
    public static final int MAX_REFERENCES_PER_FRAME = 4_096;
    private static final ThreadLocal<Deque<View>> ACTIVE_SCOPES = new ThreadLocal<>();
    private volatile Thread renderThread = Thread.currentThread();
    private volatile boolean active = true;
    private final List<WeakReference<MLuaScopedRenderObject>> references = new ArrayList<>();
    private final View view = new View(this);

    public View view() {
        return view;
    }

    Object reference(Object value) {
        if (value == null) return null;
        synchronized (references) {
            ensureUsable();
            if (references.size() >= MAX_REFERENCES_PER_FRAME) {
                references.removeIf(reference -> reference.get() == null);
                if (references.size() >= MAX_REFERENCES_PER_FRAME) {
                    throw new IllegalStateException("A render callback exceeded the limit of "
                        + MAX_REFERENCES_PER_FRAME + " temporary Java references");
                }
            }
            MLuaScopedRenderObject reference = new MLuaScopedRenderObject(this, value);
            references.add(new WeakReference<>(reference));
            return reference;
        }
    }

    private Object scopedReference(Object value) {
        return reference(value);
    }

    private Object unwrap(Object value) {
        ensureUsable();
        return value;
    }

    private static void push(View view) {
        Deque<View> scopes = ACTIVE_SCOPES.get();
        if (scopes == null) {
            scopes = new ArrayDeque<>();
            ACTIVE_SCOPES.set(scopes);
        }
        scopes.push(view);
    }

    private static void pop(View view) {
        Deque<View> scopes = ACTIVE_SCOPES.get();
        if (scopes == null) return;
        if (scopes.peek() == view) scopes.pop();
        else scopes.removeFirstOccurrence(view);
        if (scopes.isEmpty()) ACTIVE_SCOPES.remove();
    }

    /** Returns the render scope active on this thread, if a Lua render callback is running. */
    public static View currentView() {
        Deque<View> scopes = ACTIVE_SCOPES.get();
        return scopes == null ? null : scopes.peek();
    }

    private void ensureUsable() {
        if (!active) {
            throw new IllegalStateException("This Minecraft render object is no longer valid; use it only inside its render callback");
        }
        if (Thread.currentThread() != renderThread) {
            throw new IllegalStateException("This Minecraft render object can only be used on the render thread that created it");
        }
    }

    @Override
    public void close() {
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

        /** Makes implicit Java bridge results share this callback's lifetime. */
        public ScopeBinding enter() {
            scope.ensureUsable();
            push(this);
            return new ScopeBinding(this, Thread.currentThread());
        }

        /** Wraps an object derived from this frame so it shares the frame's lifetime and thread checks. */
        public Object scopedReference(Object value) {
            return scope.scopedReference(value);
        }
    }

    /** Restores the previous active scope after nested Lua or Java calls return. */
    public static final class ScopeBinding implements AutoCloseable {
        private final View view;
        private final Thread thread;
        private boolean closed;

        private ScopeBinding(View view, Thread thread) {
            this.view = view;
            this.thread = thread;
        }

        @Override
        public void close() {
            if (closed) return;
            if (Thread.currentThread() != thread) {
                throw new IllegalStateException("A render callback scope must be closed on its creating render thread");
            }
            closed = true;
            pop(view);
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

        public View scopeView() {
            return scope.view();
        }

        private void release() { value = null; }
    }
}
