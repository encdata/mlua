package dev.mlua.modules.world.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.common.service.MLuaServiceMetadata;
import dev.mlua.modules.world.WorldModule;
import dev.mlua.modules.world.MLuaChunkWrapper;
import dev.mlua.modules.world.MLuaBlockUpdateFlags;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.modules.entity.MLuaEntitySpawner;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateProperties;
import dev.mlua.minecraft.binding.MLuaFluidStateWrapper;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaWorldService extends MLuaServiceBase {
   private final Map<Level, WeakReference<LuaTable>> worldInstances = new WeakHashMap<>();

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
      table.set("Get", varArg((args) -> this.worldValue(this.resolveLevel(args.checkjstring(serviceArgumentIndex(args, table, 1))))));
      table.set("GetAll", varArg((args) -> this.allWorlds()));
      table.set("GetSide", varArg((args) -> LuaValue.valueOf(this.context.mlua().side().name())));
      table.set("GetWeather", currentWorldMethod(table, "GetWeather"));
      table.set("SetWeather", currentWorldMethod(table, "SetWeather"));
      WorldModule.bindWorldServiceMethods(table, methodName -> this.currentWorldMethod(table, methodName));
      MLuaServiceMetadata.attachMethodDescriptions(table, WorldModule.worldServiceMethods());
      return supportColonAndDotCalls(table);
   }

   private LuaValue currentWorldMethod(LuaTable service, String methodName) {
      return varArg(args -> {
         Level level = this.currentLevel();
         if (level == null) return LuaValue.NIL;
         LuaTable instance = this.worldTable(() -> level);
         LuaValue method = instance.get(methodName);
         if (!method.isfunction()) {
            throw new LuaError("[MLua] World service class declares '" + methodName
               + "', but the current live World instance does not implement it.");
         }
         int firstArgument = serviceArgumentIndex(args, service, 1);
         int argumentCount = Math.max(0, args.narg() - firstArgument + 1);
         LuaValue[] forwarded = new LuaValue[argumentCount + 1];
         forwarded[0] = instance;
         for (int index = 0; index < argumentCount; index++) {
            forwarded[index + 1] = args.arg(firstArgument + index);
         }
         return method.invoke(LuaValue.varargsOf(forwarded));
      });
   }

   private LuaValue worldValue(Level level) {
      return level == null ? LuaValue.NIL : this.worldTable(() -> level);
   }

   private synchronized LuaTable worldTable(Supplier<Level> supplier) {
      Level initial = supplier.get();
      if (initial == null) return new LuaTable();
      WeakReference<LuaTable> reference = worldInstances.get(initial);
      LuaTable cached = reference == null ? null : reference.get();
      if (cached != null) return cached;
      LuaTable fields = new LuaTable();
      fields.set("__side", LuaValue.valueOf(initial.isClientSide ? "CLIENT" : "SERVER"));
      fields.set("__world", LuaValue.userdataOf(initial));
      fields.set("__level", LuaValue.userdataOf(initial));
      var registration = context.mlua().registration();
      LuaTable table = registration.createClassInstance("World", fields);

      table.set("GetId", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.dimension().location().toString())));
      table.set("GetDimension", table.get("GetId"));
      table.set("GetSide", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isClientSide ? "CLIENT" : "SERVER")));
      table.set("IsClient", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isClientSide)));
      table.set("IsServer", levelFn(supplier, (level, args) -> LuaValue.valueOf(!level.isClientSide)));
      table.set("GetTime", levelFn(supplier, (level, args) -> LuaValue.valueOf((double)level.getDayTime())));
      table.set("GetDayTime", levelFn(supplier, (level, args) -> LuaValue.valueOf((double)(level.getDayTime() % 24000L))));
      table.set("GetGameTime", levelFn(supplier, (level, args) -> LuaValue.valueOf((double)level.getGameTime())));
      table.set("GetMinBuildHeight", levelFn(supplier, (level, args) -> LuaValue.valueOf(minBuildHeight(level))));
      table.set("GetMaxBuildHeight", levelFn(supplier, (level, args) -> LuaValue.valueOf(maxBuildHeight(level))));
      table.set("GetSpawnPosition", levelFn(supplier, (level, args) -> {
         BlockPos spawn = level.getSharedSpawnPos();
         return MLuaPlayerWrapper.positionTable(spawn.getX(), spawn.getY(), spawn.getZ());
      }));
      table.set("SetSpawnPosition", serverLevelFn(supplier, (level, args) -> {
         double[] position = MLuaPositionArguments.vector3(args, 2);
         level.setDefaultSpawnPos(BlockPos.containing(position[0], position[1], position[2]), MLuaPositionArguments.yaw(args, 2, 3));
         return LuaValue.TRUE;
      }));
      table.set("IsDay", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isDay())));
      table.set("IsNight", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isNight())));
      table.set("SetTime", serverLevelFn(supplier, (level, args) -> {
         level.setDayTime(MLuaLuaArguments.wholeLong(args, 2, "World:SetTime", "ticks"));
         return LuaValue.NONE;
      }));
      table.set("AddTime", serverLevelFn(supplier, (level, args) -> {
         long ticks = MLuaLuaArguments.wholeLong(args, 2, "World:AddTime", "ticks");
         try {
            level.setDayTime(Math.addExact(level.getDayTime(), ticks));
         } catch (ArithmeticException error) {
            throw new LuaError("[MLua] World:AddTime would overflow the world's day time.");
         }
         return LuaValue.NONE;
      }));

      table.set("GetWeather", levelFn(supplier, (level, args) -> {
         if (level.isThundering()) {
            return LuaValue.valueOf("thunder");
         }
         return level.isRaining() ? LuaValue.valueOf("rain") : LuaValue.valueOf("clear");
      }));
      table.set("SetWeather", serverLevelFn(supplier, (level, args) -> {
         String weather = MLuaMinecraftEnums.weatherToJava(args.arg(2));
         int duration = checkedIntArgument(args, 3, 6000, 0, Integer.MAX_VALUE, "weather duration");
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
      table.set("GetDifficulty", levelFn(supplier, (level, args) -> MLuaMinecraftEnums.difficultyOf(level.getDifficulty())));
      table.set("SetDifficulty", serverLevelFn(supplier, (level, args) -> {
         MinecraftServer server = this.context.mlua().server();
         if (server == null) {
            throw new LuaError("[MLua] SetDifficulty requires an active server.");
         }
         Difficulty difficulty = MLuaMinecraftEnums.resolveDifficulty(args.arg(2));
         server.setDifficulty(difficulty, true);
         return LuaValue.NONE;
      }));

      table.set("GetBlock", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return MLuaBlockWrapper.blockTable(level, blockPos, context.mlua().registration());
      }));
      table.set("GetBlockEntity", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         LuaTable block = MLuaBlockWrapper.blockTable(level, blockPos, context.mlua().registration());
         return block.get("BlockEntity");
      }));
      table.set("GetBlockState", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return MLuaBlockStateWrapper.wrap(level.getBlockState(blockPos), level, blockPos, context.mlua().registration());
      }));
      table.set("GetFluid", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         FluidState state = level.getFluidState(blockPos);
         if (state == null || state.isEmpty()) {
            return LuaValue.NIL;
         }

         ResourceLocation id = BuiltInRegistries.FLUID.getKey(state.getType());
         return id == null ? LuaValue.NIL : LuaValue.valueOf(id.toString());
      }));
      table.set("GetFluidState", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return MLuaFluidStateWrapper.wrap(level.getFluidState(blockPos), level, blockPos, context.mlua().registration());
      }));
      table.set("GetBiome", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         Biome biome = level.getBiome(blockPos).value();
         ResourceLocation id = biomeId(level, biome);
         return LuaValue.valueOf(id == null ? "unknown" : id.toString());
      }));
      table.set("GetLightLevel", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return LuaValue.valueOf(level.getMaxLocalRawBrightness(blockPos));
      }));
      table.set("GetSkyLight", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return LuaValue.valueOf(level.getBrightness(LightLayer.SKY, blockPos));
      }));
      table.set("GetBlockLight", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return LuaValue.valueOf(level.getBrightness(LightLayer.BLOCK, blockPos));
      }));
      table.set("CanSeeSky", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return LuaValue.valueOf(level.canSeeSky(blockPos));
      }));
      table.set("IsRaining", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isRaining())));
      table.set("IsThundering", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isThundering())));
      table.set("IsRainingAt", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return LuaValue.valueOf(level.isRainingAt(blockPos));
      }));
      table.set("GetSurfaceY", levelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos column = BlockPos.containing(pos[0], minBuildHeight(level), pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, column)) return LuaValue.NIL;
         return LuaValue.valueOf(level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()));
      }));
      table.set("Raycast", levelFn(supplier, (level, args) -> {
         double[] origin = positionArgs(args, 2);
         int directionIndex = 2 + coordinateWidth(args, 2);
         double[] direction = dev.mlua.math.MLuaVector.components3(args.arg(directionIndex));
         int distanceIndex = directionIndex + coordinateWidth(args, directionIndex);
         double distance = MLuaLuaArguments.optionalFiniteNumber(args, distanceIndex, 100.0, "World:Raycast", "distance");
         if (!Double.isFinite(distance) || distance < 0) {
            throw new LuaError("[MLua] World:Raycast distance must be finite and non-negative.");
         }
         for (double component : origin) {
            if (!Double.isFinite(component)) throw new LuaError("[MLua] World:Raycast origin must be finite.");
         }
         for (double component : direction) {
            if (!Double.isFinite(component)) throw new LuaError("[MLua] World:Raycast direction must be finite.");
         }
         LuaValue fluidArgument = args.arg(distanceIndex + 1);
         if (!fluidArgument.isnil() && !fluidArgument.isboolean()) {
            throw new LuaError("[MLua] World:Raycast includeFluids must be a boolean.");
         }
         boolean includeFluids = fluidArgument.toboolean();
         Vec3 from = new Vec3(origin[0], origin[1], origin[2]);
         double directionScale = Math.max(Math.abs(direction[0]), Math.max(Math.abs(direction[1]), Math.abs(direction[2])));
         if (directionScale == 0) return LuaValue.NIL;
         Vec3 dir = new Vec3(direction[0] / directionScale, direction[1] / directionScale, direction[2] / directionScale).normalize();
         Vec3 to = from.add(dir.scale(distance));
         if (!Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)) {
            throw new LuaError("[MLua] World:Raycast endpoint must be finite.");
         }
         BlockHitResult hitResult = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, (Entity)null));
         if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return LuaValue.NIL;
         }

         LuaTable result = this.context.mlua().registration().createClassInstance("BlockRaycastResult");
         result.set("HitType", LuaValue.valueOf("block"));
         result.set("Type", LuaValue.valueOf("block"));
         result.set("Position", dev.mlua.math.MLuaVector.vector3(hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z));
         result.set("Normal", dev.mlua.math.MLuaVector.vector3(hitResult.getDirection().getStepX(), hitResult.getDirection().getStepY(), hitResult.getDirection().getStepZ()));
         result.set("Distance", LuaValue.valueOf(Math.sqrt(from.distanceToSqr(hitResult.getLocation()))));
         result.set("Block", LuaValue.valueOf(String.valueOf(BuiltInRegistries.BLOCK.getKey(level.getBlockState(hitResult.getBlockPos()).getBlock()))));
         result.set("Face", LuaValue.valueOf(hitResult.getDirection().getName()));
         result.set("BlockInstance", MLuaBlockWrapper.blockTable(level, hitResult.getBlockPos(), this.context.mlua().registration()));
         return result;
      }));
      table.set("RaycastEntity", levelFn(supplier, (level, args) -> {
         double[] origin = positionArgs(args, 2);
         int directionIndex = 2 + coordinateWidth(args, 2);
         double[] direction = dev.mlua.math.MLuaVector.components3(args.arg(directionIndex));
         int distanceIndex = directionIndex + coordinateWidth(args, directionIndex);
         double distance = MLuaLuaArguments.optionalFiniteNumber(
            args, distanceIndex, 100.0, "World:RaycastEntity", "distance");
         if (!Double.isFinite(distance) || distance < 0) {
            throw new LuaError("[MLua] World:RaycastEntity distance must be finite and non-negative.");
         }
         for (double component : origin) {
            if (!Double.isFinite(component)) throw new LuaError("[MLua] World:RaycastEntity origin must be finite.");
         }
         for (double component : direction) {
            if (!Double.isFinite(component)) throw new LuaError("[MLua] World:RaycastEntity direction must be finite.");
         }
         Vec3 from = new Vec3(origin[0], origin[1], origin[2]);
         double directionScale = Math.max(Math.abs(direction[0]), Math.max(Math.abs(direction[1]), Math.abs(direction[2])));
         if (directionScale == 0) return LuaValue.NIL;
         Vec3 rayDirection = new Vec3(direction[0] / directionScale, direction[1] / directionScale, direction[2] / directionScale).normalize();
         Vec3 to = from.add(rayDirection.scale(distance));
         if (!Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)) {
            throw new LuaError("[MLua] World:RaycastEntity endpoint must be finite.");
         }
         BlockHitResult blockHit = level.clip(new ClipContext(from, to,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)null));
         double nearestDistance = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
            ? Double.POSITIVE_INFINITY : from.distanceToSqr(blockHit.getLocation());
         AABB search = new AABB(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
            Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).inflate(1.0);
         LuaValue filter = args.arg(distanceIndex + 1);
         Entity nearest = null;
         Vec3 nearestHit = null;
         Vec3 nearestNormal = null;
         for (Entity candidate : level.getEntities((Entity)null, search)) {
            if (candidate.isRemoved() || !candidate.isPickable() || !matchesFilter(candidate, filter)) continue;
            AABB hitBox = candidate.getBoundingBox().inflate(candidate.getPickRadius());
            var hit = hitBox.clip(from, to);
            if (hit.isEmpty()) continue;
            double hitDistance = from.distanceToSqr(hit.get());
            if (hitDistance < nearestDistance) {
               nearest = candidate;
               nearestHit = hit.get();
               nearestNormal = rayHitNormal(hitBox, nearestHit);
               nearestDistance = hitDistance;
            }
         }
         if (nearest == null) return LuaValue.NIL;
         LuaTable result = this.context.mlua().registration().createClassInstance("EntityRaycastResult");
         result.set("HitType", LuaValue.valueOf("entity"));
         result.set("Type", LuaValue.valueOf("entity"));
         result.set("Entity", this.wrapEntity(nearest));
         result.set("Position", dev.mlua.math.MLuaVector.vector3(nearestHit.x, nearestHit.y, nearestHit.z));
         result.set("Normal", dev.mlua.math.MLuaVector.vector3(nearestNormal.x, nearestNormal.y, nearestNormal.z));
         result.set("Distance", LuaValue.valueOf(Math.sqrt(nearestDistance)));
         return result;
      }));

      table.set("GetPlayers", levelFn(supplier, (level, args) -> this.playersTable(level)));
      table.set("GetEntities", levelFn(supplier, (level, args) -> this.entitiesTable(level, null, LuaValue.NIL)));
      table.set("FindEntitiesInRadius", levelFn(supplier, (level, args) -> {
         double[] center = checkedQueryPosition(positionArgs(args, 2));
         int radiusIndex = 2 + coordinateWidth(args, 2);
         double radius = checkedQueryRadius(args.checkdouble(radiusIndex));
         return this.entitiesInRadius(level, center, radius, args.arg(radiusIndex + 1));
      }));
      table.set("GetEntitiesInRadius", table.get("FindEntitiesInRadius"));
      table.set("GetEntitiesNearby", table.get("FindEntitiesInRadius"));
      table.set("FindEntitiesInBox", levelFn(supplier, (level, args) -> {
         double[] min = checkedQueryPosition(positionArgs(args, 2));
         int maxIndex = 2 + coordinateWidth(args, 2);
         double[] max = checkedQueryPosition(positionArgs(args, maxIndex));
         AABB box = checkedQueryBox(min, max);
         return this.entitiesTable(level, box, args.arg(maxIndex + coordinateWidth(args, maxIndex)));
      }));
      table.set("FindEntitiesInBounds", levelFn(supplier, (level, args) ->
         this.entitiesTable(level, entityBounds(args.arg(2)), args.arg(3))));
      table.set("FindPlayersInBounds", levelFn(supplier, (level, args) ->
         this.playersTable(level, entityBounds(args.arg(2)))));
      table.set("FindPlayersInRadius", levelFn(supplier, (level, args) -> {
         double[] center = checkedQueryPosition(positionArgs(args, 2));
         double radius = checkedQueryRadius(args.checkdouble(2 + coordinateWidth(args, 2)));
         return this.playersInRadius(level, center, radius);
      }));
      table.set("GetPlayersNearby", table.get("FindPlayersInRadius"));
      table.set("FindNearestPlayer", levelFn(supplier, (level, args) -> {
         double[] pos = checkedQueryPosition(positionArgs(args, 2));
         double maxDistance = MLuaLuaArguments.optionalFiniteNumber(
            args, 2 + coordinateWidth(args, 2), Double.MAX_VALUE, "World:FindNearestPlayer", "maxDistance");
         maxDistance = checkedQueryRadius(maxDistance);
         Player nearest = null;
         double nearestDistance = maxDistance * maxDistance;
         for (Player player : this.players(level)) {
            double distance = player.distanceToSqr(pos[0], pos[1], pos[2]);
            if (distance <= nearestDistance) {
               nearestDistance = distance;
               nearest = player;
            }
         }

         return nearest == null ? LuaValue.NIL : MLuaPlayerWrapper.playerEntityTable(nearest, context.mlua().registration());
      }));
      table.set("GetNearestPlayer", table.get("FindNearestPlayer"));
      table.set("FindNearestEntity", levelFn(supplier, (level, args) -> {
         double[] pos = checkedQueryPosition(positionArgs(args, 2));
         int distanceIndex = 2 + coordinateWidth(args, 2);
         double maxDistance = checkedQueryRadius(MLuaLuaArguments.optionalFiniteNumber(
            args, distanceIndex, Double.MAX_VALUE, "World:FindNearestEntity", "maxDistance"));
         LuaValue filter = args.arg(distanceIndex + 1);
         Entity nearest = null;
         double nearestDistance = maxDistance * maxDistance;
         for (Entity entity : MLuaEntityQuery.findWithinRadius(level, pos[0], pos[1], pos[2],
               maxDistance, null, filter, context.mlua().registration())) {
            double distance = entity.distanceToSqr(pos[0], pos[1], pos[2]);
            if (distance <= nearestDistance) {
               nearestDistance = distance;
               nearest = entity;
            }
         }

         return wrapEntity(nearest);
      }));

      table.set("SetBlock", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         int blockIndex = 2 + coordinateWidth(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         LuaValue blockValue = args.arg(blockIndex);
         net.minecraft.world.level.block.state.BlockState state;
         if (context.mlua().registration().isInstanceOf(blockValue, "BlockState")) {
            state = MLuaBlockStateWrapper.blockStateFromLua(blockValue, context.mlua().registration());
         } else {
            String blockId = blockValue.checkjstring();
            Block block = BuiltInRegistries.BLOCK.getOptional(MLuaResourceLocations.parse(blockId)).orElse(null);
            if (block == null) {
               throw new LuaError("[MLua] Unknown block: " + blockId);
            }
            state = block.defaultBlockState();
         }
         LuaValue propertiesArg = args.arg(blockIndex + 1);
         if (!propertiesArg.isnil() && !propertiesArg.istable() && !propertiesArg.isboolean() && !propertiesArg.isnumber()) {
            throw new LuaError("[MLua] World:SetBlock expects block properties, a loadChunk boolean, or update flags after the block.");
         }
         LuaValue properties = propertiesArg.istable() ? propertiesArg : LuaValue.NIL;
         boolean loadChunk = true;
         LuaValue updateFlagsValue;
         if (propertiesArg.istable()) {
            LuaValue loadOrFlags = args.arg(blockIndex + 2);
            if (loadOrFlags.isboolean()) {
               loadChunk = loadOrFlags.toboolean();
               updateFlagsValue = args.arg(blockIndex + 3);
            } else {
               updateFlagsValue = loadOrFlags.isnil() ? args.arg(blockIndex + 3) : loadOrFlags;
            }
         } else if (propertiesArg.isboolean()) {
            loadChunk = propertiesArg.toboolean();
            updateFlagsValue = args.arg(blockIndex + 2);
         } else if (propertiesArg.isnumber()) {
            updateFlagsValue = propertiesArg;
         } else if (propertiesArg.isnil()) {
            LuaValue loadOrFlags = args.arg(blockIndex + 2);
            if (loadOrFlags.isboolean()) {
               loadChunk = loadOrFlags.toboolean();
               updateFlagsValue = args.arg(blockIndex + 3);
            } else {
               updateFlagsValue = loadOrFlags.isnil() ? args.arg(blockIndex + 3) : loadOrFlags;
            }
         } else {
            throw new LuaError("[MLua] World:SetBlock expects block properties, a loadChunk boolean, or integer update flags after the block.");
         }
         int updateFlags = MLuaBlockUpdateFlags.read(updateFlagsValue, "World:SetBlock");
         net.minecraft.world.level.block.state.BlockState resolvedState = MLuaBlockStateProperties.apply(state, properties);
         if (!ensureChunkLoaded(level, blockPos, loadChunk)) return LuaValue.FALSE;
         return LuaValue.valueOf(level.setBlock(blockPos, resolvedState, updateFlags));
      }));
      table.set("SetBlockState", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         int stateIndex = 2 + coordinateWidth(args, 2);
         net.minecraft.world.level.block.state.BlockState state =
            MLuaBlockStateWrapper.blockStateFromLua(args.arg(stateIndex), context.mlua().registration());
         LuaValue loadOrFlags = args.arg(stateIndex + 1);
         boolean loadChunk = !loadOrFlags.isboolean() || loadOrFlags.toboolean();
         int flagsIndex = loadOrFlags.isboolean() ? stateIndex + 2 : stateIndex + 1;
         int updateFlags = MLuaBlockUpdateFlags.read(args.arg(flagsIndex), "World:SetBlockState");
         if (!ensureChunkLoaded(level, blockPos, loadChunk)) return LuaValue.FALSE;
         return LuaValue.valueOf(level.setBlock(blockPos, state, updateFlags));
      }));
      table.set("BreakBlock", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         boolean dropItems = MLuaLuaArguments.optionalBoolean(
            args, 2 + coordinateWidth(args, 2), true, "World:BreakBlock", "dropItems");
         level.destroyBlock(BlockPos.containing(pos[0], pos[1], pos[2]), dropItems);
         return LuaValue.TRUE;
      }));
      table.set("DestroyBlock", table.get("BreakBlock"));
      table.set("PlaySound", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         int soundIndex = 2 + coordinateWidth(args, 2);
         String soundName = args.checkjstring(soundIndex);
         SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(MLuaResourceLocations.parse(soundName)).orElse(null);
         if (sound == null) {
            throw new LuaError("[MLua] Unknown sound: " + soundName);
         }
         float volume = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
            args, soundIndex + 1, 1.0f, "World:PlaySound", "volume");
         float pitch = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
            args, soundIndex + 2, 1.0f, "World:PlaySound", "pitch");
         level.playSound((Player)null, pos[0], pos[1], pos[2], sound, SoundSource.PLAYERS, volume, pitch);
         return LuaValue.NONE;
      }));
      table.set("SpawnParticle", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         int particleIndex = 2 + coordinateWidth(args, 2);
         String particleName = args.checkjstring(particleIndex);
         int count = checkedIntArgument(args, particleIndex + 1, 1, 1, Integer.MAX_VALUE, "particle count");
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
      table.set("SpawnEntity", serverLevelFn(supplier, (level, args) -> {
         String entityId = args.checkjstring(2);
         double[] pos = positionArgs(args, 3);
         int rotationIndex = 3 + coordinateWidth(args, 3);
         float yaw = MLuaPositionArguments.yaw(args, 3, rotationIndex);
         float pitch = MLuaPositionArguments.pitch(args, 3, rotationIndex + 1);
         EntityType<?> entityType = MLuaEntitySpawner.resolveType(this.context.mlua().ownerModId(), entityId);
         if (entityType == null) throw new LuaError("[MLua] Unknown entity: " + entityId);
         Entity entity = MLuaEntitySpawner.spawn(level, entityType, pos[0], pos[1], pos[2], yaw, pitch);
         return wrapEntity(entity);
      }));
      table.set("DropItem", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         int itemIndex = 2 + coordinateWidth(args, 2);
         String itemId = args.checkjstring(itemIndex);
         int count = checkedIntArgument(args, itemIndex + 1, 1, 1, Integer.MAX_VALUE, "item count");
         net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(MLuaResourceLocations.parse(itemId)).orElse(null);
         if (item == null) {
            throw new LuaError("[MLua] Unknown item: " + itemId);
         }
         int maxStackSize = new ItemStack(item).getMaxStackSize();
         if (count > maxStackSize) {
            throw new LuaError("[MLua] Item count " + count + " exceeds " + itemId
               + " stack limit " + maxStackSize);
         }
         if (!ensureChunkLoaded(level, BlockPos.containing(pos[0], pos[1], pos[2]), true)) return LuaValue.NIL;
         ItemEntity entity = new ItemEntity(level, pos[0], pos[1], pos[2], new ItemStack(item, count));
         if (!level.addFreshEntity(entity)) return LuaValue.NIL;
         return wrapEntity(entity);
      }));
      table.set("Explode", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         int powerIndex = 2 + coordinateWidth(args, 2);
         float power = MLuaLuaArguments.optionalFiniteFloat(args, powerIndex, 4.0f, "World:Explode", "power");
         if (power < 0.0f) throw new LuaError("[MLua] World:Explode power must be non-negative.");
         boolean fire = MLuaLuaArguments.optionalBoolean(args, powerIndex + 1, false, "World:Explode", "fire");
         level.explode((Entity)null, pos[0], pos[1], pos[2], power, fire, Level.ExplosionInteraction.BLOCK);
         return LuaValue.NONE;
      }));
      table.set("SpawnLightning", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         if (!ensureChunkLoaded(level, BlockPos.containing(pos[0], pos[1], pos[2]), true)) return LuaValue.NIL;
         Entity lightning = EntityType.LIGHTNING_BOLT.create(level);
         if (lightning == null) {
            throw new LuaError("[MLua] Failed to create lightning entity");
         }
         lightning.setPos(pos[0], pos[1], pos[2]);
         if (!level.addFreshEntity(lightning)) return LuaValue.NIL;
         return wrapEntity(lightning);
      }));

      table.set("GetBlockInventory", serverLevelFn(supplier, (level, args) -> {
         double[] pos = positionArgs(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         BlockEntity blockEntity = level.getBlockEntity(blockPos);
         if (blockEntity instanceof Container container
            && (!(blockEntity instanceof dev.mlua.runtime.client.MLuaVisualBlockEntity scripted) || scripted.hasContainer())) {
            return MLuaContainer.containerTable(MCContainerImpl.fromReal(container), true, context.mlua().registration());
         }
         return LuaValue.NIL;
      }));

      table.set("GetPersistentData", levelFn(supplier, (level, args) -> MLuaDataManager.worldPersistentData(level)));
      table.set("GetSyncedData", levelFn(supplier, (level, args) -> MLuaDataManager.worldSyncedData(level)));
      table.set("GetData", levelFn(supplier, (level, args) -> {
         LuaTable persistent = MLuaDataManager.worldPersistentData(level);
         return persistent.get("Get").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
      }));
      table.set("SetData", serverLevelFn(supplier, (level, args) -> {
         LuaTable persistent = MLuaDataManager.worldPersistentData(level);
         persistent.get("Set").call(persistent, LuaValue.valueOf(args.checkjstring(2)), args.arg(3));
         return LuaValue.NONE;
      }));
      table.set("RemoveData", serverLevelFn(supplier, (level, args) -> {
         LuaTable persistent = MLuaDataManager.worldPersistentData(level);
         persistent.get("Remove").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
         return LuaValue.NONE;
      }));

      table.set("ListGameRules", levelFn(supplier, (level, args) -> this.listGameRules(level)));
      table.set("GetGameRule", levelFn(supplier, (level, args) -> this.getGameRule(level, args.checkjstring(2))));
      table.set("GetGameRuleEntry", levelFn(supplier, (level, args) -> this.getGameRuleEntry(level, args.checkjstring(2))));
      table.set("SetGameRule", serverLevelFn(supplier, (level, args) -> {
         this.setGameRule(level, args.checkjstring(2), args.arg(3));
         return LuaValue.NONE;
      }));

      WorldModule.bindWorldInstanceMethods(this.context.mlua().registration(), table);
      MLuaChunkWrapper.bindWorldMethods(table, this.context.mlua().registration());
      worldInstances.put(initial, new WeakReference<>(table));
      return table;
   }

   private synchronized LuaTable allWorlds() {
      LuaTable worlds = new LuaTable();
      Set<Level> activeLevels = Collections.newSetFromMap(new IdentityHashMap<>());
      int index = 1;
      if (this.context.mlua().side() == Side.SERVER && this.context.mlua().server() != null) {
         for (ServerLevel level : this.context.mlua().server().getAllLevels()) {
            activeLevels.add(level);
            worlds.set(index++, this.worldTable(() -> level));
         }
      } else if (this.context.mlua().side() == Side.CLIENT) {
         Level current = this.currentLevel();
         if (current != null) {
            activeLevels.add(current);
            worlds.set(index, this.worldTable(() -> current));
         }
      }
      worldInstances.keySet().removeIf(level -> !activeLevels.contains(level));
      return worlds;
   }

   private Level currentLevel() {
      if (this.context.mlua().side() == Side.CLIENT) {
         return clientLevel();
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

      if (this.context.mlua().side() == Side.SERVER && this.context.mlua().server() != null) {
         for (ServerLevel level : this.context.mlua().server().getAllLevels()) {
            if (level.dimension().location().equals(target)) {
               return level;
            }
         }
      }

      if (this.context.mlua().side() == Side.CLIENT) {
         Level clientLevel = clientLevel();
         return clientLevel != null && clientLevel.dimension().location().equals(target) ? clientLevel : null;
      }
      return null;
   }

   private LuaTable playersTable(Level level) {
      return this.playersTable(level, null);
   }

   private LuaTable playersTable(Level level, AABB box) {
      LuaTable list = new LuaTable();
      int index = 1;
      for (Player player : this.players(level)) {
         if (box == null || box.contains(player.position())) {
            list.set(index++, MLuaPlayerWrapper.playerEntityTable(player, context.mlua().registration()));
         }
      }
      return list;
   }

   private LuaTable entitiesTable(Level level, AABB box, LuaValue filter) {
      MLuaEntityQuery.validateFilter(filter, context.mlua().registration());
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

   private LuaTable entitiesInRadius(Level level, double[] center, double radius, LuaValue filter) {
      LuaTable list = new LuaTable();
      int index = 1;
      for (Entity entity : MLuaEntityQuery.findWithinRadius(level, center[0], center[1], center[2],
            radius, null, filter, context.mlua().registration())) {
         list.set(index++, wrapEntity(entity));
      }
      return list;
   }

   private LuaTable playersInRadius(Level level, double[] center, double radius) {
      LuaTable list = new LuaTable();
      int index = 1;
      LuaTable playerFilter = new LuaTable();
      playerFilter.set("players_only", LuaValue.TRUE);
      for (Entity entity : MLuaEntityQuery.findWithinRadius(level, center[0], center[1], center[2],
            radius, null, playerFilter, context.mlua().registration())) {
         if (entity instanceof Player player) {
            list.set(index++, MLuaPlayerWrapper.playerEntityTable(player, context.mlua().registration()));
         }
      }
      return list;
   }

   private List<Player> players(Level level) {
      List<Player> players = new ArrayList<>();
      for (Entity entity : this.allEntities(level)) {
         if (entity instanceof Player player) players.add(player);
      }
      return players;
   }

   private static AABB entityBounds(LuaValue value) {
      if (!value.istable() || !value.get("__type").isstring()
            || !value.get("__type").checkjstring().equals("EntityBounds")) {
         throw new LuaError("[MLua] Expected an EntityBounds instance.");
      }
      LuaTable bounds = value.checktable();
      LuaValue minimum = bounds.get("Minimum");
      LuaValue maximum = bounds.get("Maximum");
      if (!dev.mlua.math.MLuaVector.isVector3(minimum) || !dev.mlua.math.MLuaVector.isVector3(maximum)) {
         throw new LuaError("[MLua] EntityBounds must contain Vector3 Minimum and Maximum corners.");
      }
      LuaTable min = minimum.checktable();
      LuaTable max = maximum.checktable();
      double minX = min.get("X").todouble();
      double minY = min.get("Y").todouble();
      double minZ = min.get("Z").todouble();
      double maxX = max.get("X").todouble();
      double maxY = max.get("Y").todouble();
      double maxZ = max.get("Z").todouble();
      if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
            || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
            || minX > maxX || minY > maxY || minZ > maxZ) {
         throw new LuaError("[MLua] EntityBounds corners must be finite and ordered from minimum to maximum.");
      }
      return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
   }

   private static Vec3 rayHitNormal(AABB bounds, Vec3 hit) {
      double[] distances = {
         Math.abs(hit.x - bounds.minX), Math.abs(bounds.maxX - hit.x),
         Math.abs(hit.y - bounds.minY), Math.abs(bounds.maxY - hit.y),
         Math.abs(hit.z - bounds.minZ), Math.abs(bounds.maxZ - hit.z)
      };
      int nearest = 0;
      for (int index = 1; index < distances.length; index++) {
         if (distances[index] < distances[nearest]) nearest = index;
      }
      return switch (nearest) {
         case 0 -> new Vec3(-1, 0, 0);
         case 1 -> new Vec3(1, 0, 0);
         case 2 -> new Vec3(0, -1, 0);
         case 3 -> new Vec3(0, 1, 0);
         case 4 -> new Vec3(0, 0, -1);
         default -> new Vec3(0, 0, 1);
      };
   }

   private Iterable<Entity> allEntities(Level level) {
      return MLuaEntityQuery.loadedEntities(level);
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

   private LuaTable listGameRules(Level level) {
      LuaTable table = new LuaTable();
      int index = 1;
      Map<String, GameRules.Key<?>> keys = gameRuleKeys();
      List<String> names = new ArrayList<>(keys.keySet());
      names.sort(String::compareTo);
      for (String name : names) {
         LuaTable entry = this.gameRuleEntry(level, name, keys.get(name));
         table.set(index++, entry);
         table.set(name, entry.get("Value"));
      }
      return table;
   }

   private LuaValue getGameRuleEntry(Level level, String name) {
      GameRules.Key<?> key = gameRuleKeys().get(name);
      if (key == null) throw new LuaError("[MLua] Unknown gamerule: " + name);
      return this.gameRuleEntry(level, name, key);
   }

   private LuaTable gameRuleEntry(Level level, String name, GameRules.Key<?> key) {
      LuaTable fields = new LuaTable();
      fields.set("Name", LuaValue.valueOf(name));
      fields.set("__mlua_get_game_rule_value", new org.luaj.vm2.lib.ZeroArgFunction() {
         @Override public LuaValue call() {
            return gameRuleValue(level.getGameRules(), key);
         }
      });
      fields.set("__mlua_set_game_rule_value", new org.luaj.vm2.lib.OneArgFunction() {
         @Override public LuaValue call(LuaValue value) {
            if (!(level instanceof ServerLevel serverLevel)) {
               throw new LuaError("[MLua] GameRuleEntry values can only be changed on the server.");
            }
            setGameRule(serverLevel, name, value);
            return LuaValue.NONE;
         }
      });
      return this.context.mlua().registration().createClassInstance("GameRuleEntry", fields);
   }

   private LuaValue getGameRule(Level level, String name) {
      GameRules.Key<?> key = gameRuleKeys().get(name);
      if (key == null) {
         throw new LuaError("[MLua] Unknown gamerule: " + name);
      }
      return gameRuleValue(level.getGameRules(), key);
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   private void setGameRule(ServerLevel level, String name, LuaValue value) {
      GameRules.Key<?> key = gameRuleKeys().get(name);
      if (key == null) {
         throw new LuaError("[MLua] Unknown gamerule: " + name);
      }

      GameRules.Value rule = level.getGameRules().getRule((GameRules.Key)key);
      if (rule instanceof GameRules.BooleanValue booleanValue) {
         if (!value.isboolean()) {
            throw new LuaError("[MLua] Gamerule " + name + " requires a boolean value.");
         }
         booleanValue.set(value.checkboolean(), this.context.mlua().server());
         return;
      }
      if (rule instanceof GameRules.IntegerValue integerValue) {
         if (!value.isnumber()) {
            throw new LuaError("[MLua] Gamerule " + name + " requires an integer value.");
         }
         double number = value.todouble();
         if (!Double.isFinite(number) || number != Math.rint(number)
            || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new LuaError("[MLua] Gamerule " + name + " requires a whole number within the 32-bit integer range.");
         }
         integerValue.set((int)number, this.context.mlua().server());
         return;
      }
      throw new LuaError("[MLua] Unsupported gamerule type: " + name);
   }

   private static Map<String, GameRules.Key<?>> gameRuleKeys() {
      Map<String, GameRules.Key<?>> keys = new HashMap<>();
      GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
         @Override
         public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
            keys.put(key.getId(), key);
         }
      });
      return keys;
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
      return entity == null ? LuaValue.NIL : MLuaEntityWrapper.entityTable(entity, context.mlua().registration());
   }

   private boolean matchesFilter(Entity entity, LuaValue filter) {
      return MLuaEntityQuery.matches(entity, filter, context.mlua().registration());
   }

   private static double checkedQueryRadius(double radius) {
      return MLuaLuaArguments.finiteNonNegativeNumber(radius, "Entity query", "radius");
   }

   private static double[] checkedQueryPosition(double[] position) {
      for (double component : position) {
         if (!Double.isFinite(component)) {
            throw new LuaError("[MLua] Entity query position must contain only finite coordinates.");
         }
      }
      return position;
   }

   private static AABB checkedQueryBox(double[] minimum, double[] maximum) {
      for (int axis = 0; axis < 3; axis++) {
         if (minimum[axis] > maximum[axis]) {
            throw new LuaError("[MLua] Entity query box minimum must not exceed its maximum.");
         }
      }
      return new AABB(minimum[0], minimum[1], minimum[2], maximum[0], maximum[1], maximum[2]);
   }

   private static double[] positionArgs(Varargs args, int index) {
      return MLuaPositionArguments.vector3(args, index);
   }

   private static int coordinateWidth(Varargs args, int index) {
      return args.arg(index).istable() ? 1 : 3;
   }

   private static boolean ensureChunkLoaded(ServerLevel level, BlockPos position, boolean load) {
      if (level.hasChunkAt(position)) return true;
      if (!load) return false;
      ChunkPos chunk = new ChunkPos(position.getX() >> 4, position.getZ() >> 4);
      level.getChunk(chunkX(chunk), chunkZ(chunk));
      return level.hasChunkAt(position);
   }

   private static int chunkX(ChunkPos chunk) {
      return MLuaChunkWrapper.chunkX(chunk);
   }

   private static int chunkZ(ChunkPos chunk) {
      return MLuaChunkWrapper.chunkZ(chunk);
   }

   private static int minBuildHeight(Level level) {
      //--version 26.1
      return level.getMinY();
      //--endversion
      //--version 1.20.1,1.21.1
      return level.getMinBuildHeight();
      //--endversion
   }

   private static int maxBuildHeight(Level level) {
      //--version 26.1
      return level.getMinY() + level.getHeight();
      //--endversion
      //--version 1.20.1,1.21.1
      return level.getMaxBuildHeight();
      //--endversion
   }

   private static int checkedIntArgument(Varargs args, int index, int defaultValue,
      int minimum, int maximum, String label) {
      LuaValue value = args.arg(index);
      if (value.isnil()) return defaultValue;
      if (!value.isnumber()) throw new LuaError("[MLua] " + label + " must be a whole number");
      double number = value.todouble();
      if (!Double.isFinite(number) || number != Math.rint(number)
         || number < minimum || number > maximum) {
         throw new LuaError("[MLua] " + label + " must be a whole number from " + minimum + " to " + maximum);
      }
      return (int)number;
   }

   private static ResourceLocation biomeId(Level level, Biome biome) {
      //--version 26.1
      return level.registryAccess().lookupOrThrow(Registries.BIOME).getKey(biome);
      //--endversion
      //--version 1.20.1,1.21.1
      return level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
      //--endversion
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

   private static LuaValue varArg(Function<Varargs, Varargs> fn) {
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
