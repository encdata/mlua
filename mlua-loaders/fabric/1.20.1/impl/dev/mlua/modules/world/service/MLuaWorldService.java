package dev.mlua.modules.world.service;

import dev.mlua.modules.entity.MLuaEntityBounds;
import dev.mlua.modules.world.MLuaBlockUpdateFlags;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.runtime.server.MLuaServerThread;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.common.service.MLuaServiceMetadata;
import dev.mlua.modules.world.WorldModule;
import dev.mlua.modules.world.MLuaChunkWrapper;
import dev.mlua.modules.world.MLuaWorldChunkApi;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.modules.world.MLuaWorldGameRules;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.math.MLuaVector;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateProperties;
import dev.mlua.minecraft.binding.MLuaBiomeWrapper;
import dev.mlua.minecraft.binding.MLuaFluidStateWrapper;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
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
   private final MLuaWorldInstanceCache worldInstances = new MLuaWorldInstanceCache();

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
      MLuaServiceMetadata.attachMethodDescriptions(table, WorldModule.worldServiceMethods());
      return supportColonAndDotCalls(table);
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
         LuaTable cached = this.worldInstances.get(initial);
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
         double[] pos = MLuaPositionArguments.vector3(args, 2);
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
      bindWorldMethod(table, implementations, "SetSpawnPosition", serverLevelFn(supplier, (level, args) -> {
         double[] position = MLuaPositionArguments.vector3(args, 2);
         level.setDefaultSpawnPos(BlockPos.containing(position[0], position[1], position[2]), MLuaPositionArguments.yaw(args, 2, 3));
         return LuaValue.TRUE;
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
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return MLuaBlockWrapper.blockTable(level, blockPos, context.mlua().registration());
      }));
      bindWorldMethod(table, implementations, "GetBlockEntity", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return MLuaBlockWrapper.blockEntityAt(level, blockPos, context.mlua().registration());
      }));
      bindWorldMethod(table, implementations, "GetBlockState", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return MLuaBlockStateWrapper.wrap(level.getBlockState(blockPos), level, blockPos, context.mlua().registration());
      }));
      bindWorldMethod(table, implementations, "GetFluid", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         FluidState state = level.getFluidState(blockPos);
         if (state == null || state.isEmpty()) {
            return LuaValue.NIL;
         }

         ResourceLocation id = BuiltInRegistries.FLUID.getKey(state.getType());
         return id == null ? LuaValue.NIL : LuaValue.valueOf(id.toString());
      }));
      bindWorldMethod(table, implementations, "GetFluidState", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return MLuaFluidStateWrapper.wrap(level.getFluidState(blockPos), level, blockPos, context.mlua().registration());
      }));
      bindWorldMethod(table, implementations, "GetBiome", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return MLuaBiomeWrapper.wrap(level.getBiome(blockPos), level, blockPos, context.mlua().registration());
      }));
      bindWorldMethod(table, implementations, "GetLightLevel", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return LuaValue.valueOf(level.getMaxLocalRawBrightness(blockPos));
      }));
      bindWorldMethod(table, implementations, "GetSkyLight", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return LuaValue.valueOf(level.getBrightness(LightLayer.SKY, blockPos));
      }));
      bindWorldMethod(table, implementations, "GetBlockLight", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return LuaValue.valueOf(level.getBrightness(LightLayer.BLOCK, blockPos));
      }));
      bindWorldMethod(table, implementations, "CanSeeSky", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return LuaValue.valueOf(level.canSeeSky(blockPos));
      }));
      bindWorldMethod(table, implementations, "IsRaining", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isRaining())));
      bindWorldMethod(table, implementations, "IsThundering", levelFn(supplier, (level, args) -> LuaValue.valueOf(level.isThundering())));
      bindWorldMethod(table, implementations, "IsRainingAt", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, blockPos)) return LuaValue.NIL;
         return LuaValue.valueOf(level.isRainingAt(blockPos));
      }));
      bindWorldMethod(table, implementations, "GetSurfaceY", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos column = BlockPos.containing(pos[0], level.getMinBuildHeight(), pos[2]);
         if (!MLuaChunkWrapper.ensureLoaded(level, column)) return LuaValue.NIL;
         return LuaValue.valueOf(level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()));
      }));
      bindWorldMethod(table, implementations, "Raycast", levelFn(supplier, (level, args) -> {
         double[] origin = MLuaPositionArguments.vector3(args, 2);
         double[] direction = MLuaVector.components3(args.arg(3));
         double distance = args.optdouble(4, 100.0);
         if (!Double.isFinite(distance) || distance < 0.0) throw new LuaError("[MLua] World:Raycast distance must be finite and non-negative.");
         for (double component : origin) {
            if (!Double.isFinite(component)) throw new LuaError("[MLua] World:Raycast origin must be finite.");
         }
         for (double component : direction) {
            if (!Double.isFinite(component)) throw new LuaError("[MLua] World:Raycast direction must be finite.");
         }
         LuaValue fluidArgument = args.arg(5);
         if (!fluidArgument.isnil() && !fluidArgument.isboolean()) throw new LuaError("[MLua] World:Raycast includeFluids must be a boolean.");
         boolean includeFluids = fluidArgument.toboolean();
         Vec3 from = new Vec3(origin[0], origin[1], origin[2]);
         double directionScale = Math.max(Math.abs(direction[0]), Math.max(Math.abs(direction[1]), Math.abs(direction[2])));
         if (directionScale == 0.0) return LuaValue.NIL;
         Vec3 dir = new Vec3(direction[0] / directionScale, direction[1] / directionScale, direction[2] / directionScale).normalize();
         Vec3 to = from.add(dir.scale(distance));
         if (!Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)) throw new LuaError("[MLua] World:Raycast endpoint must be finite.");
         BlockHitResult hitResult = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, (Entity)null));
         if (hitResult.getType() == HitResult.Type.MISS) {
            return LuaValue.NIL;
         }

         LuaTable result = new LuaTable();
         result.set("Type", LuaValue.valueOf("block"));
         result.set("Position", MLuaPlayerWrapper.positionTable(hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z));
         result.set("Block", LuaValue.valueOf(String.valueOf(BuiltInRegistries.BLOCK.getKey(level.getBlockState(hitResult.getBlockPos()).getBlock()))));
         result.set("Face", LuaValue.valueOf(hitResult.getDirection().getName()));
         return result;
      }));
      bindWorldMethod(table, implementations, "RaycastEntity", levelFn(supplier, (level, args) -> {
         double[] origin = MLuaPositionArguments.vector3(args, 2);
         double[] direction = MLuaVector.components3(args.arg(3));
         double distance = MLuaLuaArguments.optionalFiniteNumber(args, 4, 100.0,
            "World:RaycastEntity", "distance");
         if (distance < 0) throw new LuaError("[MLua] World:RaycastEntity distance must be non-negative.");
         requireFinite(origin, "World:RaycastEntity origin");
         requireFinite(direction, "World:RaycastEntity direction");
         Vec3 from = new Vec3(origin[0], origin[1], origin[2]);
         double scale = Math.max(Math.abs(direction[0]), Math.max(Math.abs(direction[1]), Math.abs(direction[2])));
         if (scale == 0) return LuaValue.NIL;
         Vec3 rayDirection = new Vec3(direction[0] / scale, direction[1] / scale, direction[2] / scale).normalize();
         Vec3 to = from.add(rayDirection.scale(distance));
         requireFinite(new double[]{to.x, to.y, to.z}, "World:RaycastEntity endpoint");
         BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, (Entity)null));
         double nearestDistance = blockHit.getType() == HitResult.Type.MISS
            ? Double.POSITIVE_INFINITY : from.distanceToSqr(blockHit.getLocation());
         AABB search = new AABB(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
            Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).inflate(1.0);
         Entity nearest = null;
         Vec3 nearestHit = null;
         Vec3 nearestNormal = null;
         LuaValue filter = args.arg(5);
         MLuaEntityQuery.Filter entityFilter = MLuaEntityQuery.compileFilter(filter, context.mlua().registration());
         for (Entity candidate : level.getEntities((Entity)null, search)) {
            if (candidate.isRemoved() || !candidate.isPickable() || !entityFilter.matches(candidate)) continue;
            AABB box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
            var hit = box.clip(from, to);
            if (hit.isEmpty()) continue;
            double hitDistance = from.distanceToSqr(hit.get());
            if (hitDistance < nearestDistance) {
               nearest = candidate;
               nearestHit = hit.get();
               nearestNormal = MLuaEntityBounds.rayHitNormal(box, nearestHit);
               nearestDistance = hitDistance;
            }
         }
         if (nearest == null) return LuaValue.NIL;
         LuaTable result = context.mlua().registration().createClassInstance("EntityRaycastResult");
         result.set("HitType", LuaValue.valueOf("entity"));
         result.set("Type", LuaValue.valueOf("entity"));
         result.set("Entity", wrapEntity(nearest));
         result.set("Position", MLuaVector.vector3(nearestHit.x, nearestHit.y, nearestHit.z));
         result.set("Normal", MLuaVector.vector3(nearestNormal.x, nearestNormal.y, nearestNormal.z));
         result.set("Distance", LuaValue.valueOf(Math.sqrt(nearestDistance)));
         return result;
      }));
      bindWorldMethod(table, implementations, "RaycastAABB", levelFn(supplier, (level, args) -> {
         double[] origin = MLuaPositionArguments.vector3(args, 2);
         double[] direction = MLuaVector.components3(args.arg(3));
         LuaValue distanceOrBounds = args.arg(4);
         double distance = distanceOrBounds.istable() ? 100.0 : MLuaLuaArguments.optionalFiniteNumber(
            args, 4, 100.0, "World:RaycastAABB", "distance");
         if (distance < 0) throw new LuaError("[MLua] World:RaycastAABB distance must be non-negative.");
         requireFinite(origin, "World:RaycastAABB origin");
         requireFinite(direction, "World:RaycastAABB direction");
         LuaValue list = distanceOrBounds.istable() ? distanceOrBounds : args.arg(5);
         if (!list.istable()) throw new LuaError("[MLua] World:RaycastAABB expects an EntityBounds array.");
         Vec3 from = new Vec3(origin[0], origin[1], origin[2]);
         double scale = Math.max(Math.abs(direction[0]), Math.max(Math.abs(direction[1]), Math.abs(direction[2])));
         if (scale == 0) return LuaValue.NIL;
         Vec3 unit = new Vec3(direction[0] / scale, direction[1] / scale, direction[2] / scale).normalize();
         Vec3 to = from.add(unit.scale(distance));
         requireFinite(new double[]{to.x, to.y, to.z}, "World:RaycastAABB endpoint");
         LuaTable boundsList = list.checktable();
         AABB nearestBounds = null;
         Vec3 nearestHit = null;
         int nearestIndex = -1;
         double nearestDistance = Double.POSITIVE_INFINITY;
         for (int index = 1; index <= boundsList.length(); index++) {
            AABB bounds = MLuaEntityBounds.fromLua(boundsList.get(index));
            var hit = bounds.clip(from, to);
            if (hit.isEmpty()) continue;
            double hitDistance = from.distanceToSqr(hit.get());
            if (hitDistance < nearestDistance) {
               nearestBounds = bounds;
               nearestHit = hit.get();
               nearestIndex = index;
               nearestDistance = hitDistance;
            }
         }
         if (nearestBounds == null) return LuaValue.NIL;
         LuaTable result = context.mlua().registration().createClassInstance("BoundsRaycastResult");
         result.set("HitType", LuaValue.valueOf("bounds"));
         result.set("Type", LuaValue.valueOf("bounds"));
         result.set("Position", MLuaVector.vector3(nearestHit.x, nearestHit.y, nearestHit.z));
         Vec3 normal = MLuaEntityBounds.rayHitNormal(nearestBounds, nearestHit);
         result.set("Normal", MLuaVector.vector3(normal.x, normal.y, normal.z));
         result.set("Distance", LuaValue.valueOf(Math.sqrt(nearestDistance)));
         result.set("Bounds", boundsList.get(nearestIndex));
         result.set("BoundsIndex", LuaValue.valueOf(nearestIndex));
         return result;
      }));

      bindWorldMethod(table, implementations, "GetPlayers", levelFn(supplier, (level, args) -> this.playersTable(level)));
      bindWorldMethod(table, implementations, "GetEntities", levelFn(supplier, (level, args) -> this.entitiesTable(level, null, LuaValue.NIL)));
      bindWorldMethod(table, implementations, "FindEntitiesInRadius", levelFn(supplier, (level, args) -> {
         double[] center = MLuaPositionArguments.vector3(args, 2);
         double radius = MLuaLuaArguments.finiteNonNegativeNumber(args, 3,
            "World:FindEntitiesInRadius", "radius");
         requireFinite(center, "World:FindEntitiesInRadius center");
         return this.entitiesInRadiusTable(level, center, radius, args.arg(4));
      }));
      bindWorldAlias(table, implementations, "GetEntitiesInRadius", "FindEntitiesInRadius");
      bindWorldAlias(table, implementations, "GetEntitiesNearby", "FindEntitiesInRadius");
      bindWorldMethod(table, implementations, "FindEntitiesInBox", levelFn(supplier, (level, args) -> {
         double[] min = MLuaPositionArguments.vector3(args, 2);
         double[] max = MLuaPositionArguments.vector3(args, 3);
         requireFinite(min, "World:FindEntitiesInBox minimum");
         requireFinite(max, "World:FindEntitiesInBox maximum");
         for (int axis = 0; axis < 3; axis++) {
            if (min[axis] > max[axis]) {
               throw new LuaError("[MLua] World:FindEntitiesInBox minimum coordinates must not exceed maximum coordinates.");
            }
         }
         AABB box = new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
         return this.entitiesTable(level, box, args.arg(4));
      }));
      bindWorldMethod(table, implementations, "FindEntitiesInBounds", levelFn(supplier, (level, args) ->
         this.entitiesTable(level, MLuaEntityBounds.fromLua(args.arg(2)), args.arg(3))));
      bindWorldMethod(table, implementations, "FindPlayersInBounds", levelFn(supplier, (level, args) ->
         this.playersTable(level, MLuaEntityBounds.fromLua(args.arg(2)))));
      bindWorldMethod(table, implementations, "FindPlayersInRadius", levelFn(supplier, (level, args) -> {
         double[] center = MLuaPositionArguments.vector3(args, 2);
         double radius = MLuaLuaArguments.finiteNonNegativeNumber(args, 3,
            "World:FindPlayersInRadius", "radius");
         requireFinite(center, "World:FindPlayersInRadius center");
         return this.playersInRadiusTable(level, center, radius);
      }));
      bindWorldAlias(table, implementations, "GetPlayersNearby", "FindPlayersInRadius");
      bindWorldMethod(table, implementations, "FindNearestPlayer", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         requireFinite(pos, "World:FindNearestPlayer position");
         double maxDistance = MLuaLuaArguments.optionalFiniteNumber(args, 3, Double.MAX_VALUE,
            "World:FindNearestPlayer", "maxDistance");
         if (maxDistance < 0) throw new LuaError("[MLua] World:FindNearestPlayer maxDistance must be non-negative.");
         Player nearest = null;
         double nearestDistance = maxDistance * maxDistance;
         for (Player player : level.players()) {
            double distance = player.distanceToSqr(pos[0], pos[1], pos[2]);
            if (distance <= nearestDistance) {
               nearestDistance = distance;
               nearest = player;
            }
         }

         return nearest == null ? LuaValue.NIL
            : MLuaPlayerWrapper.playerEntityTable(nearest, this.context.mlua().registration());
      }));
      bindWorldAlias(table, implementations, "GetNearestPlayer", "FindNearestPlayer");
      bindWorldMethod(table, implementations, "FindNearestEntity", levelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         requireFinite(pos, "World:FindNearestEntity position");
         double maxDistance = MLuaLuaArguments.optionalFiniteNumber(args, 3, Double.MAX_VALUE,
            "World:FindNearestEntity", "maxDistance");
         if (maxDistance < 0) throw new LuaError("[MLua] World:FindNearestEntity maxDistance must be non-negative.");
         LuaValue filter = args.arg(4);
         MLuaEntityQuery.Filter entityFilter = MLuaEntityQuery.compileFilter(filter, context.mlua().registration());
         Entity nearest = null;
         double nearestDistance = maxDistance * maxDistance;
         for (Entity entity : MLuaEntityQuery.loadedEntities(level)) {
            if (!entityFilter.matches(entity)) {
               continue;
            }
            double distance = entity.distanceToSqr(pos[0], pos[1], pos[2]);
            if (Double.isFinite(distance) && distance <= nearestDistance) {
               nearestDistance = distance;
               nearest = entity;
            }
         }

         return wrapEntity(nearest);
      }));

      bindWorldMethod(table, implementations, "SetBlock", serverLevelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
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
      bindWorldMethod(table, implementations, "SetBlockState", serverLevelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         BlockPos blockPos = BlockPos.containing(pos[0], pos[1], pos[2]);
         BlockState state =
            MLuaBlockStateWrapper.blockStateFromLua(args.arg(3), context.mlua().registration());
         LuaValue loadArgument = args.arg(4);
         if (!loadArgument.isnil() && !loadArgument.isboolean() && !loadArgument.isnumber()) {
            throw new LuaError("[MLua] World:SetBlockState loadChunk must be a boolean or update flags must be a number.");
         }
         boolean loadChunk = !loadArgument.isboolean() || loadArgument.toboolean();
         LuaValue flagsArgument = loadArgument.isboolean() ? args.arg(5) : loadArgument;
         int updateFlags = MLuaBlockUpdateFlags.read(flagsArgument, "World:SetBlockState");
         if (!level.hasChunkAt(blockPos)) {
            if (!loadChunk) return LuaValue.FALSE;
            level.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
         }
         return LuaValue.valueOf(level.setBlock(blockPos, state, updateFlags));
      }));
      bindWorldMethod(table, implementations, "BreakBlock", serverLevelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         boolean dropItems = args.optboolean(3, true);
         return LuaValue.valueOf(level.destroyBlock(BlockPos.containing(pos[0], pos[1], pos[2]), dropItems));
      }));
      bindWorldAlias(table, implementations, "DestroyBlock", "BreakBlock");
      bindWorldMethod(table, implementations, "PlaySound", serverLevelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
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
         double[] pos = MLuaPositionArguments.vector3(args, 2);
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
         double[] pos = MLuaPositionArguments.vector3(args, 3);
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
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         String itemId = args.checkjstring(3);
         int count = args.optint(4, 1);
         Item item = BuiltInRegistries.ITEM.get(MLuaResourceLocations.parse(itemId));
         if (item == null) {
            throw new LuaError("[MLua] Unknown item: " + itemId);
         }
         ItemEntity entity = new ItemEntity(level, pos[0], pos[1], pos[2], new ItemStack(item, Math.max(1, count)));
         level.addFreshEntity(entity);
         return wrapEntity(entity);
      }));
      bindWorldMethod(table, implementations, "Explode", serverLevelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         float power = (float)args.optdouble(3, 4.0);
         boolean fire = args.optboolean(4, false);
         level.explode((Entity)null, pos[0], pos[1], pos[2], power, fire, Level.ExplosionInteraction.BLOCK);
         return LuaValue.NONE;
      }));
      bindWorldMethod(table, implementations, "SpawnLightning", serverLevelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
         Entity lightning = EntityType.LIGHTNING_BOLT.create(level);
         if (lightning == null) {
            throw new LuaError("[MLua] Failed to create lightning entity");
         }
         lightning.setPos(pos[0], pos[1], pos[2]);
         level.addFreshEntity(lightning);
         return wrapEntity(lightning);
      }));

      bindWorldMethod(table, implementations, "GetBlockInventory", serverLevelFn(supplier, (level, args) -> {
         double[] pos = MLuaPositionArguments.vector3(args, 2);
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
      bindWorldMethod(table, implementations, "GetGameRuleEntry", levelFn(supplier, (level, args) -> this.getGameRuleEntry(level, args.checkjstring(2))));
      bindWorldMethod(table, implementations, "SetGameRule", serverLevelFn(supplier, (level, args) -> {
         this.setGameRule(level, args.checkjstring(2), args.arg(3));
         return LuaValue.NONE;
      }));

      MLuaWorldChunkApi.bind(table, context.mlua().registration());
      if (initial != null) this.worldInstances.put(initial, table);
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

      if (this.context.mlua().server() != null) {
         for (ServerLevel level : this.context.mlua().server().getAllLevels()) {
            if (level.dimension().location().equals(target)) {
               return level;
            }
         }
      }

      Level clientLevel = clientLevel();
      return clientLevel != null && clientLevel.dimension().location().equals(target) ? clientLevel : null;
   }

   private LuaTable playersTable(Level level) {
      return this.playersTable(level, null);
   }

   private LuaTable playersTable(Level level, AABB box) {
      return MLuaWorldEntityQueries.players(level, box, this.context.mlua().registration());
   }

   private LuaTable playersInRadiusTable(Level level, double[] center, double radius) {
      return MLuaWorldEntityQueries.playersInRadius(level, center, radius, this.context.mlua().registration());
   }

   private LuaTable entitiesTable(Level level, AABB box, LuaValue filter) {
      return MLuaWorldEntityQueries.entities(level, box, filter, this.context.mlua().registration());
   }

   private LuaTable entitiesInRadiusTable(Level level, double[] center, double radius, LuaValue filter) {
      return MLuaWorldEntityQueries.entitiesInRadius(level, center, radius, filter, this.context.mlua().registration());
   }

   private static void requireFinite(double[] values, String parameter) {
      for (double value : values) {
         if (!Double.isFinite(value)) throw new LuaError("[MLua] " + parameter + " must contain finite coordinates.");
      }
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
      return MLuaWorldGameRules.list(level, this.context.mlua().registration(),
         this.context.mlua()::server);
   }

   private LuaValue getGameRule(Level level, String name) {
      return MLuaWorldGameRules.get(level, name);
   }

   private LuaValue getGameRuleEntry(Level level, String name) {
      return MLuaWorldGameRules.entry(level, name, this.context.mlua().registration(),
         this.context.mlua()::server);
   }

   private void setGameRule(ServerLevel level, String name, LuaValue value) {
      MLuaWorldGameRules.set(level, name, value, this.context.mlua().server());
   }

   private LuaValue wrapEntity(Entity entity) {
      return entity == null ? LuaValue.NIL
         : MLuaEntityWrapper.entityTable(entity, this.context.registration());
   }

   private LuaValue levelFn(Supplier<Level> supplier, LevelFunction fn) {
      return varArg((args) -> {
         Level level = supplier.get();
         if (level == null) return LuaValue.NIL;
         if (level instanceof ServerLevel serverLevel) {
            return MLuaServerThread.call(serverLevel.getServer(), () -> fn.apply(serverLevel, args));
         }
         return fn.apply(level, args);
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
         return MLuaServerThread.call(serverLevel.getServer(), () -> fn.apply(serverLevel, args));
      });
   }

   private LuaValue varArg(Function<Varargs, LuaValue> fn) {
      return new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            try {
               MinecraftServer server = context.mlua().server();
               if (context.mlua().side() == Side.SERVER && server != null && !server.isSameThread()) {
                  return MLuaServerThread.call(server, () -> fn.apply(args));
               }
               return fn.apply(args);
            } catch (LuaError e) {
               throw e;
            } catch (Exception e) {
               throw MLuaLuaErrors.javaFailure("World service call", e);
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
