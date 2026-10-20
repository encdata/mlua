package dev.mlua.modules.world.service;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.modules.world.WorldModule;
import dev.mlua.modules.world.MLuaChunkWrapper;
import dev.mlua.runtime.server.MLuaServerThread;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaWorldService extends MLuaServiceBase {
    private final MLuaWorldInstanceCache worldInstances = new MLuaWorldInstanceCache();

    public MLuaWorldService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "World";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("__side", context.mlua().side().name());
        table.set("Current", serviceFunction(args -> worldValue(currentLevel())));
        table.set("Get", serviceFunction(args -> worldValue(resolveLevel(args.checkjstring(2)))));
        table.set("GetAll", serviceFunction(args -> allWorlds()));
        table.set("GetSide", serviceFunction(args -> LuaValue.valueOf(context.mlua().side().name())));
        table.set("GetWeather", currentWorldMethod("GetWeather"));
        table.set("SetWeather", currentWorldMethod("SetWeather"));
        WorldModule.bindWorldServiceMethods(table, this::currentWorldMethod);
        return supportColonAndDotCalls(table);
    }

    private VarArgFunction currentWorldMethod(String methodName) {
        return serviceFunction(args -> {
                Level level = MLuaWorldService.this.currentLevel();
                if (level == null) return LuaValue.NIL;
                LuaTable instance = MLuaWorldService.this.world(level);
                LuaValue method = instance.get(methodName);
                if (!method.isfunction()) {
                    throw new LuaError("[MLua] Current World instance does not implement " + methodName + ".");
                }
                LuaValue[] forwarded = new LuaValue[Math.max(1, args.narg())];
                forwarded[0] = instance;
                for (int index = 2; index <= args.narg(); index++) forwarded[index - 1] = args.arg(index);
                return method.invoke(LuaValue.varargsOf(forwarded));
            });
    }

    private VarArgFunction serviceFunction(Function<Varargs, Varargs> action) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                try {
                    MinecraftServer server = context.mlua().server();
                    if (context.mlua().side() == Side.SERVER && server != null && !server.isSameThread()) {
                        return MLuaServerThread.call(server, () -> action.apply(args));
                    }
                    return action.apply(args);
                } catch (LuaError error) {
                    throw error;
                } catch (Exception error) {
                    throw MLuaLuaErrors.javaFailure("World service call", error);
                }
            }
        };
    }

    private Level currentLevel() {
        if (context.mlua().side() == Side.CLIENT) {
            return clientLevel();
        }
        return context.mlua().server() == null ? null : context.mlua().server().overworld();
    }

    private Level resolveLevel(String id) {
        String requested = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        if (context.mlua().side() == Side.CLIENT) {
            Level level = currentLevel();
            return matches(level, requested) ? level : null;
        }
        if (context.mlua().server() == null) {
            return null;
        }
        for (ServerLevel level : context.mlua().server().getAllLevels()) {
            if (matches(level, requested)) {
                return level;
            }
        }
        return null;
    }

    private LuaTable allWorlds() {
        LuaTable worlds = new LuaTable();
        int index = 1;
        if (context.mlua().side() == Side.CLIENT) {
            Level level = currentLevel();
            if (level != null) {
                worlds.set(index, world(level));
            }
            return worlds;
        }
        if (context.mlua().server() != null) {
            for (ServerLevel level : context.mlua().server().getAllLevels()) {
                worlds.set(index++, world(level));
            }
        }
        return worlds;
    }

    private LuaValue worldValue(Level level) {
        return level == null ? LuaValue.NIL : world(level);
    }

    private static boolean matches(Level level, String id) {
        return level != null && level.dimension().identifier().toString().equals(id);
    }

    private synchronized LuaTable world(Level level) {
        LuaTable cached = worldInstances.get(level);
        if (cached != null) return cached;

        LuaTable fields = new LuaTable();
        fields.set("__world", LuaValue.userdataOf(level));
        fields.set("__level", LuaValue.userdataOf(level));
        fields.set("__side", LuaValue.valueOf(level.isClientSide() ? "CLIENT" : "SERVER"));
        LuaTable result = context.mlua().registration().createClassInstance("World", fields);
        java.util.Map<String, LuaValue> implementations = new java.util.HashMap<>();
        MLuaWorldEnvironmentApi26.bind(this, context, result, implementations, level);
        MLuaWorldBlockQueryApi26.bind(this, context, result, implementations, level);
        MLuaWorldRaycastApi26.bind(this, context, result, implementations, level);
        MLuaWorldBlockMutationApi26.bind(this, context, result, implementations, level);
        MLuaWorldEffectsApi26.bind(this, context, result, implementations, level);
        MLuaWorldDataApi26.bind(this, context, result, implementations, level);
        MLuaWorldEntityQueryApi26.bind(this, context, result, implementations, level);
        MLuaChunkWrapper.bindWorldMethods(result, context.mlua().registration());
        worldInstances.put(level, result);
        return result;
    }

    private static Level clientLevel() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            Object level = minecraft.getField("level").get(instance);
            return level instanceof Level clientLevel ? clientLevel : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static ServerLevel requireServerWorld(Level level, String method) {
        if (level instanceof ServerLevel serverLevel) return serverLevel;
        throw new LuaError("[MLua] " + method + " is only available on a server world.");
    }

    static ServerClockManager serverClock(ServerLevel level) {
        return (ServerClockManager) level.clockManager();
    }

    static Holder<WorldClock> worldClock(ServerLevel level) {
        return level.dimensionType().defaultClock()
            .orElseThrow(() -> new LuaError("[MLua] This dimension does not define a world clock."));
    }

    void bindWorldMethod(LuaTable instance, java.util.Map<String, LuaValue> implementations,
                                 String name, LuaValue implementation) {
        implementations.put(name, implementation);
        MLuaClassMethods.bindInstanceMethod(context.mlua().registration(), "World", instance,
            name, implementation);
    }

    void bindWorldAlias(LuaTable instance, java.util.Map<String, LuaValue> implementations,
                                String alias, String source) {
        LuaValue implementation = implementations.get(source);
        if (implementation == null) throw new IllegalStateException("World method is not bound: " + source);
        bindWorldMethod(instance, implementations, alias, implementation);
    }

    static VarArgFunction levelFunction(Level level, BiFunction<Level, Varargs, LuaValue> action) {
        return function(args -> {
            if (level instanceof ServerLevel serverLevel) {
                return MLuaServerThread.call(serverLevel.getServer(), () -> action.apply(serverLevel, args));
            }
            return action.apply(level, args);
        });
    }

    private static VarArgFunction function(Function<Varargs, LuaValue> action) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return action.apply(args);
                } catch (LuaError error) {
                    throw error;
                } catch (Exception error) {
                    throw MLuaLuaErrors.javaFailure("World service call", error);
                }
            }
        };
    }

}
