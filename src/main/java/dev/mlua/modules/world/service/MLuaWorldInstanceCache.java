package dev.mlua.modules.world.service;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;

/** Weak Lua wrapper identity storage; MLuaWorldServiceBase serializes access. */
final class MLuaWorldInstanceCache {
    private final Map<Level, WeakReference<LuaTable>> instances = new WeakHashMap<>();

    LuaTable get(Level level) {
        WeakReference<LuaTable> reference = this.instances.get(level);
        return reference == null ? null : reference.get();
    }

    void put(Level level, LuaTable instance) {
        this.instances.put(level, new WeakReference<>(instance));
    }

    void retain(Set<Level> activeLevels) {
        this.instances.keySet().removeIf(level -> !activeLevels.contains(level));
    }
}
