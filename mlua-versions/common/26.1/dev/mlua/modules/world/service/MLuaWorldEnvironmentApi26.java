package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.LevelData;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds dimension identity, clock, spawn, and weather operations for 26.x Worlds. */
final class MLuaWorldEnvironmentApi26 {
    private MLuaWorldEnvironmentApi26() { }

    static void bind(MLuaWorldService owner, MLuaService.Context context, LuaTable worldTable,
                     Map<String, LuaValue> implementations, Level level) {
        owner.bindWorldMethod(worldTable, implementations, "GetId", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.dimension().identifier().toString())));
        owner.bindWorldAlias(worldTable, implementations, "GetDimension", "GetId");
        owner.bindWorldMethod(worldTable, implementations, "GetSide", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.isClientSide() ? "CLIENT" : "SERVER")));
        owner.bindWorldMethod(worldTable, implementations, "IsClient", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.isClientSide())));
        owner.bindWorldMethod(worldTable, implementations, "IsServer", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf(!world.isClientSide())));
        owner.bindWorldMethod(worldTable, implementations, "GetTime", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) world.getDefaultClockTime())));
        owner.bindWorldMethod(worldTable, implementations, "GetDayTime", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) (world.getDefaultClockTime() % 24000L))));
        owner.bindWorldMethod(worldTable, implementations, "GetGameTime", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) world.getGameTime())));
        owner.bindWorldMethod(worldTable, implementations, "GetMinBuildHeight", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getMinY())));
        owner.bindWorldMethod(worldTable, implementations, "GetMaxBuildHeight", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getMaxY())));
        owner.bindWorldMethod(worldTable, implementations, "SetTime", MLuaWorldService.levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "SetTime");
            MLuaWorldService.serverClock(serverLevel).setTotalTicks(
                MLuaWorldService.worldClock(serverLevel), args.checklong(2));
            return LuaValue.NONE;
        }));
        owner.bindWorldMethod(worldTable, implementations, "AddTime", MLuaWorldService.levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "AddTime");
            MLuaWorldService.serverClock(serverLevel).addTicks(
                MLuaWorldService.worldClock(serverLevel), args.checkint(2));
            return LuaValue.NONE;
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetDifficulty", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaMinecraftEnums.difficultyOf(world.getDifficulty())));
        owner.bindWorldMethod(worldTable, implementations, "SetDifficulty", MLuaWorldService.levelFunction(level, (world, args) -> {
            MinecraftServer server = context.mlua().server();
            if (server == null) throw new LuaError("[MLua] SetDifficulty requires an active server.");
            Difficulty difficulty = MLuaMinecraftEnums.resolveDifficulty(args.arg(2));
            server.setDifficulty(difficulty, true);
            return LuaValue.NONE;
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetSpawnPosition", MLuaWorldService.levelFunction(level,
            (world, args) -> {
                BlockPos spawn = world.getWorldBorderAdjustedRespawnData(world.getRespawnData()).pos();
                return MLuaPlayerWrapper.positionTable(spawn.getX(), spawn.getY(), spawn.getZ());
            }));
        owner.bindWorldMethod(worldTable, implementations, "SetSpawnPosition", MLuaWorldService.levelFunction(level,
            (world, args) -> {
                ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "SetSpawnPosition");
                double[] position = MLuaPositionArguments.vector3(args, 2);
                BlockPos spawn = BlockPos.containing(position[0], position[1], position[2]);
                int yawIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
                float yaw = MLuaPositionArguments.yaw(args, 2, yawIndex);
                serverLevel.setRespawnData(LevelData.RespawnData.of(serverLevel.dimension(), spawn, yaw, 0.0f));
                return LuaValue.TRUE;
            }));
        owner.bindWorldMethod(worldTable, implementations, "IsDay", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getDefaultClockTime() % 24000L < 13000L)));
        owner.bindWorldMethod(worldTable, implementations, "IsNight", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getDefaultClockTime() % 24000L >= 13000L)));
        owner.bindWorldMethod(worldTable, implementations, "GetWeather", MLuaWorldService.levelFunction(level,
            (world, args) -> {
                if (world.isThundering()) return LuaValue.valueOf("thunder");
                return LuaValue.valueOf(world.isRaining() ? "rain" : "clear");
            }));
        owner.bindWorldMethod(worldTable, implementations, "SetWeather", MLuaWorldService.levelFunction(level,
            (world, args) -> {
                ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "SetWeather");
                String weather = MLuaMinecraftEnums.weatherToJava(args.arg(2)).toLowerCase();
                int duration = args.optint(3, 6000);
                WeatherData data = serverLevel.getWeatherData();
                switch (weather) {
                    case "clear" -> {
                        data.setClearWeatherTime(duration);
                        data.setRainTime(0);
                        data.setThunderTime(0);
                        data.setRaining(false);
                        data.setThundering(false);
                    }
                    case "rain" -> {
                        data.setClearWeatherTime(0);
                        data.setRainTime(duration);
                        data.setThunderTime(0);
                        data.setRaining(true);
                        data.setThundering(false);
                    }
                    case "thunder" -> {
                        data.setClearWeatherTime(0);
                        data.setRainTime(duration);
                        data.setThunderTime(duration);
                        data.setRaining(true);
                        data.setThundering(true);
                    }
                    default -> throw new LuaError("[MLua] Unknown weather: " + weather);
                }
                data.setDirty();
                return LuaValue.NONE;
            }));
    }
}
