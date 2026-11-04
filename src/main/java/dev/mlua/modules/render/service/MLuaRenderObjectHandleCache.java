package dev.mlua.modules.render.service;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import org.luaj.vm2.LuaTable;

/** Owns weak Lua handle identity for live render-object IDs. */
final class MLuaRenderObjectHandleCache {
    private final ReferenceQueue<LuaTable> collectedHandles = new ReferenceQueue<>();
    private final Map<Integer, HandleReference> handles = new HashMap<>();

    synchronized LuaTable get(int id) {
        this.drainCollected();
        HandleReference reference = this.handles.get(id);
        LuaTable handle = reference == null ? null : reference.get();
        if (handle == null && reference != null) this.handles.remove(id, reference);
        return handle;
    }

    synchronized void put(int id, LuaTable handle) {
        this.drainCollected();
        this.handles.put(id, new HandleReference(id, handle, this.collectedHandles));
    }

    synchronized void remove(int id) {
        this.handles.remove(id);
    }

    synchronized void clear() {
        this.handles.clear();
        while (this.collectedHandles.poll() != null) { }
    }

    private void drainCollected() {
        HandleReference reference;
        while ((reference = (HandleReference) this.collectedHandles.poll()) != null) {
            this.handles.remove(reference.id, reference);
        }
    }

    private static final class HandleReference extends WeakReference<LuaTable> {
        private final int id;

        private HandleReference(int id, LuaTable handle, ReferenceQueue<LuaTable> queue) {
            super(handle, queue);
            this.id = id;
        }
    }
}
