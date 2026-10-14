package dev.mlua.minecraft.binding;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.service.MLuaServiceProperties;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Lua wrapper for a Minecraft level/world instance. */
public final class MLuaLevelWrapper {
    private static final Map<MLuaRegistration, Map<Level, WeakReference<LuaTable>>> LEVEL_INSTANCES = new WeakHashMap<>();

    private MLuaLevelWrapper() {
    }

    public static LuaTable levelTable(Level level) {
        return levelTable(level, MLuaRuntime.get());
    }

    public static LuaTable levelTable(Level level, MLuaRegistration registration) {
        synchronized (LEVEL_INSTANCES) {
            Map<Level, WeakReference<LuaTable>> instances = LEVEL_INSTANCES.computeIfAbsent(registration, ignored -> new WeakHashMap<>());
            WeakReference<LuaTable> reference = instances.get(level);
            LuaTable cached = reference != null ? reference.get() : null;
            if (cached != null) return cached;
            LuaTable fields = new LuaTable();
            fields.rawset("__side", LuaValue.valueOf(level.isClientSide() ? "Client" : "Server"));
            fields.rawset("__level", LuaValue.userdataOf(level));
            LuaTable table = registration.createClassInstance("Level", fields);
            MLuaServiceProperties.attach(table);
            instances.put(level, new WeakReference<>(table));
            return table;
        }
    }

    public static Level levelFromLua(LuaValue value) {
        if (value == null || !value.istable()) return null;
        LuaValue stored = value.checktable().rawget("__level");
        return stored.isuserdata(Level.class) ? (Level) stored.touserdata(Level.class) : null;
    }

    public static LuaTable classTable() {
        return classTable(MLuaRuntime.get());
    }

    public static LuaTable classTable(MLuaRegistration registration) {
        return createClass(registration);
    }

    private static LuaTable createClass(MLuaRegistration registration) {
        LuaTable table = registration.registerClass("Level", registration.requireInstanceBaseClassName(), "A live Minecraft world instance, with dimension, time, and block access.");
        if (!table.rawget("__initialized").toboolean()) {
            table.rawset("__initialized", LuaValue.TRUE);
            registration.registerClassFunction("Level", "GetDimension", self((self, ignored) -> LuaValue.valueOf(level(self).dimension().location().toString())), "Returns this world's dimension identifier.");
            registration.registerClassFunction("Level", "IsClientSide", self((self, ignored) -> LuaValue.valueOf(level(self).isClientSide())), "Returns whether this world instance belongs to the client.");
            registration.registerClassFunction("Level", "GetGameTime", self((self, ignored) -> LuaValue.valueOf(level(self).getGameTime())), "Returns the total number of ticks this world has been running.");
            registration.registerClassFunction("Level", "GetDayTime", self((self, ignored) -> LuaValue.valueOf(level(self).getDayTime())), "Returns this world's current day time in ticks.");
            registration.registerClassFunction("Level", "SetDayTime", self((self, args) -> {
                if (level(self) instanceof ServerLevel serverLevel) {
                    serverLevel.setDayTime(args.checklong(2));
                }
                return LuaValue.NONE;
            }), "Sets the day time on a server world.");
            registration.registerClassFunction("Level", "GetBlock", self((self, args) -> {
                net.minecraft.core.BlockPos pos = MLuaBlockWrapper.vectorToBlockPos(args.arg(2));
                if (pos == null) throw new LuaError("[MLua] Level:GetBlock expects a native Vector3 or CFrame position");
                return MLuaBlockWrapper.blockTable(level(self), pos, registration);
            }), "Returns the live block instance at a position in this world.");
        }
        return table;
    }

    private static Level level(LuaValue self) {
        Level level = levelFromLua(self);
        if (level == null) throw new LuaError("Expected Level wrapper");
        return level;
    }

    @FunctionalInterface
    private interface Body {
        LuaValue apply(LuaValue self, Varargs args);
    }

    private static VarArgFunction self(Body body) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue result = body.apply(args.arg1(), args);
                return result;
            }
        };
    }
}
