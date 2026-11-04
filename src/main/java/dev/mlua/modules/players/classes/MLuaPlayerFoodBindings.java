package dev.mlua.modules.players.classes;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.mcapi.entity.MCPlayer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns Player food level, saturation, and exhaustion methods. */
final class MLuaPlayerFoodBindings {
   private MLuaPlayerFoodBindings() { }

   static void bind(LuaTable table, MCPlayer player) {
      LuaValue getFoodLevel = MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(player.getFoodData().getFoodLevel()));
      LuaValue setFoodLevel = MLuaClassMethods.varArg(args -> {
         setFoodLevel(player, args.arg(2));
         return LuaValue.NONE;
      });
      MLuaClassMethods.setInstanceMethod(table, "GetFoodLevel", getFoodLevel,
         "Returns this player's food level.");
      MLuaClassMethods.setInstanceMethod(table, "GetFood", getFoodLevel,
         "Returns this player's food level.");
      MLuaClassMethods.setInstanceMethod(table, "SetFoodLevel", setFoodLevel,
         "Sets this player's food level to a whole number from 0 to 20.");
      MLuaClassMethods.setInstanceMethod(table, "SetFood", setFoodLevel,
         "Sets this player's food level to a whole number from 0 to 20.");
      MLuaClassMethods.setInstanceMethod(table, "GetSaturation", MLuaClassMethods.zeroArg(() ->
         LuaValue.valueOf((double) player.getFoodData().getSaturationLevel())),
         "Returns this player's food saturation.");
      MLuaClassMethods.setInstanceMethod(table, "SetSaturation", MLuaClassMethods.varArg(args -> {
         setSaturation(player, args.arg(2));
         return LuaValue.NONE;
      }), "Sets this player's saturation to a finite, non-negative value.");
      MLuaClassMethods.setInstanceMethod(table, "CauseFoodExhaustion", MLuaClassMethods.varArg(args -> {
         addFoodExhaustion(player, args.arg(2));
         return LuaValue.NONE;
      }), "Adds food exhaustion to this player; server-side only for authoritative changes.");
   }

   private static void setFoodLevel(MCPlayer player, LuaValue value) {
      if (!value.isnumber()) throw new LuaError("[MLua] Player food level must be a whole number from 0 to 20.");
      double level = value.todouble();
      if (!Double.isFinite(level) || level != Math.rint(level) || level < 0 || level > 20) {
         throw new LuaError("[MLua] Player food level must be a whole number from 0 to 20.");
      }
      player.getFoodData().setFoodLevel((int)level);
   }

   private static void setSaturation(MCPlayer player, LuaValue value) {
      player.getFoodData().setSaturation(finiteNonNegativeFoodValue(value, "saturation"));
   }

   private static void addFoodExhaustion(MCPlayer player, LuaValue value) {
      player.getFoodData().addExhaustion(finiteNonNegativeFoodValue(value, "exhaustion"));
   }

   private static float finiteNonNegativeFoodValue(LuaValue value, String name) {
      if (!value.isnumber()) throw new LuaError("[MLua] Player food " + name + " must be finite and non-negative.");
      double number = value.todouble();
      if (!Double.isFinite(number) || number < 0 || number > Float.MAX_VALUE) {
         throw new LuaError("[MLua] Player food " + name + " must be finite and non-negative.");
      }
      return (float)number;
   }


}
