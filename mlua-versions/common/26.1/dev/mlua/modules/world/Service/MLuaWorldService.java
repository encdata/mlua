package dev.mlua.modules.world.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.LuaBackedService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FluidState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaWorldService implements MLuaService, LuaBackedService {
    private final MLuaService.Context context;
    private final Map<Level, WeakReference<LuaTable>> worldInstances = new WeakHashMap<>();

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

    private synchronized LuaTable world(Level level) {
        WeakReference<LuaTable> reference = worldInstances.get(level);
        LuaTable cached = reference == null ? null : reference.get();
        if (cached != null) return cached;

        LuaTable fields = new LuaTable();
        fields.set("__world", LuaValue.userdataOf(level));
        fields.set("__level", LuaValue.userdataOf(level));
        fields.set("__side", LuaValue.valueOf(level.isClientSide() ? "CLIENT" : "SERVER"));
        LuaTable result = context.mlua().registration().createClassInstance("World", fields);
        java.util.Map<String, LuaValue> implementations = new java.util.HashMap<>();
        bindWorldMethod(result, implementations, "GetId", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.dimension().identifier().toString())));
        bindWorldAlias(result, implementations, "GetDimension", "GetId");
        bindWorldMethod(result, implementations, "GetSide", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.isClientSide() ? "CLIENT" : "SERVER")));
        bindWorldMethod(result, implementations, "IsClient", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.isClientSide())));
        bindWorldMethod(result, implementations, "IsServer", levelFunction(level,
            (world, args) -> LuaValue.valueOf(!world.isClientSide())));
        bindWorldMethod(result, implementations, "IsChunkLoaded", levelFunction(level, (world, args) -> {
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            return LuaValue.valueOf(world.hasChunkAt(position));
        }));
        bindWorldAlias(result, implementations, "IsBlockLoaded", "IsChunkLoaded");
        bindWorldMethod(result, implementations, "GetTime", levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) world.getOverworldClockTime())));
        bindWorldMethod(result, implementations, "GetDayTime", levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) (world.getOverworldClockTime() % 24000L))));
        bindWorldMethod(result, implementations, "GetGameTime", levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) world.getGameTime())));
        bindWorldMethod(result, implementations, "GetMinBuildHeight", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getMinY())));
        bindWorldMethod(result, implementations, "GetMaxBuildHeight", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getMaxY())));
        bindWorldMethod(result, implementations, "GetSpawnPosition", levelFunction(level,
            (world, args) -> {
                if (!(world instanceof ServerLevel serverLevel)) return LuaValue.NIL;
                BlockPos spawn = serverLevel.getSharedSpawnPos();
                return MLuaPlayerWrapper.positionTable(spawn.getX(), spawn.getY(), spawn.getZ());
            }));
        bindWorldMethod(result, implementations, "IsDay", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.isDay())));
        bindWorldMethod(result, implementations, "IsNight", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.isNight())));
        bindWorldMethod(result, implementations, "GetWeather", levelFunction(level, (world, args) -> {
            if (world.isThundering()) return LuaValue.valueOf("thunder");
            return LuaValue.valueOf(world.isRaining() ? "rain" : "clear");
        }));
        bindWorldMethod(result, implementations, "GetBlock", levelFunction(level, (world, args) -> {
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            return MLuaBlockWrapper.blockTable(world, position, context.mlua().registration());
        }));
        bindWorldAlias(result, implementations, "GetBlockState", "GetBlock");
        bindWorldMethod(result, implementations, "GetFluid", levelFunction(level, (world, args) -> {
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            FluidState state = world.getFluidState(position);
            if (state.isEmpty()) return LuaValue.NIL;
            var id = BuiltInRegistries.FLUID.getKey(state.getType());
            return id == null ? LuaValue.NIL : LuaValue.valueOf(id.toString());
        }));
        bindWorldMethod(result, implementations, "GetLightLevel", levelFunction(level, (world, args) -> {
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            return LuaValue.valueOf(world.getMaxLocalRawBrightness(position));
        }));
        bindWorldMethod(result, implementations, "GetSkyLight", levelFunction(level, (world, args) -> {
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            return LuaValue.valueOf(world.getBrightness(LightLayer.SKY, position));
        }));
        bindWorldMethod(result, implementations, "GetBlockLight", levelFunction(level, (world, args) -> {
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            return LuaValue.valueOf(world.getBrightness(LightLayer.BLOCK, position));
        }));
        bindWorldMethod(result, implementations, "SetTime", levelFunction(level, (world, args) -> {
            if (!(world instanceof ServerLevel serverLevel)) {
                throw new LuaError("[MLua] SetTime is only available on a server world.");
            }
            serverLevel.setDayTime(args.checklong(2));
            return LuaValue.NONE;
        }));
        bindWorldMethod(result, implementations, "AddTime", levelFunction(level, (world, args) -> {
            if (!(world instanceof ServerLevel serverLevel)) {
                throw new LuaError("[MLua] AddTime is only available on a server world.");
            }
            serverLevel.setDayTime(serverLevel.getDayTime() + args.checklong(2));
            return LuaValue.NONE;
        }));
        bindWorldMethod(result, implementations, "GetDifficulty", levelFunction(level,
            (world, args) -> MLuaMinecraftEnums.difficultyOf(world.getDifficulty())));
        bindWorldMethod(result, implementations, "SetDifficulty", levelFunction(level, (world, args) -> {
            MinecraftServer server = context.mlua().server();
            if (server == null) throw new LuaError("[MLua] SetDifficulty requires an active server.");
            Difficulty difficulty = MLuaMinecraftEnums.resolveDifficulty(args.arg(2));
            server.setDifficulty(difficulty, true);
            return LuaValue.NONE;
        }));
        bindWorldMethod(result, implementations, "GetPersistentData", levelFunction(level,
            (world, args) -> MLuaDataManager.worldPersistentData(world)));
        bindWorldMethod(result, implementations, "GetSyncedData", levelFunction(level,
            (world, args) -> MLuaDataManager.worldSyncedData(world)));
        bindWorldMethod(result, implementations, "GetData", levelFunction(level, (world, args) -> {
            LuaTable data = MLuaDataManager.worldPersistentData(world);
            return data.get("Get").call(data, LuaValue.valueOf(args.checkjstring(2)));
        }));
        bindWorldMethod(result, implementations, "SetData", levelFunction(level, (world, args) -> {
            requireServerWorld(world, "SetData");
            LuaTable data = MLuaDataManager.worldPersistentData(world);
            data.get("Set").call(data, LuaValue.valueOf(args.checkjstring(2)), args.arg(3));
            return LuaValue.NONE;
        }));
        bindWorldMethod(result, implementations, "RemoveData", levelFunction(level, (world, args) -> {
            requireServerWorld(world, "RemoveData");
            LuaTable data = MLuaDataManager.worldPersistentData(world);
            data.get("Remove").call(data, LuaValue.valueOf(args.checkjstring(2)));
            return LuaValue.NONE;
        }));
        worldInstances.put(level, new WeakReference<>(result));
        return result;
    }

    private static ServerLevel requireServerWorld(Level level, String method) {
        if (level instanceof ServerLevel serverLevel) return serverLevel;
        throw new LuaError("[MLua] " + method + " is only available on a server world.");
    }

    private void bindWorldMethod(LuaTable instance, java.util.Map<String, LuaValue> implementations,
                                 String name, LuaValue implementation) {
        implementations.put(name, implementation);
        MLuaClassMethods.bindInstanceMethod(context.mlua().registration(), "World", instance,
            name, implementation);
    }

    private void bindWorldAlias(LuaTable instance, java.util.Map<String, LuaValue> implementations,
                                String alias, String source) {
        LuaValue implementation = implementations.get(source);
        if (implementation == null) throw new IllegalStateException("World method is not bound: " + source);
        bindWorldMethod(instance, implementations, alias, implementation);
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
