package dev.mlua.modules.world.service;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.entity.MLuaEntityBounds;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.math.MLuaVector;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.entity.MLuaEntityQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Owns 26.1 world ray traversal, result classes, and argument validation. */
final class MLuaWorldRaycastOperations26 {
    private MLuaWorldRaycastOperations26() { }

    static LuaValue raycastBounds(Level world, Varargs args, MLuaRegistration registration) {
        double[] origin = MLuaPositionArguments.vector3(args, 2);
        double[] direction = MLuaVector.components3(args.arg(3));
        LuaValue distanceOrBounds = args.arg(4);
        double distance = distanceOrBounds.istable() ? 100.0D : args.optdouble(4, 100.0D);
        if (!Double.isFinite(distance) || distance < 0.0D) {
            throw new LuaError("[MLua] World:RaycastAABB distance must be finite and non-negative.");
        }
        requireFinite(origin, "World:RaycastAABB origin");
        requireFinite(direction, "World:RaycastAABB direction");
        LuaValue listValue = distanceOrBounds.istable() ? distanceOrBounds : args.arg(5);
        if (!listValue.istable()) {
            throw new LuaError("[MLua] World:RaycastAABB bounds must be an array of EntityBounds.");
        }

        Vec3 from = new Vec3(origin[0], origin[1], origin[2]);
        double scale = Math.max(Math.abs(direction[0]), Math.max(Math.abs(direction[1]), Math.abs(direction[2])));
        if (scale == 0.0D) return LuaValue.NIL;
        Vec3 rayDirection = new Vec3(direction[0] / scale, direction[1] / scale, direction[2] / scale).normalize();
        Vec3 to = from.add(rayDirection.scale(distance));
        if (!Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)) {
            throw new LuaError("[MLua] World:RaycastAABB endpoint must be finite.");
        }

        LuaTable boundsList = listValue.checktable();
        AABB nearestBounds = null;
        Vec3 nearestHit = null;
        int nearestIndex = -1;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int index = 1; index <= boundsList.length(); index++) {
            LuaValue value = boundsList.get(index);
            AABB bounds = MLuaEntityBounds.fromLua(value);
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
        result.set("HitType", LuaValue.valueOf("bounds"));
        result.set("Type", LuaValue.valueOf("bounds"));
        result.set("Position", MLuaVector.vector3(nearestHit.x, nearestHit.y, nearestHit.z));
        Vec3 normal = MLuaEntityBounds.rayHitNormal(nearestBounds, nearestHit);
        result.set("Normal", MLuaVector.vector3(normal.x, normal.y, normal.z));
        result.set("Distance", LuaValue.valueOf(Math.sqrt(nearestDistance)));
        result.set("Bounds", boundsList.get(nearestIndex));
        result.set("BoundsIndex", LuaValue.valueOf(nearestIndex));
        return result;
    }

    static LuaValue raycastEntity(Level world, Varargs args, MLuaRegistration registration) {
        double[] origin = MLuaPositionArguments.vector3(args, 2);
        double[] direction = MLuaVector.components3(args.arg(3));
        double distance = dev.mlua.minecraft.binding.MLuaLuaArguments.optionalFiniteNumber(
            args, 4, 100.0D, "World:RaycastEntity", "distance");
        if (distance < 0.0D) {
            throw new LuaError("[MLua] World:RaycastEntity distance must be non-negative.");
        }
        requireFinite(origin, "World:RaycastEntity origin");
        requireFinite(direction, "World:RaycastEntity direction");
        Vec3 from = new Vec3(origin[0], origin[1], origin[2]);
        double scale = Math.max(Math.abs(direction[0]), Math.max(Math.abs(direction[1]), Math.abs(direction[2])));
        if (scale == 0.0D) return LuaValue.NIL;
        Vec3 rayDirection = new Vec3(direction[0] / scale, direction[1] / scale, direction[2] / scale).normalize();
        Vec3 to = from.add(rayDirection.scale(distance));
        if (!Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)) {
            throw new LuaError("[MLua] World:RaycastEntity endpoint must be finite.");
        }
        BlockHitResult blockHit = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, (Entity) null));
        double nearestDistance = blockHit.getType() == HitResult.Type.MISS
            ? Double.POSITIVE_INFINITY : from.distanceToSqr(blockHit.getLocation());
        AABB search = new AABB(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
            Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).inflate(1.0D);
        LuaValue filter = args.arg(5);
        MLuaEntityQuery.Filter query = MLuaEntityQuery.compileFilter(filter, registration);
        Entity nearest = null;
        Vec3 nearestHit = null;
        Vec3 nearestNormal = null;
        for (Entity candidate : world.getEntities((Entity) null, search)) {
            if (candidate.isRemoved() || !candidate.isPickable() || !query.matches(candidate)) continue;
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
        result.set("HitType", LuaValue.valueOf("entity"));
        result.set("Type", LuaValue.valueOf("entity"));
        result.set("Entity", MLuaEntityWrapper.entityTable(nearest, registration));
        result.set("Position", MLuaVector.vector3(nearestHit.x, nearestHit.y, nearestHit.z));
        result.set("Normal", MLuaVector.vector3(nearestNormal.x, nearestNormal.y, nearestNormal.z));
        result.set("Distance", LuaValue.valueOf(Math.sqrt(nearestDistance)));
        return result;
    }

    private static void requireFinite(double[] values, String argument) {
        for (double value : values) {
            if (!Double.isFinite(value)) throw new LuaError("[MLua] " + argument + " must be finite.");
        }
    }

}
