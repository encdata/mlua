package dev.mlua.modules.players.classes;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.runtime.MLuaGameThreadAccess;
import dev.mlua.runtime.data.MLuaDataManager;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns entity relationships, flags, bounds, tags, and Lua property projection. */
final class MLuaEntityPropertiesBindings {
   private MLuaEntityPropertiesBindings() { }

   static LuaTable attach(LuaTable table) {
      attachEntityControlMethods(table);
      Map<String, LuaValue> getters = new java.util.HashMap<>();
      for (String property : new String[]{"Id", "Type", "EntityType", "Name", "UUID"}) {
         LuaValue getter = table.get("Get" + property);
         if (getter.isfunction()) getters.put(property, getter);
      }
      return MLuaServiceProperties.attach(table, getters, java.util.Map.of());
   }

   private static void attachEntityControlMethods(LuaTable table) {
      LuaValue value = table.rawget("__entity");
      if (!value.isuserdata(Entity.class)) return;
      Entity entity = (Entity)value.touserdata(Entity.class);
      MLuaRegistration registration = MLuaRuntime.registrationForLevel(entity.level());
      MLuaClassMethods.setInstanceMethod(table, "GetNearbyEntities", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            double radius = MLuaLuaArguments.finiteNonNegativeNumber(args, 2, "Entity:GetNearbyEntities", "radius");
            LuaValue filter = args.arg(3);
            java.util.function.Supplier<LuaTable> query = () -> {
               LuaTable nearby = new LuaTable();
               int index = 1;
               for (Entity other : MLuaEntityQuery.findWithinRadius(
                     entity.level(), entity.getX(), entity.getY(), entity.getZ(), radius, entity, filter, registration)) {
                  nearby.set(index++, MLuaEntityWrapper.entityTable(other, registration));
               }
               return nearby;
            };
            return MLuaGameThreadAccess.callUnchecked(entity.level(), query::get);
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetVehicle", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            Entity vehicle = entity.getVehicle();
            return vehicle == null ? LuaValue.NIL : MLuaEntityWrapper.entityTable(vehicle, registration);
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetRootVehicle", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return MLuaEntityWrapper.entityTable(entity.getRootVehicle(), registration); }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetPassengers", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            LuaTable passengers = new LuaTable();
            int index = 1;
            for (Entity passenger : entity.getPassengers()) {
               passengers.set(index++, MLuaEntityWrapper.entityTable(passenger, registration));
            }
            return passengers;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "StartRiding", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            LuaValue target = args.arg(2);
            if (!target.istable()) throw new LuaError("[MLua] StartRiding expects a live Entity instance.");
            LuaValue rawTarget = target.checktable().rawget("__entity");
            if (!rawTarget.isuserdata(Entity.class)) {
               throw new LuaError("[MLua] StartRiding expects a live Entity instance.");
            }
            Entity targetEntity =
               (Entity)rawTarget.touserdata(Entity.class);
            //--version 26.1
            return LuaValue.valueOf(entity.startRiding(targetEntity, args.optboolean(3, false), true));
            //--endversion
            //--version 1.20.1,1.21.1
            return LuaValue.valueOf(entity.startRiding(targetEntity, args.optboolean(3, false)));
            //--endversion
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "StopRiding", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { entity.stopRiding(); return LuaValue.NONE; }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetIsPassenger", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.isPassenger()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetIsVehicle", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.isVehicle()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetIsInLava", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.isInLava()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetIsUnderWater", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.isUnderWater()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetFallDistance", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.fallDistance); }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetFallDistance", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            float distance = MLuaLuaArguments.finiteFloat(args, 2, "Entity:SetFallDistance", "distance");
            entity.fallDistance = Math.max(0.0F, distance);
            return LuaValue.NONE;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetCustomName", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            Component name = entity.getCustomName();
            return name == null ? LuaValue.NIL : LuaValue.valueOf(name.getString());
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetCustomName", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            LuaValue name = args.arg(2);
            entity.setCustomName(name.isnil() ? null : Component.literal(name.checkjstring()));
            return LuaValue.NONE;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetCustomNameVisible", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.isCustomNameVisible()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetCustomNameVisible", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { entity.setCustomNameVisible(args.checkboolean(2)); return LuaValue.NONE; }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetGlowing", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.hasGlowingTag()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetGlowing", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { entity.setGlowingTag(args.checkboolean(2)); return LuaValue.NONE; }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetNoGravity", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.isNoGravity()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetNoGravity", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { entity.setNoGravity(args.checkboolean(2)); return LuaValue.NONE; }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetInvulnerable", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.isInvulnerable()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetInvulnerable", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { entity.setInvulnerable(args.checkboolean(2)); return LuaValue.NONE; }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetSilent", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.isSilent()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetSilent", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { entity.setSilent(args.checkboolean(2)); return LuaValue.NONE; }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetFireTicks", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.getRemainingFireTicks()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "SetFireTicks", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            int ticks = MLuaLuaArguments.wholeInt(args, 2, "Entity:SetFireTicks", "ticks");
            entity.setRemainingFireTicks(Math.max(0, ticks));
            return LuaValue.NONE;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetEyePosition", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            Vec3 position = entity.getEyePosition();
            return MLuaVector.vector3(position.x, position.y, position.z);
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetBoundingBox", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            AABB bounds = entity.getBoundingBox();
            LuaTable result = registration.createClassInstance("EntityBounds");
            result.rawset("Minimum", MLuaVector.vector3(bounds.minX, bounds.minY, bounds.minZ));
            result.rawset("Maximum", MLuaVector.vector3(bounds.maxX, bounds.maxY, bounds.maxZ));
            result.freeze();
            return result;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetWidth", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.getBbWidth()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetHeight", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.getBbHeight()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetEyeHeight", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(entity.getEyeHeight()); }
      });
      MLuaClassMethods.setInstanceMethod(table, "GetTags", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            LuaTable tags = new LuaTable();
            int index = 1;
            for (String tag : MLuaDataManager.entityTags(entity)) tags.set(index++, LuaValue.valueOf(tag));
            return tags;
         }
      });
      MLuaClassMethods.setInstanceMethod(table, "HasTag", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(MLuaDataManager.hasEntityTag(entity, args.checkjstring(2))); }
      });
      MLuaClassMethods.setInstanceMethod(table, "AddTag", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(MLuaDataManager.addEntityTag(entity, args.checkjstring(2))); }
      });
      MLuaClassMethods.setInstanceMethod(table, "RemoveTag", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(MLuaDataManager.removeEntityTag(entity, args.checkjstring(2))); }
      });
   }


}
