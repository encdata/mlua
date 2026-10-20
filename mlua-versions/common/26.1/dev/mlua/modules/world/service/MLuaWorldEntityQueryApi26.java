package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.modules.entity.MLuaEntityBounds;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.modules.world.MLuaPositionArguments;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds loaded player and entity queries on 26.x World instances. */
final class MLuaWorldEntityQueryApi26 {
    private MLuaWorldEntityQueryApi26() { }

    static void bind(MLuaWorldService owner, MLuaService.Context context, LuaTable worldTable,
                     Map<String, LuaValue> implementations, Level level) {
        owner.bindWorldMethod(worldTable, implementations, "GetPlayers", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaWorldEntityQueries.players(world, null, context.mlua().registration())));
        owner.bindWorldMethod(worldTable, implementations, "FindPlayersInRadius", MLuaWorldService.levelFunction(level, (world, args) -> {
            double[] center = MLuaPositionArguments.vector3(args, 2);
            int radiusIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
            double radius = dev.mlua.minecraft.binding.MLuaLuaArguments.finiteNonNegativeNumber(
                args, radiusIndex, "World:FindPlayersInRadius", "radius");
            return MLuaWorldEntityQueries.playersInRadius(world, center, radius, context.mlua().registration());
        }));
        owner.bindWorldAlias(worldTable, implementations, "GetPlayersNearby", "FindPlayersInRadius");
        owner.bindWorldMethod(worldTable, implementations, "FindNearestPlayer", MLuaWorldService.levelFunction(level, (world, args) -> {
            double[] center = MLuaPositionArguments.vector3(args, 2);
            int distanceIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
            double maxDistance = dev.mlua.minecraft.binding.MLuaLuaArguments.optionalFiniteNumber(
                args, distanceIndex, Double.MAX_VALUE, "World:FindNearestPlayer", "maxDistance");
            if (maxDistance < 0.0D) throw new LuaError("[MLua] World:FindNearestPlayer maxDistance must be non-negative.");
            double nearestDistance = maxDistance * maxDistance;
            Player nearestPlayer = null;
            for (Player player : world.players()) {
                double distance = player.distanceToSqr(center[0], center[1], center[2]);
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = player;
                }
            }
            return nearestPlayer == null ? LuaValue.NIL
                : MLuaPlayerWrapper.playerEntityTable(nearestPlayer, context.mlua().registration());
        }));
        owner.bindWorldAlias(worldTable, implementations, "GetNearestPlayer", "FindNearestPlayer");
        owner.bindWorldMethod(worldTable, implementations, "GetEntities", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaWorldEntityQueries.entities(world, null, LuaValue.NIL, context.mlua().registration())));
        owner.bindWorldMethod(worldTable, implementations, "FindEntitiesInRadius", MLuaWorldService.levelFunction(level, (world, args) -> {
            double[] center = MLuaPositionArguments.vector3(args, 2);
            int radiusIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
            double radius = dev.mlua.minecraft.binding.MLuaLuaArguments.finiteNonNegativeNumber(
                args, radiusIndex, "World:FindEntitiesInRadius", "radius");
            return MLuaWorldEntityQueries.entitiesInRadius(world, center, radius, args.arg(radiusIndex + 1), context.mlua().registration());
        }));
        owner.bindWorldAlias(worldTable, implementations, "GetEntitiesInRadius", "FindEntitiesInRadius");
        owner.bindWorldAlias(worldTable, implementations, "GetEntitiesNearby", "FindEntitiesInRadius");
        owner.bindWorldMethod(worldTable, implementations, "FindEntitiesInBox", MLuaWorldService.levelFunction(level, (world, args) -> {
            double[] minimum = MLuaPositionArguments.vector3(args, 2);
            int maximumIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
            double[] maximum = MLuaPositionArguments.vector3(args, maximumIndex);
            int filterIndex = maximumIndex + MLuaPositionArguments.argumentWidth(args, maximumIndex);
            for (int axis = 0; axis < 3; axis++) {
                if (minimum[axis] > maximum[axis]) {
                    throw new LuaError("[MLua] World:FindEntitiesInBox minimum coordinates must not exceed maximum coordinates.");
                }
            }
            return MLuaWorldEntityQueries.entities(world,
                new AABB(minimum[0], minimum[1], minimum[2], maximum[0], maximum[1], maximum[2]),
                args.arg(filterIndex), context.mlua().registration());
        }));
        owner.bindWorldMethod(worldTable, implementations, "FindEntitiesInBounds", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaWorldEntityQueries.entities(world, MLuaEntityBounds.fromLua(args.arg(2)),
                args.arg(3), context.mlua().registration())));
        owner.bindWorldMethod(worldTable, implementations, "FindPlayersInBounds", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaWorldEntityQueries.players(world, MLuaEntityBounds.fromLua(args.arg(2)),
                context.mlua().registration())));
        owner.bindWorldMethod(worldTable, implementations, "FindNearestEntity", MLuaWorldService.levelFunction(level, (world, args) -> {
            double[] center = MLuaPositionArguments.vector3(args, 2);
            int distanceIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
            double maxDistance = dev.mlua.minecraft.binding.MLuaLuaArguments.optionalFiniteNumber(
                args, distanceIndex, Double.MAX_VALUE, "World:FindNearestEntity", "maxDistance");
            if (maxDistance < 0.0D) throw new LuaError("[MLua] World:FindNearestEntity maxDistance must be non-negative.");
            MLuaEntityQuery.Filter query = MLuaEntityQuery.compileFilter(args.arg(distanceIndex + 1), context.mlua().registration());
            Entity nearest = MLuaEntityQuery.findNearestWithinRadius(
                world, center[0], center[1], center[2], maxDistance, query);
            return nearest == null ? LuaValue.NIL
                : MLuaEntityWrapper.entityTable(nearest, context.mlua().registration());
        }));
    }
}
