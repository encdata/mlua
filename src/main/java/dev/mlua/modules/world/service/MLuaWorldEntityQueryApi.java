package dev.mlua.modules.world.service;

import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.entity.MLuaEntityBounds;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.module.MLuaRegistration;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds world entity search methods and owns their argument validation. */
final class MLuaWorldEntityQueryApi {
   private MLuaWorldEntityQueryApi() {}

   static void bind(LuaTable table, Supplier<Level> supplier, MLuaRegistration registration) {
      table.rawset("GetPlayers", MLuaWorldApiSupport.levelFn(supplier,
         (level, args) -> MLuaWorldEntityQueries.players(level, null, registration)));
      table.rawset("GetEntities", MLuaWorldApiSupport.levelFn(supplier,
         (level, args) -> MLuaWorldEntityQueries.entities(level, null, LuaValue.NIL, registration)));
      table.rawset("FindEntitiesInRadius", MLuaWorldApiSupport.levelFn(supplier, (level, args) -> {
         double[] center = checkedPosition(MLuaPositionArguments.vector3(args, 2));
         int radiusIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         double radius = checkedRadius(args.checkdouble(radiusIndex));
         return MLuaWorldEntityQueries.entitiesInRadius(level, center, radius, args.arg(radiusIndex + 1), registration);
      }));
      table.rawset("GetEntitiesInRadius", table.get("FindEntitiesInRadius"));
      table.rawset("GetEntitiesNearby", table.get("FindEntitiesInRadius"));
      table.rawset("FindEntitiesInBox", MLuaWorldApiSupport.levelFn(supplier, (level, args) -> {
         double[] min = checkedPosition(MLuaPositionArguments.vector3(args, 2));
         int maxIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         double[] max = checkedPosition(MLuaPositionArguments.vector3(args, maxIndex));
         AABB box = checkedBox(min, max);
         return MLuaWorldEntityQueries.entities(level, box, args.arg(maxIndex +
            MLuaPositionArguments.argumentWidth(args, maxIndex)), registration);
      }));
      table.rawset("FindEntitiesInBounds", MLuaWorldApiSupport.levelFn(supplier, (level, args) ->
         MLuaWorldEntityQueries.entities(level, MLuaEntityBounds.fromLua(args.arg(2)), args.arg(3), registration)));
      table.rawset("FindPlayersInBounds", MLuaWorldApiSupport.levelFn(supplier, (level, args) ->
         MLuaWorldEntityQueries.players(level, MLuaEntityBounds.fromLua(args.arg(2)), registration)));
      table.rawset("FindPlayersInRadius", MLuaWorldApiSupport.levelFn(supplier, (level, args) -> {
         double[] center = checkedPosition(MLuaPositionArguments.vector3(args, 2));
         double radius = checkedRadius(args.checkdouble(2 + MLuaPositionArguments.argumentWidth(args, 2)));
         return MLuaWorldEntityQueries.playersInRadius(level, center, radius, registration);
      }));
      table.rawset("GetPlayersNearby", table.get("FindPlayersInRadius"));
      table.rawset("FindNearestPlayer", MLuaWorldApiSupport.levelFn(supplier, (level, args) -> {
         double[] pos = checkedPosition(MLuaPositionArguments.vector3(args, 2));
         double maxDistance = MLuaLuaArguments.optionalFiniteNumber(args,
            2 + MLuaPositionArguments.argumentWidth(args, 2), Double.MAX_VALUE,
            "World:FindNearestPlayer", "maxDistance");
         maxDistance = checkedRadius(maxDistance);
         Player nearest = null;
         double nearestDistance = maxDistance * maxDistance;
         for (Player player : level.players()) {
            double distance = player.distanceToSqr(pos[0], pos[1], pos[2]);
            if (distance <= nearestDistance) {
               nearestDistance = distance;
               nearest = player;
            }
         }
         return nearest == null ? LuaValue.NIL : MLuaPlayerWrapper.playerEntityTable(nearest, registration);
      }));
      table.rawset("GetNearestPlayer", table.get("FindNearestPlayer"));
      table.rawset("FindNearestEntity", MLuaWorldApiSupport.levelFn(supplier, (level, args) -> {
         double[] pos = checkedPosition(MLuaPositionArguments.vector3(args, 2));
         int distanceIndex = 2 + MLuaPositionArguments.argumentWidth(args, 2);
         double maxDistance = checkedRadius(MLuaLuaArguments.optionalFiniteNumber(
            args, distanceIndex, Double.MAX_VALUE, "World:FindNearestEntity", "maxDistance"));
         LuaValue filter = args.arg(distanceIndex + 1);
         MLuaEntityQuery.Filter query = MLuaEntityQuery.compileFilter(filter, registration);
         Entity nearest = MLuaEntityQuery.findNearestWithinRadius(level, pos[0], pos[1], pos[2], maxDistance, query);
         return nearest == null ? LuaValue.NIL : MLuaEntityWrapper.entityTable(nearest, registration);
      }));
   }

   private static double checkedRadius(double radius) {
      return MLuaLuaArguments.finiteNonNegativeNumber(radius, "Entity query", "radius");
   }

   private static double[] checkedPosition(double[] position) {
      for (double component : position) {
         if (!Double.isFinite(component)) {
            throw new LuaError("[MLua] Entity query position must contain only finite coordinates.");
         }
      }
      return position;
   }

   private static AABB checkedBox(double[] minimum, double[] maximum) {
      for (int axis = 0; axis < 3; axis++) {
         if (minimum[axis] > maximum[axis]) {
            throw new LuaError("[MLua] Entity query box minimum must not exceed its maximum.");
         }
      }
      return new AABB(minimum[0], minimum[1], minimum[2], maximum[0], maximum[1], maximum[2]);
   }
}
