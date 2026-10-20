package dev.mlua.modules.players.classes;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.math.MLuaVector;
import dev.mlua.mc.impl.core.MCVec3Impl;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.math.MLuaCFrame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Owns the Lua method bindings for entity position, velocity, rotation, and facing. */
final class MLuaEntityTransformBindings {
   private MLuaEntityTransformBindings() { }

   static void bind(LuaTable table, MCEntity entity) {
      MLuaClassMethods.setInstanceMethod(table, "GetPosition", MLuaClassMethods.zeroArg(() -> {
         MCVec3 position = entity.getPosition();
         return MLuaPlayerWrapper.positionTable(position.x(), position.y(), position.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetPositionX", MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(entity.getPosition().x())));
      MLuaClassMethods.setInstanceMethod(table, "GetPositionY", MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(entity.getPosition().y())));
      MLuaClassMethods.setInstanceMethod(table, "GetPositionZ", MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(entity.getPosition().z())));
      MLuaClassMethods.setInstanceMethod(table, "SetPosition", MLuaClassMethods.varArg(args -> {
         double[] position = MLuaPlayerWrapper.positionArgs(args, 2);
         Entity raw = ((MCEntityImpl)entity).inner();
         if (raw instanceof Player) {
            raw.teleportTo(position[0], position[1], position[2]);
         } else {
            raw.setPos(position[0], position[1], position[2]);
         }
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "Teleport", MLuaClassMethods.varArg(args -> {
         double[] position = MLuaPlayerWrapper.positionArgs(args, 2);
         ((MCEntityImpl)entity).inner().teleportTo(position[0], position[1], position[2]);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetVelocity", MLuaClassMethods.zeroArg(() -> {
         MCVec3 velocity = entity.getDeltaMovement();
         return MLuaPlayerWrapper.positionTable(velocity.x(), velocity.y(), velocity.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetVelocity", MLuaClassMethods.varArg(args -> setVelocity(entity, args.arg(2))));
      MLuaClassMethods.setInstanceMethod(table, "GetRotation", MLuaClassMethods.zeroArg(() ->
         MLuaVector.vector2((double)entity.getXRot(), (double)entity.getYRot())));
      MLuaClassMethods.setInstanceMethod(table, "GetRotationRadians", MLuaClassMethods.zeroArg(() ->
         MLuaVector.vector2(Math.toRadians((double)entity.getXRot()), Math.toRadians((double)entity.getYRot()))));
      MLuaClassMethods.setInstanceMethod(table, "SetRotationRadians", MLuaClassMethods.varArg(args -> {
         double[] rotation = MLuaVector.components2(args.arg(2));
         applyRotation(entity, MLuaVector.vector2(Math.toDegrees(rotation[0]), Math.toDegrees(rotation[1])));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetCFrame", MLuaClassMethods.zeroArg(() -> cframe(entity).toLua()));
      MLuaClassMethods.setInstanceMethod(table, "GetLookVector", MLuaClassMethods.zeroArg(() -> {
         MCVec3 look = lookAngle(entity);
         return MLuaPlayerWrapper.positionTable(look.x(), look.y(), look.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetCFrame", MLuaClassMethods.varArg(args -> {
         applyCFrame(entity, MLuaCFrame.fromLua(args.arg(2)));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetRotation", MLuaClassMethods.varArg(args -> {
         applyRotationArguments(entity, args, 2);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "LookAt", MLuaClassMethods.varArg(args -> {
         double[] target = MLuaEntityWrapper.targetPosition(args.arg(2));
         MCVec3 position = entity.getPosition();
         applyCFrame(entity, MLuaCFrame.lookAt(position.x(), position.y(), position.z(), target[0], target[1], target[2]));
         return LuaValue.NONE;
      }));
   }






   static LuaValue setVelocity(MCEntity entity, LuaValue value) {
      double[] velocity = MLuaVector.components3(value);
      for (double component : velocity) {
         if (!Double.isFinite(component)) {
            throw new LuaError("[MLua] Entity:SetVelocity requires finite Vector3 components.");
         }
      }
      entity.setDeltaMovement(MCVec3.of(velocity[0], velocity[1], velocity[2]));
      ((MCEntityImpl)entity).inner().hurtMarked = true;
      return LuaValue.NONE;
   }

   static MCVec3 lookAngle(MCEntity entity) {
      Vec3 look = ((MCEntityImpl)entity).inner().getLookAngle();
      return MCVec3Impl.fromReal(look);
   }

   static MLuaCFrame cframe(MCEntity entity) {
      MCVec3 look = lookAngle(entity);
      MCVec3 p = entity.getPosition();
      return MLuaCFrame.fromEntity(p.x(), p.y(), p.z(), look.x(), look.y(), look.z());
   }

   static void applyCFrame(MCEntity entity, MLuaCFrame cf) {
      double yaw = cf.minecraftYaw();
      double pitch = cf.minecraftPitch();
      requireFiniteTransform(cf.x(), cf.y(), cf.z(), yaw, pitch);
      Entity raw = ((MCEntityImpl)entity).inner();
      raw.teleportTo(cf.x(), cf.y(), cf.z());
      setRotation(entity, pitch, yaw);
   }

   static void setRotation(MCEntity entity, double pitch, double yaw) {
      requireFiniteTransform(0.0, 0.0, 0.0, yaw, pitch);
      entity.setYRot((float)yaw);
      entity.setXRot((float)pitch);
      Entity raw = ((MCEntityImpl)entity).inner();
      raw.setYHeadRot((float)yaw);
      raw.setYBodyRot((float)yaw);
      raw.hurtMarked = true;
   }

   static void applyRotationArguments(MCEntity entity, Varargs args, int valueIndex) {
      if (args.arg(valueIndex + 1).isnumber()) {
         setRotation(entity, args.checkdouble(valueIndex), args.checkdouble(valueIndex + 1));
      } else {
         applyRotation(entity, args.arg(valueIndex));
      }
   }

   static LuaValue distanceTo(MCEntity entity, LuaValue targetValue) {
      double[] target = MLuaEntityWrapper.targetPosition(targetValue);
      MCVec3 position = entity.getPosition();
      double dx = position.x() - target[0];
      double dy = position.y() - target[1];
      double dz = position.z() - target[2];
      return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy + dz * dz));
   }

   static void applyRotation(MCEntity entity, LuaValue value) {
      if (MLuaCFrame.isCFrame(value)) {
         applyCFrame(entity, MLuaCFrame.fromLua(value));
      } else if (!value.istable()) {
         throw new LuaError("[MLua] Rotation expects {Yaw, Pitch}, Vector3 Orientation, or CFrame");
      } else {
         LuaTable rot = value.checktable();
         double yaw = rot.get("Yaw").optdouble(rot.get("yaw").optdouble(Double.NaN));
         double pitch = rot.get("Pitch").optdouble(rot.get("pitch").optdouble(Double.NaN));
         if (Double.isNaN(yaw) && rot.get("Y").isnumber()) {
            yaw = rot.get("Y").todouble();
         }
         if (Double.isNaN(pitch) && rot.get("X").isnumber()) {
            pitch = rot.get("X").todouble();
         }
         if (Double.isNaN(yaw)) {
            yaw = (double)entity.getYRot();
         }
         if (Double.isNaN(pitch)) {
            pitch = (double)entity.getXRot();
         }
         setRotation(entity, pitch, yaw);
      }
   }

   private static void requireFiniteTransform(double x, double y, double z, double yaw, double pitch) {
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
         || !Double.isFinite(yaw) || !Double.isFinite(pitch)
         || Math.abs(yaw) > Float.MAX_VALUE || Math.abs(pitch) > Float.MAX_VALUE) {
         throw new LuaError("[MLua] Entity transform values must be finite and representable by Minecraft.");
      }
   }


}
