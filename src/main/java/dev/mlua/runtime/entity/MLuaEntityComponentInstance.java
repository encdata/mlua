package dev.mlua.runtime.entity;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.module.MLuaRegistration;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

import java.lang.ref.WeakReference;

/** Owns one entity component's callbacks, state, stable Lua handle, and lifecycle. */
final class MLuaEntityComponentInstance implements MLuaEntityComponentCallbacks.Component,
        MLuaEntityComponentHandle.Access {
    private volatile WeakReference<Entity> entityRef;
    private final Side side;
    private final String name;
    private final String ownerModId;
    private final Object runtimeOwner;
    private volatile MLuaContext context;
    private final LuaTable state;
    private final LuaTable handle;
    private final long registrationOrder;
    private volatile LuaTable callbacks;
    private volatile boolean enabled = true;
    private volatile boolean failed;
    private volatile boolean removed;
    private volatile String errorMessage;

    MLuaEntityComponentInstance(Entity entity, Side side, String name, String ownerModId,
            LuaTable callbacks, MLuaContext context, long registrationOrder) {
        this.entityRef = new WeakReference<>(entity);
        this.side = side;
        this.name = name;
        this.ownerModId = ownerModId;
        this.runtimeOwner = context != null ? context.session() : null;
        this.context = context;
        this.callbacks = callbacks;
        this.state = MLuaEntityComponentHandle.initialState(callbacks);
        this.registrationOrder = registrationOrder;
        MLuaRegistration registration = context == null ? null : context.registration();
        this.handle = MLuaEntityComponentHandle.create(this, registration);
    }

    @Override public Entity entity() {
        Entity entity = this.entityRef.get();
        if (entity == null) throw new LuaError("[MLua] Component entity is no longer available");
        return entity;
    }

    @Override public Side side() { return this.side; }
    @Override public String name() { return this.name; }
    @Override public MLuaContext context() { return this.context; }
    @Override public LuaTable callbacks() { return this.callbacks; }
    @Override public Entity entityForCallback() { return this.entityRef.get(); }
    @Override public LuaTable handle() { return this.handle; }
    @Override public LuaTable state() { return this.state; }
    @Override public boolean removed() { return this.removed; }
    @Override public boolean failed() { return this.failed; }
    String ownerModId() { return this.ownerModId; }
    Object runtimeOwner() { return this.runtimeOwner; }
    long registrationOrder() { return this.registrationOrder; }

    @Override public void disable(String message) {
        this.failed = true;
        this.enabled = false;
        this.errorMessage = message;
    }

    @Override public boolean enabled() { return this.enabled; }
    @Override public String errorMessage() { return this.errorMessage; }
    @Override public void setEnabled(boolean value) { this.enabled = !this.removed && !this.failed && value; }

    @Override public void destroy() {
        MLuaEntityComponentManager.remove(this.entity(), this.side, this.name, this.ownerModId, this.runtimeOwner);
    }

    void rebind(Entity entity) {
        this.entityRef = new WeakReference<>(entity);
    }

    void replaceCallbacks(LuaTable replacement, MLuaContext replacementContext) {
        MLuaContext currentContext = this.context;
        MLuaContext lockContext = currentContext != null ? currentContext : replacementContext;
        Runnable update = () -> {
            if (this.removed) return;
            this.callbacks = replacement;
            if (replacementContext != null) this.context = replacementContext;
            if (this.failed) {
                this.failed = false;
                this.errorMessage = null;
                this.enabled = true;
            }
        };
        if (lockContext != null) {
            MLuaLuaBindings.withExecutionLock(lockContext, () -> {
                update.run();
                return null;
            });
        } else {
            synchronized (this) {
                update.run();
            }
        }
    }

    synchronized boolean markRemoved() {
        if (this.removed) return false;
        this.removed = true;
        this.enabled = false;
        MLuaEntityComponentManager.releaseComponent();
        return true;
    }
}
