package dev.mlua.modules.world.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.LuaBackedService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaWorldService implements MLuaService, LuaBackedService {
    private static final Map<String, GameRules.Key<?>> GAME_RULE_KEYS = new HashMap<>();
    static {
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                GAME_RULE_KEYS.put(key.getId(), key);
            }
        });
    }

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
            (world, args) -> LuaValue.valueOf((double) world.getDefaultClockTime())));
        bindWorldMethod(result, implementations, "GetDayTime", levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) (world.getDefaultClockTime() % 24000L))));
        bindWorldMethod(result, implementations, "GetGameTime", levelFunction(level,
            (world, args) -> LuaValue.valueOf((double) world.getGameTime())));
        bindWorldMethod(result, implementations, "GetMinBuildHeight", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getMinY())));
        bindWorldMethod(result, implementations, "GetMaxBuildHeight", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getMaxY())));
        bindWorldMethod(result, implementations, "GetSpawnPosition", levelFunction(level,
            (world, args) -> {
                if (!(world instanceof ServerLevel serverLevel)) return LuaValue.NIL;
                BlockPos spawn = serverLevel.getWorldBorderAdjustedRespawnData(serverLevel.getRespawnData()).pos();
                return MLuaPlayerWrapper.positionTable(spawn.getX(), spawn.getY(), spawn.getZ());
            }));
        bindWorldMethod(result, implementations, "IsDay", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getDefaultClockTime() % 24000L < 13000L)));
        bindWorldMethod(result, implementations, "IsNight", levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.getDefaultClockTime() % 24000L >= 13000L)));
        bindWorldMethod(result, implementations, "GetWeather", levelFunction(level, (world, args) -> {
            if (world.isThundering()) return LuaValue.valueOf("thunder");
            return LuaValue.valueOf(world.isRaining() ? "rain" : "clear");
        }));
        bindWorldMethod(result, implementations, "SetWeather", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "SetWeather");
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
        bindWorldMethod(result, implementations, "GetBiome", levelFunction(level, (world, args) -> {
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            var key = world.getBiome(position).unwrapKey();
            return key.isEmpty() ? LuaValue.NIL : LuaValue.valueOf(key.get().identifier().toString());
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
        bindWorldMethod(result, implementations, "Raycast", levelFunction(level, (world, args) -> {
            Vec3 origin = new Vec3(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            Vec3 direction = new Vec3(args.checkdouble(5), args.checkdouble(6), args.checkdouble(7)).normalize();
            double distance = args.optdouble(8, 100.0);
            boolean includeFluids = args.optboolean(9, false);
            BlockHitResult hit = world.clip(new ClipContext(origin, origin.add(direction.scale(distance)),
                ClipContext.Block.COLLIDER,
                includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, null));
            if (hit.getType() == HitResult.Type.MISS) return LuaValue.NIL;
            LuaTable resultTable = new LuaTable();
            resultTable.set("Type", LuaValue.valueOf("block"));
            resultTable.set("Position", MLuaPlayerWrapper.positionTable(
                hit.getLocation().x, hit.getLocation().y, hit.getLocation().z));
            resultTable.set("Block", LuaValue.valueOf(
                BuiltInRegistries.BLOCK.getKey(world.getBlockState(hit.getBlockPos()).getBlock()).toString()));
            resultTable.set("Face", LuaValue.valueOf(hit.getDirection().getName()));
            return resultTable;
        }));
        bindWorldMethod(result, implementations, "SetTime", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "SetTime");
            serverClock(serverLevel).setTotalTicks(worldClock(serverLevel), args.checklong(2));
            return LuaValue.NONE;
        }));
        bindWorldMethod(result, implementations, "AddTime", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "AddTime");
            serverClock(serverLevel).addTicks(worldClock(serverLevel), args.checkint(2));
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
        bindWorldMethod(result, implementations, "SetBlock", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "SetBlock");
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            String blockId = args.checkjstring(5);
            if (!blockId.contains(":")) blockId = context.mlua().ownerModId() + ":" + blockId;
            Identifier identifier = Identifier.tryParse(blockId);
            if (identifier == null) throw new LuaError("[MLua] Invalid block id: " + blockId);
            Block block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
            if (block == null) {
                String[] parts = blockId.split(":", 2);
                RegistryHandle<Block> handle = MLuaLoaderRegistry.get(RegistryKind.BLOCK, parts[0], parts[1]);
                block = handle == null ? null : handle.get();
            }
            if (block == null) throw new LuaError("[MLua] Unknown block: " + blockId);
            return LuaValue.valueOf(serverLevel.setBlockAndUpdate(position, block.defaultBlockState()));
        }));
        bindWorldMethod(result, implementations, "BreakBlock", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "BreakBlock");
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            return LuaValue.valueOf(serverLevel.destroyBlock(position, args.optboolean(5, true)));
        }));
        bindWorldAlias(result, implementations, "DestroyBlock", "BreakBlock");
        bindWorldMethod(result, implementations, "PlaySound", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "PlaySound");
            Identifier id = Identifier.tryParse(args.checkjstring(5));
            SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
            if (sound == null) throw new LuaError("[MLua] Unknown sound: " + args.checkjstring(5));
            serverLevel.playSound((Player) null, args.checkdouble(2), args.checkdouble(3), args.checkdouble(4),
                sound, SoundSource.PLAYERS, (float) args.optdouble(6, 1.0), (float) args.optdouble(7, 1.0));
            return LuaValue.NONE;
        }));
        bindWorldMethod(result, implementations, "DropItem", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "DropItem");
            String itemId = args.checkjstring(5);
            Identifier id = Identifier.tryParse(itemId.contains(":") ? itemId : "minecraft:" + itemId);
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) throw new LuaError("[MLua] Unknown item: " + itemId);
            ItemEntity dropped = new ItemEntity(serverLevel, args.checkdouble(2), args.checkdouble(3), args.checkdouble(4),
                new ItemStack(item, Math.max(1, args.optint(6, 1))));
            serverLevel.addFreshEntity(dropped);
            return MLuaEntityWrapper.entityTable(dropped, context.mlua().registration());
        }));
        bindWorldMethod(result, implementations, "Explode", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "Explode");
            serverLevel.explode(null, args.checkdouble(2), args.checkdouble(3), args.checkdouble(4),
                (float) args.optdouble(5, 4.0), args.optboolean(6, false), Level.ExplosionInteraction.BLOCK);
            return LuaValue.NONE;
        }));
        bindWorldMethod(result, implementations, "SpawnLightning", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "SpawnLightning");
            Entity lightning = EntityType.LIGHTNING_BOLT.create(serverLevel, EntitySpawnReason.COMMAND);
            if (lightning == null) throw new LuaError("[MLua] Failed to create lightning entity.");
            lightning.setPos(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            serverLevel.addFreshEntity(lightning);
            return MLuaEntityWrapper.entityTable(lightning, context.mlua().registration());
        }));
        bindWorldMethod(result, implementations, "SpawnEntity", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "SpawnEntity");
            String entityId = args.checkjstring(2);
            if (!entityId.contains(":")) entityId = context.mlua().ownerModId() + ":" + entityId;
            Identifier id = Identifier.tryParse(entityId);
            if (id == null) throw new LuaError("[MLua] Invalid entity id: " + entityId);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) {
                String[] parts = entityId.split(":", 2);
                RegistryHandle<EntityType<?>> handle = MLuaLoaderRegistry.get(RegistryKind.ENTITY, parts[0], parts[1]);
                type = handle == null ? null : handle.get();
            }
            if (type == null) throw new LuaError("[MLua] Unknown entity: " + entityId);
            Entity entity = type.create(serverLevel, EntitySpawnReason.COMMAND);
            if (entity == null) throw new LuaError("[MLua] Failed to spawn entity: " + entityId);
            entity.setPos(args.checkdouble(3), args.checkdouble(4), args.checkdouble(5));
            entity.setYRot((float) args.optdouble(6, 0.0));
            entity.setXRot((float) args.optdouble(7, 0.0));
            serverLevel.addFreshEntity(entity);
            return MLuaEntityWrapper.entityTable(entity, context.mlua().registration());
        }));
        bindWorldMethod(result, implementations, "SpawnParticle", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "SpawnParticle");
            String particleId = args.checkjstring(5);
            Identifier id = Identifier.tryParse(particleId);
            ParticleType<?> particle = id == null ? null : BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
            if (particle == null) throw new LuaError("[MLua] Unknown particle: " + particleId);
            if (!(particle instanceof ParticleOptions options)) {
                throw new LuaError("[MLua] Particle type " + particleId + " does not have default options.");
            }
            serverLevel.sendParticles(options, args.checkdouble(2), args.checkdouble(3), args.checkdouble(4),
                args.optint(6, 1), 0.5, 0.5, 0.5, 0.0);
            return LuaValue.NONE;
        }));
        bindWorldMethod(result, implementations, "GetBlockInventory", levelFunction(level, (world, args) -> {
            BlockPos position = BlockPos.containing(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
            BlockEntity blockEntity = world.getBlockEntity(position);
            if (!(blockEntity instanceof Container container)) return LuaValue.NIL;
            return MLuaContainer.containerTable(MCContainerImpl.fromReal(container), true,
                context.mlua().registration());
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
        bindWorldMethod(result, implementations, "ListGameRules", levelFunction(level,
            (world, args) -> listGameRules(world)));
        bindWorldMethod(result, implementations, "GetGameRule", levelFunction(level,
            (world, args) -> getGameRule(world, args.checkjstring(2))));
        bindWorldMethod(result, implementations, "SetGameRule", levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = requireServerWorld(world, "SetGameRule");
            setGameRule(serverLevel, args.checkjstring(2), args.arg(3));
            return LuaValue.NONE;
        }));
        bindWorldMethod(result, implementations, "GetPlayers", levelFunction(level,
            (world, args) -> players(world, null)));
        bindWorldMethod(result, implementations, "FindPlayersInRadius", levelFunction(level, (world, args) -> {
            double x = args.checkdouble(2);
            double y = args.checkdouble(3);
            double z = args.checkdouble(4);
            double radius = args.checkdouble(5);
            return players(world, new AABB(x - radius, y - radius, z - radius,
                x + radius, y + radius, z + radius));
        }));
        bindWorldAlias(result, implementations, "GetPlayersNearby", "FindPlayersInRadius");
        bindWorldMethod(result, implementations, "FindNearestPlayer", levelFunction(level, (world, args) -> {
            if (!(world instanceof ServerLevel serverLevel)) return LuaValue.NIL;
            double x = args.checkdouble(2);
            double y = args.checkdouble(3);
            double z = args.checkdouble(4);
            double maxDistance = args.optdouble(5, Double.MAX_VALUE);
            ServerPlayer nearest = null;
            double nearestDistance = maxDistance * maxDistance;
            for (ServerPlayer player : serverLevel.players()) {
                double distance = player.distanceToSqr(x, y, z);
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearest = player;
                }
            }
            return nearest == null ? LuaValue.NIL
                : MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(nearest), context.mlua().registration());
        }));
        bindWorldAlias(result, implementations, "GetNearestPlayer", "FindNearestPlayer");
        bindWorldMethod(result, implementations, "GetEntities", levelFunction(level,
            (world, args) -> entities(world, null, LuaValue.NIL)));
        bindWorldMethod(result, implementations, "FindEntitiesInRadius", levelFunction(level, (world, args) -> {
            double x = args.checkdouble(2);
            double y = args.checkdouble(3);
            double z = args.checkdouble(4);
            double radius = args.checkdouble(5);
            return entities(world, new AABB(x - radius, y - radius, z - radius,
                x + radius, y + radius, z + radius), args.arg(6));
        }));
        bindWorldAlias(result, implementations, "GetEntitiesInRadius", "FindEntitiesInRadius");
        bindWorldAlias(result, implementations, "GetEntitiesNearby", "FindEntitiesInRadius");
        bindWorldMethod(result, implementations, "FindEntitiesInBox", levelFunction(level, (world, args) -> {
            double x1 = args.checkdouble(2);
            double y1 = args.checkdouble(3);
            double z1 = args.checkdouble(4);
            double x2 = args.checkdouble(5);
            double y2 = args.checkdouble(6);
            double z2 = args.checkdouble(7);
            return entities(world, new AABB(x1, y1, z1, x2, y2, z2), args.arg(8));
        }));
        bindWorldMethod(result, implementations, "FindNearestEntity", levelFunction(level, (world, args) -> {
            double x = args.checkdouble(2);
            double y = args.checkdouble(3);
            double z = args.checkdouble(4);
            double maxDistance = args.optdouble(5, Double.MAX_VALUE);
            LuaValue filter = args.arg(6);
            Entity nearest = null;
            double nearestDistance = maxDistance * maxDistance;
            AABB bounds = new AABB(x - maxDistance, y - maxDistance, z - maxDistance,
                x + maxDistance, y + maxDistance, z + maxDistance);
            for (Entity entity : entitiesIn(world, bounds)) {
                if (!matchesEntityFilter(entity, filter)) continue;
                double distance = entity.distanceToSqr(x, y, z);
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearest = entity;
                }
            }
            return nearest == null ? LuaValue.NIL
                : MLuaEntityWrapper.entityTable(nearest, context.mlua().registration());
        }));
        worldInstances.put(level, new WeakReference<>(result));
        return result;
    }

    private LuaTable players(Level level, AABB bounds) {
        LuaTable values = new LuaTable();
        if (!(level instanceof ServerLevel serverLevel)) return values;
        int index = 1;
        for (ServerPlayer player : serverLevel.players()) {
            Vec3 position = player.position();
            if (bounds == null || bounds.contains(position)) {
                values.set(index++, MLuaPlayerWrapper.playerTable(
                    MCServerPlayerImpl.fromReal(player), context.mlua().registration()));
            }
        }
        return values;
    }

    private LuaTable entities(Level level, AABB bounds, LuaValue filter) {
        LuaTable values = new LuaTable();
        int index = 1;
        for (Entity entity : entitiesIn(level, bounds)) {
            if (matchesEntityFilter(entity, filter)) {
                values.set(index++, MLuaEntityWrapper.entityTable(entity, context.mlua().registration()));
            }
        }
        return values;
    }

    private static Iterable<Entity> entitiesIn(Level level, AABB bounds) {
        if (bounds != null) return level.getEntities((Entity) null, bounds);
        if (level instanceof ServerLevel serverLevel) return serverLevel.getAllEntities();
        if (level instanceof ClientLevel clientLevel) return clientLevel.entitiesForRendering();
        return List.of();
    }

    private static boolean matchesEntityFilter(Entity entity, LuaValue filter) {
        if (filter == null || filter.isnil()) return true;
        String entityId = entityId(entity);
        if (filter.isstring()) return entityId.equals(filter.tojstring());
        if (!filter.istable()) return true;
        LuaTable options = filter.checktable();
        LuaValue type = options.get("type");
        if (!type.isnil() && !entityId.equals(type.tojstring())) return false;
        LuaValue playersOnly = options.get("players_only");
        if (playersOnly.toboolean() && !(entity instanceof net.minecraft.world.entity.player.Player)) return false;
        LuaValue excludePlayers = options.get("exclude_players");
        if (excludePlayers.toboolean() && entity instanceof net.minecraft.world.entity.player.Player) return false;
        LuaValue alive = options.get("alive");
        if (!alive.isnil() && alive.toboolean() != entity.isAlive()) return false;
        LuaValue name = options.get("name");
        return name.isnil() || entity.getName().getString().equals(name.tojstring());
    }

    private static String entityId(Entity entity) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id == null ? "unknown" : id.toString();
    }

    private static LuaTable listGameRules(Level level) {
        LuaTable table = new LuaTable();
        int index = 1;
        List<String> names = new ArrayList<>(GAME_RULE_KEYS.keySet());
        names.sort(String::compareTo);
        for (String name : names) {
            LuaValue value = getGameRule(level, name);
            LuaTable entry = new LuaTable();
            entry.set("Name", LuaValue.valueOf(name));
            entry.set("Value", value);
            table.set(index++, entry);
            table.set(name, value);
        }
        return table;
    }

    private static LuaValue getGameRule(Level level, String name) {
        GameRules.Key<?> key = GAME_RULE_KEYS.get(name);
        if (key == null) throw new LuaError("[MLua] Unknown gamerule: " + name);
        return gameRuleValue(level.getGameRules(), key);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setGameRule(ServerLevel level, String name, LuaValue value) {
        GameRules.Key<?> key = GAME_RULE_KEYS.get(name);
        if (key == null) throw new LuaError("[MLua] Unknown gamerule: " + name);
        GameRules.Value rule = level.getGameRules().getRule((GameRules.Key) key);
        if (rule instanceof GameRules.BooleanValue booleanValue) {
            booleanValue.set(value.checkboolean(), context.mlua().server());
            return;
        }
        if (rule instanceof GameRules.IntegerValue integerValue) {
            integerValue.set(value.checkint(), context.mlua().server());
            return;
        }
        throw new LuaError("[MLua] Unsupported gamerule type: " + name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LuaValue gameRuleValue(GameRules rules, GameRules.Key<?> key) {
        GameRules.Value rule = rules.getRule((GameRules.Key) key);
        if (rule instanceof GameRules.BooleanValue booleanValue) return LuaValue.valueOf(booleanValue.get());
        if (rule instanceof GameRules.IntegerValue integerValue) return LuaValue.valueOf(integerValue.get());
        return LuaValue.valueOf(rule.toString());
    }

    private static ServerLevel requireServerWorld(Level level, String method) {
        if (level instanceof ServerLevel serverLevel) return serverLevel;
        throw new LuaError("[MLua] " + method + " is only available on a server world.");
    }

    private static ServerClockManager serverClock(ServerLevel level) {
        return (ServerClockManager) level.clockManager();
    }

    private static Holder<WorldClock> worldClock(ServerLevel level) {
        return level.dimensionType().defaultClock()
            .orElseThrow(() -> new LuaError("[MLua] This dimension does not define a world clock."));
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
