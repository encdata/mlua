package dev.mlua.modules.players.classes;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.entity.MCLivingEntity;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class MLuaEntityWrapper {
   private MLuaEntityWrapper() { }

   public static void clearCache() { MLuaEntityInstanceCache.clearCache(); }
   public static void clearCache(MLuaRegistration registration) { MLuaEntityInstanceCache.clearCache(registration); }
   public static void clearSideCache(dev.mlua.common.enums.Side side) { MLuaEntityInstanceCache.clearSideCache(side); }
   public static void removeFromCache(int entityId) { MLuaEntityInstanceCache.removeFromCache(entityId); }
   public static void removeFromCache(Entity entity) { MLuaEntityInstanceCache.removeFromCache(entity); }
   public static void removeFromCache(MLuaEntityScope scope) { MLuaEntityInstanceCache.removeFromCache(scope); }
   public static void cleanCache() { MLuaEntityInstanceCache.cleanCache(); }
   public static int cacheSize() { return MLuaEntityInstanceCache.cacheSize(); }

   public static LuaTable classTable() {
      return classTable(MLuaRuntime.get());
   }

   public static LuaTable classTable(MLuaRegistration registration) {
      LuaTable classTable = registration.classTable("Entity");
      if (classTable == null) throw new IllegalStateException("Lua class is not registered: Entity");
      return classTable;
   }

   public static LuaTable entityTable(Entity entity) {
      return MLuaEntityInstanceFactory.entityTable(entity);
   }

   public static LuaTable entityTable(Entity entity, MLuaRegistration registration) {
      return MLuaEntityInstanceFactory.entityTable(entity, registration);
   }

   public static LuaTable entityTable(LivingEntity entity) {
      return MLuaEntityInstanceFactory.entityTable(entity);
   }

   public static LuaTable entityTable(LivingEntity entity, MLuaRegistration registration) {
      return MLuaEntityInstanceFactory.entityTable(entity, registration);
   }

   public static LuaTable livingEntityTable(LivingEntity entity, MLuaRegistration registration) {
      return MLuaEntityInstanceFactory.livingEntityTable(entity, registration);
   }

   static LuaTable livingEntityTableBase(LivingEntity entity, MLuaRegistration registration) {
      return MLuaEntityInstanceFactory.livingEntityTableBase(entity, registration);
   }

   static MLuaEntityWrapperCache cacheFor(MLuaRegistration registration) {
      return MLuaEntityInstanceCache.cacheFor(registration);
   }

   public static LuaTable cachedPlayerTable(Entity player, MLuaRegistration registration) {
      return MLuaEntityInstanceCache.cachedPlayerTable(player, registration);
   }

   public static void cachePlayerTable(Entity player, MLuaRegistration registration, LuaTable table) {
      MLuaEntityInstanceCache.cachePlayerTable(player, registration, table);
   }

   public static LuaTable attachEntityProperties(LuaTable table) {
      return MLuaEntityPropertiesBindings.attach(table);
   }

   private static void attachEntityClass(MLuaRegistration registration, LuaTable table) {
      if (registration == null) throw new IllegalStateException("Lua registration is required for Entity instances");
      registration.attachClassInstance(table, "Entity", registration.requireInstanceBaseClassName());
   }

   private static void attachLivingEntityClass(MLuaRegistration registration, LuaTable table) {
      if (registration == null) throw new IllegalStateException("Lua registration is required for LivingEntity instances");
      LuaValue value = table.rawget("__entity");
      if (value.isuserdata(Mob.class)) {
         registration.attachClassInstance(table, "Mob", "LivingEntity");
      } else {
         registration.attachClassInstance(table, "LivingEntity", "Entity");
      }
   }

   private static boolean hasEntity(LuaTable table, Entity entity) {
      LuaValue value = table.rawget("__entity");
      return value.isuserdata(Entity.class)
         && value.touserdata(Entity.class) == entity;
   }

   private static String entityTypeName(MCEntity entity) {
      try {
         Entity raw = ((MCEntityImpl)entity).inner();
         ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(raw.getType());
         return typeId != null ? typeId.toString() : raw.getClass().getSimpleName();
      } catch (Throwable var2) {
         return ((MCEntityImpl)entity).inner().getClass().getSimpleName();
      }
   }

   public static int entityId(MCEntity entity) {
      return entity.getId();
   }

   public static UUID entityUuid(MCEntity entity) {
      return entity.getUUID();
   }

   public static String entityStringUuid(MCEntity entity) {
      return entity.getUUID().toString();
   }

   public static String entityName(MCEntity entity) {
      Component component = ((MCEntityImpl)entity).inner().getName();
      return component != null ? component.getString() : "";
   }

   public static String entityDisplayName(MCEntity entity) {
      Component component = ((MCEntityImpl)entity).inner().getDisplayName();
      return component != null ? component.getString() : entityName(entity);
   }

   public static boolean isAlive(MCEntity entity) {
      return entity.isAlive();
   }

   public static MCVec3 position(MCEntity entity) {
      return entity.getPosition();
   }

   static void bindLevelMethods(LuaTable table, Level level,
                                MLuaRegistration registration) {
      MLuaClassMethods.setInstanceMethod(table, "GetWorld",
         MLuaClassMethods.zeroArg(() -> MLuaLevelWrapper.levelTable(level, registration)));
      MLuaClassMethods.setInstanceMethod(table, "GetLevel",
         MLuaClassMethods.zeroArg(() -> MLuaLevelWrapper.levelTable(level, registration)));
      MLuaClassMethods.setInstanceMethod(table, "GetDimension",
         MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(level.dimension().location().toString())));
   }

   public static LuaValue setVelocity(MCEntity entity, LuaValue value) {
      return MLuaEntityTransformBindings.setVelocity(entity, value);
   }

   public static MCVec3 lookAngle(MCEntity entity) {
      return MLuaEntityTransformBindings.lookAngle(entity);
   }

   public static float xRot(MCEntity entity) {
      return entity.getXRot();
   }

   public static float yRot(MCEntity entity) {
      return entity.getYRot();
   }

   public static float health(MCLivingEntity entity) {
      return entity.getHealth();
   }

   public static void setHealth(MCLivingEntity entity, float value) {
      if (!Float.isFinite(value) || value < 0.0F) {
         throw new LuaError("[MLua] LivingEntity:SetHealth requires a finite, non-negative health value.");
      }
      entity.setHealth(value);
   }

   public static void setAbsorption(LivingEntity entity, float value) {
      if (!Float.isFinite(value) || value < 0.0F) {
         throw new LuaError("[MLua] LivingEntity:SetAbsorption requires a finite, non-negative value.");
      }
      entity.setAbsorptionAmount(value);
   }

   public static float maxHealth(MCLivingEntity entity) {
      return entity.getMaxHealth();
   }

   public static int armorValue(MCLivingEntity entity) {
      return (int)entity.getArmorValue();
   }

   static double[] parsePos(LuaValue value) {
      if (!value.istable()) {
         return null;
      } else {
         LuaTable t = value.checktable();
         double x = t.get("X").optdouble(t.get("x").optdouble(Double.NaN));
         double y = t.get("Y").optdouble(t.get("y").optdouble(Double.NaN));
         double z = t.get("Z").optdouble(t.get("z").optdouble(Double.NaN));
         return !Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z) ? new double[]{x, y, z} : null;
      }
   }

   public static MLuaCFrame cframe(MCEntity entity) { return MLuaEntityTransformBindings.cframe(entity); }
   public static void applyCFrame(MCEntity entity, MLuaCFrame value) { MLuaEntityTransformBindings.applyCFrame(entity, value); }
   public static void setRotation(MCEntity entity, double pitch, double yaw) { MLuaEntityTransformBindings.setRotation(entity, pitch, yaw); }
   public static void applyRotationArguments(MCEntity entity, Varargs args, int valueIndex) {
      MLuaEntityTransformBindings.applyRotationArguments(entity, args, valueIndex);
   }
   public static LuaValue distanceTo(MCEntity entity, LuaValue target) { return MLuaEntityTransformBindings.distanceTo(entity, target); }
   public static void applyRotation(MCEntity entity, LuaValue value) { MLuaEntityTransformBindings.applyRotation(entity, value); }

   public static double[] targetPosition(LuaValue value) {
      if (MLuaCFrame.isCFrame(value) || MLuaVector.isVector3(value)) {
         return MLuaLuaArguments.finiteVector3(value, "Entity target position");
      }
      Entity entity = entityFromLua(value);
      if (entity != null) {
         MLuaRegistration registration = MLuaRuntime.registrationForLevel(entity.level());
         if (registration != null && registration.isInstanceOf(value, "Entity")) {
            return new double[]{entity.getX(), entity.getY(), entity.getZ()};
         }
      }
      throw new LuaError("[MLua] Expected a native Vector3, CFrame, or Entity instance");
   }

   private static Entity entityFromLua(LuaValue value) {
      if (!value.istable()) {
         return null;
      }
      LuaValue entityValue = value.checktable().rawget("__entity");
      return entityValue.isuserdata(Entity.class) ? (Entity) entityValue.touserdata(Entity.class) : null;
   }

   public static Entity fromLuaInstance(LuaValue value, MLuaRegistration registration) {
      if (registration == null || !registration.isInstanceOf(value, "Entity")) return null;
      return entityFromLua(value);
   }

   public static Entity requireLuaInstance(LuaValue value, MLuaRegistration registration,
                                                                        String className, String expected) {
      if (registration == null || !registration.isInstanceOf(value, className)) {
         throw new LuaError("[MLua] Expected a " + expected + " instance");
      }
      Entity entity = entityFromLua(value);
      if (entity == null) throw new LuaError("[MLua] Expected a live " + expected + " instance");
      return entity;
   }




}
