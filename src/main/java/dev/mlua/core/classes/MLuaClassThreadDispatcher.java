package dev.mlua.core.classes;

import dev.mlua.runtime.server.MLuaServerThread;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Keeps live world-backed Lua class methods on the thread that owns their Minecraft state. */
final class MLuaClassThreadDispatcher {
    private static final String DISPATCHERS = "__mlua_class_thread_dispatchers";

    private MLuaClassThreadDispatcher() { }

    static LuaValue bind(LuaValue implementation) {
        if (implementation instanceof OwnerThreadFunction) return implementation;
        return new OwnerThreadFunction(implementation);
    }

    static LuaValue bind(LuaTable receiver, LuaValue implementation) {
        if (serverLevel(receiver) == null) return implementation;
        if (implementation instanceof OwnerThreadFunction) return implementation;
        LuaValue metaValue = receiver.getmetatable();
        if (!metaValue.istable()) return bind(implementation);
        LuaTable meta = metaValue.checktable();
        LuaValue dispatchers = meta.rawget(DISPATCHERS);
        LuaTable cache = dispatchers.istable() ? dispatchers.checktable() : null;
        if (cache != null) {
            LuaValue existing = cache.rawget(implementation);
            if (existing.isfunction()) return existing;
        }
        LuaValue bound = bind(implementation);
        if (!meta.isfrozen()) {
            if (cache == null) {
                cache = new LuaTable();
                meta.rawset(DISPATCHERS, cache);
            }
            if (!cache.isfrozen()) cache.rawset(implementation, bound);
        }
        return bound;
    }

    private static final class OwnerThreadFunction extends VarArgFunction {
        private final LuaValue implementation;

        private OwnerThreadFunction(LuaValue implementation) {
            this.implementation = implementation;
        }

        @Override public Varargs invoke(Varargs args) {
            ServerLevel level = serverLevel(args.arg1());
            if (level == null) return implementation.invoke(args);
            return MLuaServerThread.call(level.getServer(), () -> implementation.invoke(args));
        }
    }

    private static ServerLevel serverLevel(LuaValue receiver) {
        if (!receiver.istable()) return null;
        LuaValue entityValue = receiver.rawget("__entity");
        if (entityValue.isuserdata(Entity.class)) {
            Level level = ((Entity) entityValue.touserdata(Entity.class)).level();
            if (level instanceof ServerLevel serverLevel) return serverLevel;
        }

        LuaValue blockEntityValue = receiver.rawget("__block_entity");
        if (blockEntityValue.isuserdata(BlockEntity.class)) {
            Level level = ((BlockEntity) blockEntityValue.touserdata(BlockEntity.class)).getLevel();
            if (level instanceof ServerLevel serverLevel) return serverLevel;
        }

        LuaValue levelValue = receiver.rawget("__level");
        if (!levelValue.isuserdata(Level.class)) levelValue = receiver.rawget("__world");
        if (levelValue.isuserdata(Level.class)
            && levelValue.touserdata(Level.class) instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        return null;
    }
}
