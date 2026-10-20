package dev.mlua.modules.world.service;

import dev.mlua.modules.entity.MLuaEntityBounds;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.math.MLuaVector;
import dev.mlua.module.MLuaRegistration;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds block, entity, and custom-bounds raycasts for a live world instance. */
final class MLuaWorldRaycastApi {
   private MLuaWorldRaycastApi() {}

   static void bind(LuaTable table, Supplier<Level> supplier, MLuaRegistration registration) {
      table.rawset("Raycast", MLuaWorldApiSupport.levelFn(supplier, (level, args) -> {
         double[] origin = MLuaPositionArguments.vector3(args, 2);
         int directionIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         double[] direction = MLuaVector.components3(args.arg(directionIndex));
         int distanceIndex = directionIndex + MLuaPositionArguments.argumentWidth(args, directionIndex);
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
         if (hitResult.getType() == HitResult.Type.MISS) {
            return LuaValue.NIL;
         }

         LuaTable result = registration.createClassInstance("BlockRaycastResult");
         result.rawset("HitType", LuaValue.valueOf("block"));
         result.rawset("Type", LuaValue.valueOf("block"));
         result.rawset("Position", MLuaVector.vector3(hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z));
         result.rawset("Normal", MLuaVector.vector3(hitResult.getDirection().getStepX(), hitResult.getDirection().getStepY(), hitResult.getDirection().getStepZ()));
         result.rawset("Distance", LuaValue.valueOf(Math.sqrt(from.distanceToSqr(hitResult.getLocation()))));
         result.rawset("Block", LuaValue.valueOf(String.valueOf(BuiltInRegistries.BLOCK.getKey(level.getBlockState(hitResult.getBlockPos()).getBlock()))));
         result.rawset("Face", LuaValue.valueOf(hitResult.getDirection().getName()));
         result.rawset("BlockInstance", MLuaBlockWrapper.blockTable(level, hitResult.getBlockPos(), registration));
         result.freeze();
         return result;
      }));
      table.rawset("RaycastEntity", MLuaWorldApiSupport.levelFn(supplier, (level, args) -> {
         double[] origin = MLuaPositionArguments.vector3(args, 2);
         int directionIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         double[] direction = MLuaVector.components3(args.arg(directionIndex));
         int distanceIndex = directionIndex + MLuaPositionArguments.argumentWidth(args, directionIndex);
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
         double nearestDistance = blockHit.getType() == HitResult.Type.MISS
            ? Double.POSITIVE_INFINITY : from.distanceToSqr(blockHit.getLocation());
         AABB search = new AABB(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
            Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).inflate(1.0);
         LuaValue filter = args.arg(distanceIndex + 1);
         MLuaEntityQuery.Filter entityFilter = MLuaEntityQuery.compileFilter(filter, registration);
         Entity nearest = null;
         Vec3 nearestHit = null;
         Vec3 nearestNormal = null;
         for (Entity candidate : level.getEntities((Entity)null, search)) {
            if (candidate.isRemoved() || !candidate.isPickable() || !entityFilter.matches(candidate)) continue;
            AABB hitBox = candidate.getBoundingBox().inflate(candidate.getPickRadius());
            var hit = hitBox.clip(from, to);
            if (hit.isEmpty()) continue;
            double hitDistance = from.distanceToSqr(hit.get());
            if (hitDistance < nearestDistance) {
               nearest = candidate;
               nearestHit = hit.get();
               nearestNormal = MLuaEntityBounds.rayHitNormal(hitBox, nearestHit);
               nearestDistance = hitDistance;
            }
         }
         if (nearest == null) return LuaValue.NIL;
         LuaTable result = registration.createClassInstance("EntityRaycastResult");
         result.rawset("HitType", LuaValue.valueOf("entity"));
         result.rawset("Type", LuaValue.valueOf("entity"));
         result.rawset("Entity", MLuaEntityWrapper.entityTable(nearest, registration));
         result.rawset("Position", MLuaVector.vector3(nearestHit.x, nearestHit.y, nearestHit.z));
         result.rawset("Normal", MLuaVector.vector3(nearestNormal.x, nearestNormal.y, nearestNormal.z));
         result.rawset("Distance", LuaValue.valueOf(Math.sqrt(nearestDistance)));
         result.freeze();
         return result;
      }));
      table.rawset("RaycastAABB", MLuaWorldApiSupport.levelFn(supplier, (level, args) -> {
         double[] origin = MLuaPositionArguments.vector3(args, 2);
         int directionIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         double[] direction = MLuaVector.components3(args.arg(directionIndex));
         int distanceIndex = directionIndex + MLuaPositionArguments.argumentWidth(args, directionIndex);
         LuaValue distanceOrBounds = args.arg(distanceIndex);
         double distance = distanceOrBounds.istable() ? 100.0 : MLuaLuaArguments.optionalFiniteNumber(
            args, distanceIndex, 100.0, "World:RaycastAABB", "distance");
         if (!Double.isFinite(distance) || distance < 0) {
            throw new LuaError("[MLua] World:RaycastAABB distance must be finite and non-negative.");
         }
         for (double component : origin) if (!Double.isFinite(component))
            throw new LuaError("[MLua] World:RaycastAABB origin must be finite.");
         for (double component : direction) if (!Double.isFinite(component))
            throw new LuaError("[MLua] World:RaycastAABB direction must be finite.");
         LuaValue listValue = distanceOrBounds.istable() ? distanceOrBounds : args.arg(distanceIndex + 1);
         if (!listValue.istable()) throw new LuaError("[MLua] World:RaycastAABB bounds must be an array of EntityBounds.");
         Vec3 from = new Vec3(origin[0], origin[1], origin[2]);
         double scale = Math.max(Math.abs(direction[0]), Math.max(Math.abs(direction[1]), Math.abs(direction[2])));
         if (scale == 0) return LuaValue.NIL;
         Vec3 rayDirection = new Vec3(direction[0] / scale, direction[1] / scale, direction[2] / scale).normalize();
         Vec3 to = from.add(rayDirection.scale(distance));
         if (!Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z))
            throw new LuaError("[MLua] World:RaycastAABB endpoint must be finite.");
         LuaTable boundsList = listValue.checktable();
         AABB nearestBounds = null;
         Vec3 nearestHit = null;
         int nearestIndex = -1;
         double nearestDistance = Double.POSITIVE_INFINITY;
         for (int index = 1; index <= boundsList.length(); index++) {
            LuaValue boundsValue = boundsList.get(index);
            AABB bounds = MLuaEntityBounds.fromLua(boundsValue);
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
         LuaTable result = registration.createClassInstance("BoundsRaycastResult");
         result.rawset("HitType", LuaValue.valueOf("bounds"));
         result.rawset("Type", LuaValue.valueOf("bounds"));
         result.rawset("Position", MLuaVector.vector3(nearestHit.x, nearestHit.y, nearestHit.z));
         Vec3 normal = MLuaEntityBounds.rayHitNormal(nearestBounds, nearestHit);
         result.rawset("Normal", MLuaVector.vector3(normal.x, normal.y, normal.z));
         result.rawset("Distance", LuaValue.valueOf(Math.sqrt(nearestDistance)));
         result.rawset("Bounds", boundsList.get(nearestIndex));
         result.rawset("BoundsIndex", LuaValue.valueOf(nearestIndex));
         result.freeze();
         return result;
      }));

   }
}
