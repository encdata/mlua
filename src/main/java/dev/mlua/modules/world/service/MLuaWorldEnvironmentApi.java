//--version 1.20.1,1.21.1
package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.world.MLuaChunkWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.modules.world.MLuaWorldGameRules;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds dimension identity, clock, spawn, weather, difficulty, and gamerule operations. */
final class MLuaWorldEnvironmentApi {
    private MLuaWorldEnvironmentApi() { }

    static void bind(LuaTable table, Supplier<Level> level, MLuaService.Context context) {
        table.rawset("GetId", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf(world.dimension().location().toString())));
        table.rawset("GetDimension", table.rawget("GetId"));
        table.rawset("GetSide", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf(world.isClientSide ? "CLIENT" : "SERVER")));
        table.rawset("IsClient", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf(world.isClientSide)));
        table.rawset("IsServer", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf(!world.isClientSide)));
        table.rawset("GetTime", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf((double) world.getDayTime())));
        table.rawset("GetDayTime", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf((double) (world.getDayTime() % 24000L))));
        table.rawset("GetGameTime", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf((double) world.getGameTime())));
        table.rawset("GetMinBuildHeight", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf(minBuildHeight(world))));
        table.rawset("GetMaxBuildHeight", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf(maxBuildHeight(world))));
        table.rawset("GetSpawnPosition", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            BlockPos spawn = world.getSharedSpawnPos();
            return MLuaPlayerWrapper.positionTable(spawn.getX(), spawn.getY(), spawn.getZ());
        }));
        table.rawset("SetSpawnPosition", MLuaWorldApiSupport.serverLevelFn(context, level, (world, args) -> {
            double[] position = MLuaPositionArguments.vector3(args, 2);
            world.setDefaultSpawnPos(BlockPos.containing(position[0], position[1], position[2]),
                MLuaPositionArguments.yaw(args, 2, 3));
            return LuaValue.TRUE;
        }));
        table.rawset("IsDay", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf(world.isDay())));
        table.rawset("IsNight", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf(world.isNight())));
        table.rawset("SetTime", MLuaWorldApiSupport.serverLevelFn(context, level, (world, args) -> {
            world.setDayTime(MLuaLuaArguments.wholeLong(args, 2, "World:SetTime", "ticks"));
            return LuaValue.NONE;
        }));
        table.rawset("AddTime", MLuaWorldApiSupport.serverLevelFn(context, level, (world, args) -> {
            long ticks = MLuaLuaArguments.wholeLong(args, 2, "World:AddTime", "ticks");
            try {
                world.setDayTime(Math.addExact(world.getDayTime(), ticks));
            } catch (ArithmeticException overflow) {
                throw new LuaError("[MLua] World:AddTime would overflow the world's day time.");
            }
            return LuaValue.NONE;
        }));
        table.rawset("GetWeather", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            if (world.isThundering()) return LuaValue.valueOf("thunder");
            return world.isRaining() ? LuaValue.valueOf("rain") : LuaValue.valueOf("clear");
        }));
        table.rawset("IsRaining", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf(world.isRaining())));
        table.rawset("IsThundering", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> LuaValue.valueOf(world.isThundering())));
        table.rawset("IsRainingAt", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            double[] coordinates = MLuaPositionArguments.vector3(args, 2);
            BlockPos position = BlockPos.containing(coordinates[0], coordinates[1], coordinates[2]);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return LuaValue.valueOf(world.isRainingAt(position));
        }));
        table.rawset("SetWeather", MLuaWorldApiSupport.serverLevelFn(context, level, (world, args) -> {
            String weather = MLuaMinecraftEnums.weatherToJava(args.arg(2));
            int duration = MLuaWorldApiSupport.checkedIntArgument(args, 3, 6000, 0,
                Integer.MAX_VALUE, "weather duration");
            switch (weather.toLowerCase(java.util.Locale.ROOT)) {
                case "clear" -> world.setWeatherParameters(duration, 0, false, false);
                case "rain" -> world.setWeatherParameters(0, duration, true, false);
                case "thunder" -> world.setWeatherParameters(0, duration, true, true);
                default -> throw new LuaError("[MLua] Unknown weather: " + weather);
            }
            return LuaValue.NONE;
        }));
        table.rawset("GetDifficulty", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> MLuaMinecraftEnums.difficultyOf(world.getDifficulty())));
        table.rawset("SetDifficulty", MLuaWorldApiSupport.serverLevelFn(context, level, (world, args) -> {
            MinecraftServer server = context.mlua().server();
            if (server == null) throw new LuaError("[MLua] SetDifficulty requires an active server.");
            Difficulty difficulty = MLuaMinecraftEnums.resolveDifficulty(args.arg(2));
            server.setDifficulty(difficulty, true);
            return LuaValue.NONE;
        }));
        bindGameRules(table, level, context);
    }

    private static void bindGameRules(LuaTable table, java.util.function.Supplier<Level> level,
                                     MLuaService.Context context) {
        table.rawset("ListGameRules", MLuaWorldApiSupport.levelFn(level, (world, args) ->
            MLuaWorldGameRules.list(world, context.mlua().registration(), context.mlua()::server)));
        table.rawset("GetGameRule", MLuaWorldApiSupport.levelFn(level,
            (world, args) -> MLuaWorldGameRules.get(world, args.checkjstring(2))));
        table.rawset("GetGameRuleEntry", MLuaWorldApiSupport.levelFn(level, (world, args) ->
            MLuaWorldGameRules.entry(world, args.checkjstring(2),
                context.mlua().registration(), context.mlua()::server)));
        table.rawset("SetGameRule", MLuaWorldApiSupport.serverLevelFn(context, level, (world, args) -> {
            MLuaWorldGameRules.set(world, args.checkjstring(2), args.arg(3), context.mlua().server());
            return LuaValue.NONE;
        }));
    }

    static int minBuildHeight(Level level) {
        //--version 26.1
        return level.getMinY();
        //--endversion
        //--version 1.20.1,1.21.1
        return level.getMinBuildHeight();
        //--endversion
    }

    static int maxBuildHeight(Level level) {
        //--version 26.1
        return level.getMinY() + level.getHeight();
        //--endversion
        //--version 1.20.1,1.21.1
        return level.getMaxBuildHeight();
        //--endversion
    }
}
//--endversion
