package dev.mlua.modules.players.classes;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaTable;

/** Owns player-to-player spatial query bindings. */
final class MLuaPlayerRelationshipBindings {
   private MLuaPlayerRelationshipBindings() { }

   static void bindNearbyPlayers(LuaTable table,
                                           Player player,
                                           MLuaRegistration registration) {
      MLuaClassMethods.setInstanceMethod(table, "GetNearbyPlayers", MLuaClassMethods.varArg(args -> {
         double radius = MLuaLuaArguments.finiteNonNegativeNumber(args, 2, "Player:GetNearbyPlayers", "radius");
         AABB bounds = player.getBoundingBox().inflate(radius);
         LuaTable nearby = new LuaTable();
         int index = 1;
         for (Player other : player.level().getEntitiesOfClass(
               Player.class, bounds, candidate -> candidate != player)) {
            nearby.set(index++, MLuaPlayerWrapper.playerEntityTable(other, registration));
         }
         return nearby;
      }));
   }


}
