package dev.mlua.modules.coregui.service;

import dev.mlua.core.MLuaContext;
import org.luaj.vm2.LuaValue;

/** Runtime-owned callback and ordering metadata for one registered HUD element. */
final class MLuaHudElement {
    final String id;
    final String owner;
    final String anchor;
    final boolean before;
    final String position;
    final int priority;
    final LuaValue callback;
    final long order;
    final Object executionLock;
    final MLuaContext context;
    volatile boolean failed;

    MLuaHudElement(String id, String owner, String anchor, boolean before, String position,
                   LuaValue callback, int priority, long order, Object executionLock, MLuaContext context) {
        this.id = id;
        this.owner = owner;
        this.anchor = anchor;
        this.before = before;
        this.position = position;
        this.callback = callback;
        this.priority = priority;
        this.order = order;
        this.executionLock = executionLock;
        this.context = context;
    }
}
