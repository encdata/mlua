package dev.mlua.modules.world.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.modules.world.WorldModule;
import dev.mlua.modules.world.MLuaChunkWrapper;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateProperties;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaWorldService extends MLuaServiceBase {
   private static final Map<String, GameRules.Key<?>> GAME_RULE_KEYS = new HashMap<>();
   private final Map<Level, WeakReference<LuaTable>> worldInstances = new WeakHashMap<>();
   static {
      GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
         public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
            GAME_RULE_KEYS.put(key.getId(), key);
         }
      });
   }

   public MLuaWorldService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "World";
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__side", LuaValue.valueOf(this.context.mlua().side().name()));
      table.set("Current", varArg((args) -> this.worldValue(this.currentLevel())));
      table.set("Get", varArg((args) -> this.worldValue(this.resolveLevel(args.checkjstring(2)))));
      table.set("GetAll", varArg((args) -> this.allWorlds()));
      table.set("GetSide", varArg((args) -> LuaValue.valueOf(this.context.mlua().side().name())));
      table.set("GetWeather", this.currentWorldMethod("GetWeather"));
      table.set("SetWeather", this.currentWorldMethod("SetWeather"));
      WorldModule.bindWorldServiceMethods(table, this::currentWorldMethod);
      return table;
   }

   private LuaValue currentWorldMethod(String methodName) {
      return new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            Level level = MLuaWorldService.this.currentLevel();
            if (level == null) return LuaValue.NIL;
            LuaTable instance = MLuaWorldService.this.worldTable(() -> level);
            LuaValue method = instance.get(methodName);
            if (!method.isfunction()) {
               throw new LuaError("[MLua] Current World instance does not implement " + methodName + ".");
            }
            LuaValue[] forwarded = new LuaValue[Math.max(1, args.narg())];
            forwarded[0] = instance;
            for (int index = 2; index <= args.narg(); index++) forwarded[index - 1] = args.arg(index);
            return method.invoke(LuaValue.varargsOf(forwarded));
         }
      };
   }

   private LuaValue worldValue(Level level) {
      return level == null ? LuaValue.NIL : this.worldTable(() -> level);
   }

   private synchronized LuaTable worldTable(Supplier<Level> supplier) {
      Level initial = supplier.get();
      if (initial != null) {
         WeakReference<LuaTable> reference = this.worldInstances.get(initial);
         LuaTable cached = reference == null ? null : reference.get();
         if (cached != null) return cached;
      }
      LuaTable fields = new LuaTable();
      fields.set("__side", LuaValue.valueOf(initial != null && initial.isClientSide ? "CLIENT" : "SERVER"));
      if (initial != null) {
         fields.set("__world", LuaValue.userdataOf(initial));
         fields.set("__level", LuaValue.userdataOf(initial));
      }
      LuaTable table = this.context.mlua().registration().createClassInstance("World", fields);
      Map<String, LuaValue> implementations = new HashMap<>();

      bindWorldMethod(table, implementations, "GetId", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.dimension().location().toString())));
      bindWorldAlias(table, implementations, "GetDimension", "GetId");
      bindWorldMethod(table, implementations, "GetSide", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isClientSide ? "CLIENT" : "SERVER")));
      bindWorldMethod(table, implementations, "IsClient", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isClientSide)));
      bindWorldMethod(table, implementations, "IsServer", levelFn(supplier, (level, args) -> LuaValue.valueOf(!level.isClientSide)));
      bindWorldMethod(table, implementations, "IsChunkLoaded", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         return LuaValue.valueOf(level.hasChunkAt(BlockPos.containing(pos[0], pos[1], pos[2])));
      }));
      bindWorldAlias(table, implementations, "IsBlockLoaded", "IsChunkLoaded");

      bindWorldMethod(table, implementations, "GetTime", levelFn(supplier, (level, args) -> LuaValue.valueOf((double)level.getDayTime())));
      bindWorldMethod(table, implementations, "GetDayTime", levelFn(supplier, (level, args) -> LuaValue.valueOf((double)(level.getDayTime() % 24000L))));
      bindWorldMethod(table, implementations, "GetGameTime", levelFn(supplier, (level, args) -> LuaValue.valueOf((double)level.getGameTime())));
      bindWorldMethod(table, implementations, "GetMinBuildHeight", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.getMinBuildHeight())));
      bindWorldMethod(table, implementations, "GetMaxBuildHeight", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.getMaxBuildHeight())));
      bindWorldMethod(table, implementations, "GetSpawnPosition", levelFn(supplier, (level, args) -> {
         BlockPos spawn = level.getSharedSpawnPos();
         return MLuaPlayerWrapper.positionTable(spawn.getX(), spawn.getY(), spawn.getZ());
      }));
      bindWorldMethod(table, implementations, "IsDay", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isDay())));
      bindWorldMethod(table, implementations, "IsNight", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isNight())));
      bindWorldMethod(table, implementations, "SetTime", serverLevelFn(supplier, (level, args) -> {
         level.setDayTime(args.checklong(2));
         return LuaValue.NONE;
      }));
      bindWorldMethod(table, implementations, "AddTime", serverLevelFn(supplier, (level, args) -> {
         level.setDayTime(level.getDayTime() + args.checklong(2));
         return LuaValue.NONE;
      }));

      bindWorldMethod(table, implementations, "GetWeather", levelFn(supplier, (level, args) -> {
         if (level.isThundering()) {
            return LuaValue.valueOf("thunder");
         }
         return level.isRaining() ? LuaValue.valueOf("rain") : LuaValue.valueOf("clear");
      }));
      bindWorldMethod(table, implementations, "SetWeather", serverLevelFn(supplier, (level, args) -> {
         String weather = MLuaMinecraftEnums.weatherToJava(args.arg(2));
         int duration = args.optint(3, 6000);
         switch(weather.toLowerCase()) {
         case "clear":
            level.setWeatherParameters(duration, 0, false, false);
            break;
         case "rain":
            level.setWeatherParameters(0, duration, true, false);
            break;
         case "thunder":
            level.setWeatherParameters(0, duration, true, true);
            break;
         default:
            throw new LuaError("[MLua] Unknown weather: " + weather);
         }

         return LuaValue.NONE;
      }));
      bindWorldMethod(table, implementations, "GetDifficulty", levelFn(supplier, (level, args) -> MLuaMinecraftEnums.difficultyOf(level.getDifficulty())));
      bindWorldMethod(table, implementations, "SetDifficulty", serverLevelFn(supplier, (level, args) -> {
         MinecraftServer server = this.context.mlua().server();
         if (server == null) {
            throw new LuaError("[MLua] SetDifficulty requires an active server.");
         }
         Difficulty difficulty = MLuaMinecraftEnums.resolveDifficulty(args.arg(2));
         server.setDifficulty(difficulty, true);
         return LuaValue.NONE;
      }));

      bindWorldMethod(table, implementations, "GetBlock", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         return MLuaBlockWrapper.blockTable(level, BlockPos.containing(pos[0], pos[1], pos[2]));
      }));
      bindWorldAlias(table, implementations, "GetBlockState", "GetBlock");
      bindWorldMethod(table, implementations, "GetFluid", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         FluidState state = level.getFluidState(BlockPos.containing(pos[0], pos[1], pos[2]));
         if (state == null || state.isEmpty()) {
            return LuaValue.NIL;
         }

         ResourceLocation id = BuiltInRegistries.FLUID.getKey(state.getType());
         return id == null ? LuaValue.NIL : LuaValue.valueOf(id.toString());
      }));
      bindWorldMethod(table, implementations, "GetBiome", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         Biome biome = level.getBiome(BlockPos.containing(pos[0], pos[1], pos[2])).value();
         ResourceLocation id = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
         return LuaValue.valueOf(id == null ? "unknown" : id.toString());
      }));
      bindWorldMethod(table, implementations, "GetLightLevel", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         return LuaValue.valueOf(level.getMaxLocalRawBrightness(BlockPos.containing(pos[0], pos[1], pos[2])));
      }));
      bindWorldMethod(table, implementations, "GetSkyLight", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         return LuaValue.valueOf(level.getBrightness(LightLayer.SKY, BlockPos.containing(pos[0], pos[1], pos[2])));
      }));
      bindWorldMethod(table, implementations, "GetBlockLight", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         return LuaValue.valueOf(level.getBrightness(LightLayer.BLOCK, BlockPos.containing(pos[0], pos[1], pos[2])));
      }));
      bindWorldMethod(table, implementations, "Raycast", levelFn(supplier, (level, args) -> {
         double[] origin = positionArgs(args, 2);
         double[] direction = positionArgs(args, 3);
         double distance = args.optdouble(4, 100.0);
         boolean includeFluids = args.optboolean(5, false);
         Vec3 from = new Vec3(origin[0], origin[1], origin[2]);
         Vec3 dir = new Vec3(direction[0], direction[1], direction[2]).normalize();
         BlockHitResult hitResult = level.clip(new ClipContext(from, from.add(dir.scale(distance)), ClipContext.Block.COLLIDER, includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, (Entity)null));
         if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return LuaValue.NIL;
         }

         LuaTable result = new LuaTable();
         result.set("Type", LuaValue.valueOf("block"));
         result.set("Position", MLuaPlayerWrapper.positionTable(hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z));
         result.set("Block", LuaValue.valueOf(String.valueOf(BuiltInRegistries.BLOCK.getKey(level.getBlockState(hitResult.getBlockPos()).getBlock()))));
         result.set("Face", LuaValue.valueOf(hitResult.getDirection().getName()));
         return result;
      }));

      bindWorldMethod(table, implementations, "GetPlayers", levelFn(supplier, (level, args) -> this.playersTable(level)));
      bindWorldMethod(table, implementations, "GetEntities", levelFn(supplier, (level, args) -> this.entitiesTable(level, null, LuaValue.NIL)));
      bindWorldMethod(table, implementations, "FindEntitiesInRadius", levelFn(supplier, (level, args) -> {
         double[] center = positionArgs(args, 2);
         double radius = args.checkdouble(3);
         AABB box = new AABB(center[0] - radius, center[1] - radius, center[2] - radius, center[0] + radius, center[1] + radius, center[2] + radius);
         return this.entitiesTable(level, box, args.arg(4));
      }));
      bindWorldAlias(table, implementations, "GetEntitiesInRadius", "FindEntitiesInRadius");
      bindWorldAlias(table, implementations, "GetEntitiesNearby", "FindEntitiesInRadius");
      bindWorldMethod(table, implementations, "FindEntitiesInBox", levelFn(supplier, (level, args) -> {
         double[] min = positionArgs(args, 2);
         double[] max = positionArgs(args, 3);
         AABB box = new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
         return this.entitiesTable(level, box, args.arg(4));
      }));
      bindWorldMethod(table, implementations, "FindPlayersInRadius", levelFn(supplier, (level, args) -> {
         double[] center = positionArgs(args, 2);
         double radius = args.checkdouble(3);
         AABB box = new AABB(center[0] - radius, center[1] - radius, center[2] - radius, center[0] + radius, center[1] + radius, center[2] + radius);
         return this.playersTable(level, box);
      }));
      bindWorldAlias(table, implementations, "GetPlayersNearby", "FindPlayersInRadius");
      bindWorldMethod(table, implementations, "FindNearestPlayer", levelFn(supplier, (level, args) -> {
         if (!(level instanceof ServerLevel serverLevel)) {
            return LuaValue.NIL;
         }

         double[] pos = positionArgs(args, 2);
         double maxDistance = args.optdouble(3, Double.MAX_VALUE);
         ServerPlayer nearest = null;
         double nearestDistance = maxDistance * maxDistance;
         for (ServerPlayer player : serverLevel.players()) {
            double distance = player.distanceToSqr(pos[0], pos[1], pos[2]);
            if (distance <= nearestDistance) {
               nearestDistance = distance;
               nearest = player;
            }
         }

         return nearest == null ? LuaValue.NIL : MLuaPlayerWrapper.playerTable(
            MCServerPlayerImpl.fromReal(nearest), this.context.mlua().registration());
      }));
      bindWorldAlias(table, implementations, "GetNearestPlayer", "FindNearestPlayer");
      bindWorldMethod(table, implementations, "FindNearestEntity", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         double maxDistance = args.optdouble(3, Double.MAX_VALUE);
         LuaValue filter = args.arg(4);
         AABB box = new AABB(pos[0] - maxDistance, pos[1] - maxDistance, pos[2] - maxDistance, pos[0] + maxDistance, pos[1] + maxDistance, pos[2] + maxDistance);
         Entity nearest = null;
         double nearestDistance = maxDistance * maxDistance;
         for (Entity entity : level.getEntities((Entity)null, box)) {
            if (!matchesFilter(entity, filter)) {
               continue;
            }
            double distance = entity.distanceToSqr(pos[0], pos[1], pos[2]);
            if (distance <= nearestDistance) {
               nearestDistance = distance;
               nearest = entity;
            }
         }

         return wrapEntity(nearest);
      }));

      bindWorldMethod(table, implementations, "SetBlock", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         String blockId = args.checkjstring(3);
         Block block = BuiltInRegistries.BLOCK.getOptional(MLuaResourceLocations.parse(blockId)).orElse(null);
         if (block == null) {
            throw new LuaError("[MLua] Unknown block: " + blockId);
         }
         BlockPos blockPosition = BlockPos.containing(pos[0], pos[1], pos[2]);
         boolean load = args.optboolean(5, true);
         if (!level.hasChunkAt(blockPosition)) {
            if (!load) return LuaValue.FALSE;
            level.getChunk(blockPosition.getX() >> 4, blockPosition.getZ() >> 4);
         }
         return LuaValue.valueOf(level.setBlock(blockPosition,
            MLuaBlockStateProperties.apply(block.defaultBlockState(), args.arg(4)), 3));
      }));
      bindWorldMethod(table, implementations, "BreakBlock", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         boolean dropItems = args.optboolean(3, true);
         return LuaValue.valueOf(level.destroyBlock(BlockPos.containing(pos[0], pos[1], pos[2]), dropItems));
      }));
      bindWorldAlias(table, implementations, "DestroyBlock", "BreakBlock");
      bindWorldMethod(table, implementations, "PlaySound", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         String soundName = args.checkjstring(3);
         SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(MLuaResourceLocations.parse(soundName)).orElse(null);
         if (sound == null) {
            throw new LuaError("[MLua] Unknown sound: " + soundName);
         }
         float volume = (float)args.optdouble(4, 1.0);
         float pitch = (float)args.optdouble(5, 1.0);
         level.playSound((Player)null, pos[0], pos[1], pos[2], sound, SoundSource.PLAYERS, volume, pitch);
         return LuaValue.NONE;
      }));
      bindWorldMethod(table, implementations, "SpawnParticle", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         String particleName = args.checkjstring(3);
         int count = args.optint(4, 1);
         ResourceLocation id = MLuaResourceLocations.tryParseOrNull(particleName);
         if (id == null) {
            throw new LuaError("[MLua] Invalid particle id: " + particleName);
         }
         ParticleType<?> particle = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
         if (particle == null) {
            throw new LuaError("[MLua] Unknown particle: " + particleName);
         }
         if (!(particle instanceof ParticleOptions options)) {
            throw new LuaError("[MLua] Particle type " + particleName + " does not have default options");
         }
         level.sendParticles(options, pos[0], pos[1], pos[2], count, 0.5, 0.5, 0.5, 0.0);
         return LuaValue.NONE;
      }));
      bindWorldMethod(table, implementations, "SpawnEntity", serverLevelFn(supplier, (level, args) -> {
         String entityId = args.checkjstring(2);
         double[] pos = positionArgs(args, 3);
         double yaw = args.optdouble(4, 0.0);
         double pitch = args.optdouble(5, 0.0);
         if (!entityId.contains(":")) {
            entityId = this.context.mlua().ownerModId() + ":" + entityId;
         }
         String[] parts = entityId.split(":", 2);
         RegistryHandle<EntityType<?>> handle = MLuaLoaderRegistry.get(RegistryKind.ENTITY, parts[0], parts[1]);
         if (handle == null) {
            throw new LuaError("[MLua] Unknown entity: " + entityId);
         }
         EntityType<?> entityType = handle.get();
         if (entityType == null) {
            throw new LuaError("[MLua] Entity type not yet available: " + entityId);
         }
         Entity entity = entityType.create(level);
         if (entity == null) {
            throw new LuaError("[MLua] Failed to spawn entity: " + entityId);
         }
         entity.setPos(pos[0], pos[1], pos[2]);
         entity.setYRot((float)yaw);
         entity.setXRot((float)pitch);
         entity.setYHeadRot((float)yaw);
         entity.setYBodyRot((float)yaw);
         level.addFreshEntity(entity);
         return wrapEntity(entity);
      }));
      bindWorldMethod(table, implementations, "DropItem", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         String itemId = args.checkjstring(3);
         int count = args.optint(4, 1);
         net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(MLuaResourceLocations.parse(itemId));
         if (item == null) {
            throw new LuaError("[MLua] Unknown item: " + itemId);
         }
         ItemEntity entity = new ItemEntity(level, pos[0], pos[1], pos[2], new ItemStack(item, Math.max(1, count)));
         level.addFreshEntity(entity);
         return wrapEntity(entity);
      }));
      bindWorldMethod(table, implementations, "Explode", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         float power = (float)args.optdouble(3, 4.0);
         boolean fire = args.optboolean(4, false);
         level.explode((Entity)null, pos[0], pos[1], pos[2], power, fire, Level.ExplosionInteraction.BLOCK);
         return LuaValue.NONE;
      }));
      bindWorldMethod(table, implementations, "SpawnLightning", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         Entity lightning = EntityType.LIGHTNING_BOLT.create(level);
         if (lightning == null) {
            throw new LuaError("[MLua] Failed to create lightning entity");
         }
         lightning.setPos(pos[0], pos[1], pos[2]);
         level.addFreshEntity(lightning);
         return wrapEntity(lightning);
      }));

      bindWorldMethod(table, implementations, "GetBlockInventory", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockEntity blockEntity = level.getBlockEntity(BlockPos.containing(pos[0], pos[1], pos[2]));
         if (blockEntity instanceof Container container) {
            return MLuaContainer.containerTable(MCContainerImpl.fromReal(container));
         }
         return LuaValue.NIL;
      }));

      bindWorldMethod(table, implementations, "GetPersistentData", levelFn(supplier, (level, args) -> MLuaDataManager.worldPersistentData(level)));
      bindWorldMethod(table, implementations, "GetSyncedData", levelFn(supplier, (level, args) -> MLuaDataManager.worldSyncedData(level)));
      bindWorldMethod(table, implementations, "GetData", levelFn(supplier, (level, args) -> {
         LuaTable persistent = MLuaDataManager.worldPersistentData(level);
         return persistent.get("Get").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
      }));
      bindWorldMethod(table, implementations, "SetData", serverLevelFn(supplier, (level, args) -> {
         LuaTable persistent = MLuaDataManager.worldPersistentData(level);
         persistent.get("Set").call(persistent, LuaValue.valueOf(args.checkjstring(2)), args.arg(3));
         return LuaValue.NONE;
      }));
      bindWorldMethod(table, implementations, "RemoveData", serverLevelFn(supplier, (level, args) -> {
         LuaTable persistent = MLuaDataManager.worldPersistentData(level);
         persistent.get("Remove").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
         return LuaValue.NONE;
      }));

      bindWorldMethod(table, implementations, "ListGameRules", levelFn(supplier, (level, args) -> this.listGameRules(level)));
      bindWorldMethod(table, implementations, "GetGameRule", levelFn(supplier, (level, args) -> this.getGameRule(level, args.checkjstring(2))));
      bindWorldMethod(table, implementations, "SetGameRule", serverLevelFn(supplier, (level, args) -> {
         this.setGameRule(level, args.checkjstring(2), args.arg(3));
         return LuaValue.NONE;
      }));

      MLuaChunkWrapper.bindWorldMethods(table, context.mlua().registration());
      if (initial != null) this.worldInstances.put(initial, new WeakReference<>(table));
      return table;
   }

   private void bindWorldMethod(LuaTable instance, Map<String, LuaValue> implementations,
                                String name, LuaValue implementation) {
      implementations.put(name, implementation);
      MLuaClassMethods.bindInstanceMethod(this.context.mlua().registration(), "World", instance,
         name, implementation);
   }

   private void bindWorldAlias(LuaTable instance, Map<String, LuaValue> implementations,
                               String alias, String source) {
      LuaValue implementation = implementations.get(source);
      if (implementation == null) throw new IllegalStateException("World method is not bound: " + source);
      this.bindWorldMethod(instance, implementations, alias, implementation);
   }

   private LuaTable allWorlds() {
      LuaTable worlds = new LuaTable();
      int index = 1;
      if (this.context.mlua().server() != null) {
         for (ServerLevel level : this.context.mlua().server().getAllLevels()) {
            worlds.set(index++, this.worldTable(() -> level));
         }
         return worlds;
      }

      Level current = this.currentLevel();
      if (current != null) {
         worlds.set(index, this.worldTable(() -> current));
      }
      return worlds;
   }

   private Level currentLevel() {
      if (this.context.mlua().side() == Side.CLIENT) {
         return Minecraft.getInstance().level;
      }
      return this.context.mlua().server() == null ? null : this.context.mlua().server().overworld();
   }

   private Level resolveLevel(String id) {
      if (id == null || id.isEmpty()) {
         return this.currentLevel();
      }
      ResourceLocation target = MLuaResourceLocations.tryParseOrNull(id);
      if (target == null) {
         return null;
      }

      if (this.context.mlua().server() != null) {
         for (ServerLevel level : this.context.mlua().server().getAllLevels()) {
            if (level.dimension().location().equals(target)) {
               return level;
            }
         }
      }

      ClientLevel clientLevel = Minecraft.getInstance().level;
      return clientLevel != null && clientLevel.dimension().location().equals(target) ? clientLevel : null;
   }

   private LuaTable playersTable(Level level) {
      return this.playersTable(level, null);
   }

   private LuaTable playersTable(Level level, AABB box) {
      LuaTable list = new LuaTable();
      if (!(level instanceof ServerLevel serverLevel)) {
         return list;
      }

      int index = 1;
      for (ServerPlayer player : serverLevel.players()) {
         if (box == null || box.contains(player.position())) {
            list.set(index++, MLuaPlayerWrapper.playerTable(
               MCServerPlayerImpl.fromReal(player), this.context.mlua().registration()));
         }
      }
      return list;
   }

   private LuaTable entitiesTable(Level level, AABB box, LuaValue filter) {
      LuaTable list = new LuaTable();
      Iterable<Entity> entities = box == null ? this.allEntities(level) : level.getEntities((Entity)null, box);
      int index = 1;
      for (Entity entity : entities) {
         if (matchesFilter(entity, filter)) {
            list.set(index++, wrapEntity(entity));
         }
      }
      return list;
   }

   private Iterable<Entity> allEntities(Level level) {
      if (level instanceof ServerLevel serverLevel) {
         return serverLevel.getAllEntities();
      }
      if (level instanceof ClientLevel clientLevel) {
         return clientLevel.entitiesForRendering();
      }
      return List.of();
   }

   private LuaTable listGameRules(Level level) {
      LuaTable table = new LuaTable();
      int index = 1;
      List<String> names = new ArrayList<>(GAME_RULE_KEYS.keySet());
      names.sort(String::compareTo);
      for (String name : names) {
         LuaTable entry = new LuaTable();
         entry.set("Name", LuaValue.valueOf(name));
         entry.set("Value", this.getGameRule(level, name));
         table.set(index++, entry);
         table.set(name, entry.get("Value"));
      }
      return table;
   }

   private LuaValue getGameRule(Level level, String name) {
      GameRules.Key<?> key = GAME_RULE_KEYS.get(name);
      if (key == null) {
         throw new LuaError("[MLua] Unknown gamerule: " + name);
      }
      return gameRuleValue(level.getGameRules(), key);
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   private void setGameRule(ServerLevel level, String name, LuaValue value) {
      GameRules.Key<?> key = GAME_RULE_KEYS.get(name);
      if (key == null) {
         throw new LuaError("[MLua] Unknown gamerule: " + name);
      }

      GameRules.Value rule = level.getGameRules().getRule((GameRules.Key)key);
      if (rule instanceof GameRules.BooleanValue booleanValue) {
         booleanValue.set(value.checkboolean(), this.context.mlua().server());
         return;
      }
      if (rule instanceof GameRules.IntegerValue integerValue) {
         integerValue.set(value.checkint(), this.context.mlua().server());
         return;
      }
      throw new LuaError("[MLua] Unsupported gamerule type: " + name);
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   private static LuaValue gameRuleValue(GameRules rules, GameRules.Key<?> key) {
      GameRules.Value rule = rules.getRule((GameRules.Key)key);
      if (rule instanceof GameRules.BooleanValue booleanValue) {
         return LuaValue.valueOf(booleanValue.get());
      }
      if (rule instanceof GameRules.IntegerValue integerValue) {
         return LuaValue.valueOf(integerValue.get());
      }
      return LuaValue.valueOf(rule.toString());
   }

   private LuaValue wrapEntity(Entity entity) {
      return entity == null ? LuaValue.NIL
         : MLuaEntityWrapper.entityTable(entity, this.context.registration());
   }

   private static boolean matchesFilter(Entity entity, LuaValue filter) {
      if (filter == null || filter.isnil()) {
         return true;
      }
      if (filter.isstring()) {
         return entityTypeId(entity).equals(filter.tojstring());
      }
      if (!filter.istable()) {
         return true;
      }

      LuaTable table = filter.checktable();
      LuaValue type = table.get("type");
      if (!type.isnil() && !entityTypeId(entity).equals(type.tojstring())) {
         return false;
      }
      LuaValue playersOnly = table.get("players_only");
      if (!playersOnly.isnil() && playersOnly.toboolean() && !(entity instanceof Player)) {
         return false;
      }
      LuaValue excludePlayers = table.get("exclude_players");
      if (!excludePlayers.isnil() && excludePlayers.toboolean() && entity instanceof Player) {
         return false;
      }
      LuaValue alive = table.get("alive");
      if (!alive.isnil() && alive.toboolean() != entity.isAlive()) {
         return false;
      }
      LuaValue name = table.get("name");
      return name.isnil() || entity.getName().getString().equals(name.tojstring());
   }

   private static String entityTypeId(Entity entity) {
      ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      return id == null ? "unknown" : id.toString();
   }

   private static double[] positionArgs(Varargs args, int index) {
      return MLuaChunkWrapper.positionArgs(args, index);
   }

   private LuaValue levelFn(Supplier<Level> supplier, LevelFunction fn) {
      return varArg((args) -> {
         Level level = supplier.get();
         return level == null ? LuaValue.NIL : fn.apply(level, args);
      });
   }

   private LuaValue serverLevelFn(Supplier<Level> supplier, ServerLevelFunction fn) {
      return varArg((args) -> {
         if (this.context.mlua().side() != Side.SERVER) {
            throw new LuaError("[MLua] World mutation methods are server-side only");
         }
         Level level = supplier.get();
         if (!(level instanceof ServerLevel serverLevel)) {
            throw new LuaError("[MLua] This world is not a server world");
         }
         return fn.apply(serverLevel, args);
      });
   }

   private static LuaValue varArg(Function<Varargs, LuaValue> fn) {
      return new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            try {
               return fn.apply(args);
            } catch (LuaError e) {
               throw e;
            } catch (Exception e) {
               throw new LuaError("[MLua] " + e.getMessage());
            }
         }
      };
   }

   @FunctionalInterface
   private interface LevelFunction {
      LuaValue apply(Level level, Varargs args);
   }

   @FunctionalInterface
   private interface ServerLevelFunction {
      LuaValue apply(ServerLevel level, Varargs args);
   }
}
