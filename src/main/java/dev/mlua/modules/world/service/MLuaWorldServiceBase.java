package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.modules.players.MLuaClientPlayerAccess;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;

/** Shares live-world wrapper identity and lifecycle rules across Minecraft API generations. */
abstract class MLuaWorldServiceBase extends MLuaServiceBase {
    private final MLuaWorldInstanceCache worldInstances = new MLuaWorldInstanceCache();

    protected MLuaWorldServiceBase(MLuaService.Context context) {
        super(context);
    }

    protected final synchronized LuaTable getOrCreateWorld(Level level) {
        LuaTable cached = worldInstances.get(level);
        if (cached != null) return cached;
        LuaTable created = createWorld(level);
        if (created == null) throw new IllegalStateException("World wrapper factory returned null");
        worldInstances.put(level, created);
        return created;
    }

    protected abstract LuaTable createWorld(Level level);

    protected final void retainWorlds(Set<Level> activeLevels) {
        worldInstances.retain(activeLevels);
    }

    protected static Level clientLevel() {
        try {
            Object level = MLuaClientPlayerAccess.level();
            return level instanceof Level clientLevel ? clientLevel : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
