package dev.mlua.modules.players.classes;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Registers native attack, kill, removal, and distance operations for entities. */
final class MLuaEntityActionBindings {
   private MLuaEntityActionBindings() { }

   static void bind(LuaTable table, MCEntity entity) {
      Entity raw = ((MCEntityImpl)entity).inner();
      MLuaClassMethods.setInstanceMethod(table, "Attack", MLuaClassMethods.zeroArg(() -> {
         if (!raw.level().isClientSide) {
            throw new LuaError("[MLua] Entity:Attack is client-side only; use server-side combat callbacks or entity AI on the server.");
         }
         return LuaValue.valueOf(dev.mlua.minecraft.client.MLuaClientEntityActions.attack(raw));
      }), "Requests a normal local-player attack against this entity. The active client, game mode, and server validate the hit.");
      MLuaClassMethods.setInstanceMethod(table, "attack", MLuaClassMethods.zeroArg(() -> {
         if (!raw.level().isClientSide) {
            throw new LuaError("[MLua] Entity:attack is client-side only; use server-side combat callbacks or entity AI on the server.");
         }
         return LuaValue.valueOf(dev.mlua.minecraft.client.MLuaClientEntityActions.attack(raw));
      }), "Requests a normal local-player attack against this entity through Minecraft's active game mode.");
      MLuaClassMethods.setInstanceMethod(table, "Kill", MLuaClassMethods.zeroArg(() -> {
         ServerLevel serverLevel = requireServerEntityWorld(raw, "Kill");
         //--version 26.1
         raw.kill(serverLevel);
         //--endversion
         //--version 1.20.1,1.21.1
         raw.kill();
         //--endversion
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "Remove", MLuaClassMethods.zeroArg(() -> {
         requireServerEntityWorld(raw, "Remove");
         raw.remove(Entity.RemovalReason.DISCARDED);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "DistanceTo", MLuaClassMethods.varArg(args -> MLuaEntityWrapper.distanceTo(entity, args.arg(2))));
   }

   private static ServerLevel requireServerEntityWorld(Entity entity, String operation) {
      if (entity.level() instanceof ServerLevel serverLevel) return serverLevel;
      throw new LuaError("[MLua] Entity:" + operation + " is only available for entities in a server world.");
   }





}
