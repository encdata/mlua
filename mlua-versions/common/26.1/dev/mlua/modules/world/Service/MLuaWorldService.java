package dev.mlua.modules.world.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.LuaBackedService;
import dev.mlua.core.service.MLuaService;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaWorldService implements MLuaService, LuaBackedService {
    private final MLuaService.Context context;

    public MLuaWorldService(MLuaService.Context context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "World";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("__side", context.mlua().side().name());
        table.set("Current", function(args -> worldValue(currentLevel())));
        table.set("Get", function(args -> worldValue(resolveLevel(args.checkjstring(2)))));
        table.set("GetAll", function(args -> allWorlds()));
        table.set("GetSide", function(args -> LuaValue.valueOf(context.mlua().side().name())));
        return table;
    }

    private Level currentLevel() {
        if (context.mlua().side() == Side.CLIENT) {
            return Minecraft.getInstance().level;
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

    private LuaTable world(Level level) {
        registerWorldClassMethods();
        LuaTable fields = new LuaTable();
        fields.set("__world", LuaValue.userdataOf(level));
        fields.set("__side", LuaValue.valueOf(level.isClientSide() ? "CLIENT" : "SERVER"));
        LuaTable result = context.mlua().registration().createClassInstance("World", fields);
        result.set("GetId", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.dimension().identifier().toString())));
        result.set("GetDimension", result.get("GetId"));
        result.set("GetSide", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.isClientSide() ? "CLIENT" : "SERVER")));
        result.set("IsClient", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.isClientSide())));
        result.set("IsServer", levelFunction(level,
            (world, args) -> LuaValue.valueOf(!world.isClientSide())));
        result.set("IsChunkLoaded", levelFunction(level, (world, args) -> {
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            return LuaValue.valueOf(world.hasChunkAt(position));
        }));
        result.set("IsBlockLoaded", result.get("IsChunkLoaded"));
        result.set("GetTime", levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) world.getOverworldClockTime())));
        result.set("GetDayTime", levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) (world.getOverworldClockTime() % 24000L))));
        result.set("GetGameTime", levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) world.getGameTime())));
        result.set("GetMinBuildHeight", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getMinY())));
        result.set("GetMaxBuildHeight", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getMaxY())));
        return result;
}

    private void registerWorldClassMethods() {
        var registration = context.mlua().registration();
        MLuaClassMethods.registerInstanceMethod(registration, "World", "GetId",
            "Returns the world's dimension identifier.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "GetDimension",
            "Returns the world's dimension identifier.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "GetSide",
            "Returns whether the world is on the client or server.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "IsClient",
            "Returns whether this world runs on the client.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "IsServer",
            "Returns whether this world runs on the server.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "IsChunkLoaded",
            "Returns whether the chunk at the given coordinates is loaded.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "IsBlockLoaded",
            "Returns whether the block position is in a loaded chunk.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "GetTime",
            "Returns the world's current clock time.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "GetDayTime",
            "Returns the current time within the day.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "GetGameTime",
            "Returns the world's total game time.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "GetMinBuildHeight",
            "Returns the minimum build height.");
        MLuaClassMethods.registerInstanceMethod(registration, "World", "GetMaxBuildHeight",
            "Returns the maximum build height.");
    }

    private static VarArgFunction levelFunction(Level level, BiFunction<Level, Varargs, LuaValue> action) {
        return function(args -> action.apply(level, args));
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
                    throw new LuaError("[MLua] " + error.getMessage());
                }
            }
        };
    }

}
