package dev.mlua.modules.players.classes;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.minecraft.binding.MLuaNbtWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns common entity state, living state, and persistent data bindings. */
final class MLuaEntityStateBindings {
   private static final double MOVEMENT_SPEED_THRESHOLD_SQUARED = 0.0025;
   private static final double VERTICAL_MOVEMENT_THRESHOLD = 0.08;

   private MLuaEntityStateBindings() { }

   static void bindSharedState(LuaTable table, MCEntity entity) {
   
      Entity raw = ((MCEntityImpl)entity).inner();
      MLuaClassMethods.setInstanceMethod(table, "GetIsAlive",
         MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(entity.isAlive())));
      MLuaClassMethods.setInstanceMethod(table, "GetIsOnGround",
         MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(raw.onGround())));
      MLuaClassMethods.setInstanceMethod(table, "GetIsInWater",
         MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(raw.isInWater())));
      MLuaClassMethods.setInstanceMethod(table, "GetIsOnFire",
         MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(raw.isOnFire())));
      MLuaClassMethods.setInstanceMethod(table, "GetIsMoving", MLuaClassMethods.zeroArg(() -> {
         MCVec3 velocity = entity.getDeltaMovement();
         double speedSquared = velocity.x() * velocity.x()
            + velocity.y() * velocity.y()
            + velocity.z() * velocity.z();
         return LuaValue.valueOf(speedSquared > MOVEMENT_SPEED_THRESHOLD_SQUARED);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetIsFalling", MLuaClassMethods.zeroArg(() -> {
         MCVec3 velocity = entity.getDeltaMovement();
         return LuaValue.valueOf(!raw.onGround() && velocity.y() < -VERTICAL_MOVEMENT_THRESHOLD);
      }));
   }

   static void bindLivingState(LuaTable table, LivingEntity entity) {
      MLuaClassMethods.setInstanceMethod(table, "GetIsJumping", MLuaClassMethods.zeroArg(() ->
         LuaValue.valueOf(!entity.onGround() && entity.getDeltaMovement().y > VERTICAL_MOVEMENT_THRESHOLD)));
      MLuaClassMethods.setInstanceMethod(table, "GetIsSwimming", MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(entity.isSwimming())));
   }

   static void bindPersistentData(LuaTable table, Entity entity) {
   
      MLuaClassMethods.setInstanceMethod(table, "GetNbt", MLuaClassMethods.varArg(args -> {
         if (args.narg() < 2 || args.arg(2).isnil()) {
            return MLuaDataManager.entityPersistentData(entity, !entity.level().isClientSide);
         }
         if (entity.level().isClientSide) return LuaValue.NIL;
         return MLuaNbtWrapper.get(MLuaDataManager.entityPersistentTag(entity), args.checkjstring(2));
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetPersistentData",
         MLuaClassMethods.zeroArg(() -> MLuaDataManager.entityPersistentData(entity, !entity.level().isClientSide)));
      MLuaClassMethods.setInstanceMethod(table, "GetSyncedData",
         MLuaClassMethods.zeroArg(() -> MLuaDataManager.entitySyncedData(entity)));
      MLuaClassMethods.setInstanceMethod(table, "getNbt", table.get("GetNbt"));
      MLuaClassMethods.setInstanceMethod(table, "SetNbt", MLuaClassMethods.varArg(args -> {
         if (entity.level().isClientSide) {
            throw new LuaError("[MLua] Entity persistent data is server-authoritative; use GetSyncedData for client-visible values.");
         }
         if (args.narg() == 2 && args.arg(2).istable()) {
            MLuaDataManager.setEntityPersistentTag(entity, MLuaNbtWrapper.toCompound(args.arg(2)));
            return LuaValue.NONE;
         }
         CompoundTag persistentData = MLuaDataManager.entityPersistentTag(entity);
         MLuaNbtWrapper.set(persistentData, args.checkjstring(2), args.arg(3));
         MLuaDataManager.setEntityPersistentTag(entity, persistentData);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "setNbt", table.get("SetNbt"));
   }




}
