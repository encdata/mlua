package dev.mlua.modules.players.Class;

import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.minecraft.animation.MLuaAnimationTrack;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.binding.MLuaAnimationBindings;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.modules.combat.Service.MLuaCombatService;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaNbtWrapper;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.entity.MCLivingEntity;
import dev.mlua.mc.impl.damage.MCDamageSourceImpl;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mc.impl.entity.MCLivingEntityImpl;
import dev.mlua.mc.impl.core.MCVec3Impl;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaClientModelManager;
import dev.mlua.runtime.client.MLuaClientParticleManager;
import dev.mlua.runtime.client.MLuaClientSoundManager;
import dev.mlua.runtime.entity.MLuaEntityComponentManager;
import java.util.HashSet;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public final class MLuaEntityWrapper {
   private static final MLuaEntityWrapperCache LEGACY_CACHE = new MLuaEntityWrapperCache();
   private static final Map<MLuaRegistration, MLuaEntityWrapperCache> ENTITY_CACHES =
         Collections.synchronizedMap(new WeakHashMap<>());

   private MLuaEntityWrapper() {
   }

   public static void clearCache() {
      LEGACY_CACHE.clear();
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.values().forEach(MLuaEntityWrapperCache::clear); }
   }

   public static void clearCache(MLuaRegistration registration) {
      if (registration == null) return;
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.remove(registration); }
   }

   public static void removeFromCache(int entityId) {
      LEGACY_CACHE.removeEntityId(entityId);
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.values().forEach(cache -> cache.removeEntityId(entityId)); }
      MLuaAnimationController.removeEntity(entityId);
   }

   public static void removeFromCache(net.minecraft.world.entity.Entity entity) {
      if (entity == null) return;
      MLuaEntityScope scope = MLuaEntityScope.of(entity);
      MLuaEntityComponentManager.clearEntity(entity);
      MLuaEntityWrapperCache.Key key = MLuaEntityWrapperCache.key(entity);
      LEGACY_CACHE.remove(key);
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.values().forEach(cache -> cache.remove(key)); }
      MLuaAnimationController.removeEntity(entity);
      if (scope.side() == dev.mlua.common.enums.Side.CLIENT) {
         MLuaClientRenderManager.resetEntityModel(entity);
         MLuaClientModelManager.clearEntity(scope);
         MLuaClientSoundManager.removeFollowingEntity(scope);
         MLuaClientParticleManager.removeFollowingEntity(scope);
      }
   }

   public static void cleanCache() {
      LEGACY_CACHE.clean();
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.values().forEach(MLuaEntityWrapperCache::clean); }
   }

   public static int cacheSize() {
      synchronized (ENTITY_CACHES) {
         return LEGACY_CACHE.size() + ENTITY_CACHES.values().stream().mapToInt(MLuaEntityWrapperCache::size).sum();
      }
   }

   public static LuaTable classTable() {
      return classTable(MLuaRuntime.get());
   }

   public static LuaTable classTable(MLuaRegistration registration) {
      LuaTable classTable = registration.classTable("Entity");
      if (classTable == null) throw new IllegalStateException("Lua class is not registered: Entity");
      return classTable;
   }

    public static LuaTable entityTable(net.minecraft.world.entity.Entity entity) {
      return entityTable(entity, MLuaRuntime.get());
    }

    public static LuaTable entityTable(net.minecraft.world.entity.Entity entity, MLuaRegistration registration) {
      if (entity instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
         return entityTable(livingEntity, registration);
      }
      MCEntity wrapped = new MCEntityImpl(entity);
      MLuaEntityWrapperCache.Key cacheKey = MLuaEntityWrapperCache.key(entity);
      MLuaEntityWrapperCache cache = cacheFor(registration);
      LuaTable cached = cache.get(cacheKey);
      if (cached != null && cached.rawget("__entity").touserdata() == entity) return cached;

      LuaTable table = cached != null ? cached : new LuaTable();
      table.rawset("__side", LuaValue.valueOf("Shared"));
      table.rawset("__entity", LuaValue.userdataOf(entity));
      registration.attachClassInstance(table, "Entity", "Instance");
      if (cached == null) cache.put(cacheKey, table);
      table.rawset("GetId", luaFn(() -> LuaValue.valueOf(wrapped.getId())));
      table.rawset("GetType", luaFn(() -> LuaValue.valueOf(entityTypeName(wrapped))));
      table.rawset("GetName", luaFn(() -> LuaValue.valueOf(entityName(wrapped))));
      table.rawset("GetUUID", luaFn(() -> LuaValue.valueOf(entityStringUuid(wrapped))));
      table.rawset("GetPosition", luaFn(() -> {
         MCVec3 p = wrapped.position();
         return MLuaPlayerWrapper.positionTable(p.x(), p.y(), p.z());
      }));
      table.rawset("GetPositionX", luaFn(() -> LuaValue.valueOf(wrapped.position().x())));
      table.rawset("GetPositionY", luaFn(() -> LuaValue.valueOf(wrapped.position().y())));
      table.rawset("GetPositionZ", luaFn(() -> LuaValue.valueOf(wrapped.position().z())));
      table.rawset("GetEntityType", luaFn(() -> LuaValue.valueOf(entityTypeName(wrapped))));
      table.rawset("SetPosition", varArg((args) -> {
         double[] pos = MLuaPlayerWrapper.positionArgs(args, 2);
         ((MCEntityImpl)wrapped).inner().setPos(pos[0], pos[1], pos[2]);
         return LuaValue.NONE;
      }));
      table.rawset("Teleport", varArg((args) -> {
         double[] pos = MLuaPlayerWrapper.positionArgs(args, 2);
         ((MCEntityImpl)wrapped).inner().teleportTo(pos[0], pos[1], pos[2]);
         return LuaValue.NONE;
      }));
      table.rawset("GetVelocity", luaFn(() -> {
         MCVec3 v = wrapped.getDeltaMovement();
         return MLuaPlayerWrapper.positionTable(v.x(), v.y(), v.z());
      }));
      table.rawset("SetVelocity", varArg((args) -> {
         double[] vel = MLuaPlayerWrapper.positionArgs(args, 2);
         wrapped.setDeltaMovement(MCVec3.of(vel[0], vel[1], vel[2]));
         ((MCEntityImpl)wrapped).inner().hurtMarked = true;
         return LuaValue.NONE;
      }));
      table.rawset("GetRotation", luaFn(() -> MLuaVector.vector2((double)wrapped.getXRot(), (double)wrapped.getYRot())));
      table.rawset("GetRotationRadians", luaFn(() -> MLuaVector.vector2(Math.toRadians((double)wrapped.getXRot()), Math.toRadians((double)wrapped.getYRot()))));
      table.rawset("SetRotationRadians", varArg((args) -> {
         LuaValue value = args.arg(2);
         if (!value.istable()) {
            throw new LuaError("[MLua] SetRotationRadians expects Vector2(pitch, yaw)");
         }
         LuaTable rotation = value.checktable();
         double pitch = rotation.get("X").isnumber() ? rotation.get("X").todouble() : rotation.get("x").checkdouble();
         double yaw = rotation.get("Y").isnumber() ? rotation.get("Y").todouble() : rotation.get("y").checkdouble();
         applyRotation(wrapped, MLuaVector.vector2(Math.toDegrees(pitch), Math.toDegrees(yaw)));
         return LuaValue.NONE;
      }));
      table.rawset("GetCFrame", luaFn(() -> cframe(wrapped).toLua()));
      table.rawset("GetLookVector", luaFn(() -> {
         MCVec3 look = lookAngle(wrapped);
         return MLuaPlayerWrapper.positionTable(look.x(), look.y(), look.z());
      }));
      table.rawset("SetCFrame", varArg((args) -> {
         MLuaCFrame cf = MLuaCFrame.fromLua(args.arg(2));
         applyCFrame(wrapped, cf);
         return LuaValue.NONE;
      }));
      table.rawset("SetRotation", varArg((args) -> {
         applyRotation(wrapped, args.arg(2));
         return LuaValue.NONE;
      }));
      table.rawset("LookAt", varArg((args) -> {
         double[] target = targetPosition(args.arg(2));
         MCVec3 p = wrapped.position();
         applyCFrame(wrapped, MLuaCFrame.lookAt(p.x(), p.y(), p.z(), target[0], target[1], target[2]));
         return LuaValue.NONE;
      }));
      table.rawset("Kill", luaFn(() -> {
         ((MCEntityImpl)wrapped).inner().kill();
         return LuaValue.NONE;
      }));
      table.rawset("Remove", luaFn(() -> {
         ((MCEntityImpl)wrapped).inner().remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
         return LuaValue.NONE;
      }));
      table.rawset("DistanceTo", varArg((args) -> {
         double[] other = MLuaPlayerWrapper.positionArgs(args, 2);
         MCVec3 p = wrapped.position();
         double dx = p.x() - other[0];
         double dy = p.y() - other[1];
         double dz = p.z() - other[2];
         return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy + dz * dz));
      }));
      table.rawset("GetIsAlive", luaFn(() -> LuaValue.valueOf(wrapped.isAlive())));
      table.rawset("GetIsOnGround", luaFn(() -> LuaValue.valueOf(((MCEntityImpl)wrapped).inner().onGround())));
      table.rawset("GetIsInWater", luaFn(() -> LuaValue.valueOf(((MCEntityImpl)wrapped).inner().isInWater())));
      table.rawset("GetIsOnFire", luaFn(() -> LuaValue.valueOf(((MCEntityImpl)wrapped).inner().isOnFire())));
      table.rawset("GetIsMoving", luaFn(() -> {
         MCVec3 v = wrapped.getDeltaMovement();
         double hSpeed = Math.sqrt(v.x() * v.x() + v.z() * v.z());
         return LuaValue.valueOf(hSpeed > 0.05);
      }));
      table.rawset("GetIsFalling", luaFn(() -> {
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
         return LuaValue.valueOf(!raw.onGround() && raw.getDeltaMovement().y < -0.08);
      }));
      table.rawset("GetNbt", varArg((args) -> {
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
         if (args.narg() < 2 || args.arg(2).isnil()) {
            return MLuaDataManager.entityPersistentData(raw, !raw.level().isClientSide);
         }
         if (raw.level().isClientSide) return LuaValue.NIL;
         return MLuaNbtWrapper.get(MLuaDataManager.entityPersistentTag(raw), args.checkjstring(2));
      }));
      table.rawset("GetPersistentData", luaFn(() -> MLuaDataManager.entityPersistentData(((MCEntityImpl)wrapped).inner(), !((MCEntityImpl)wrapped).inner().level().isClientSide)));
      table.rawset("GetSyncedData", luaFn(() -> MLuaDataManager.entitySyncedData(((MCEntityImpl)wrapped).inner())));
      table.rawset("getNbt", table.get("GetNbt"));
      table.rawset("SetNbt", varArg((args) -> {
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
         if (raw.level().isClientSide) {
            throw new LuaError("[MLua] Entity persistent data is server-authoritative; use GetSyncedData for client-visible values.");
         }
         CompoundTag persistentData = MLuaDataManager.entityPersistentTag(raw);
         if (args.narg() == 2 && args.arg(2).istable()) {
            for (String key : new HashSet<>(persistentData.getAllKeys())) {
               persistentData.remove(key);
            }
            org.luaj.vm2.LuaTable replacement = args.arg(2).checktable();
            LuaValue key = LuaValue.NIL;
            while (true) {
               Varargs next = replacement.next(key);
               key = next.arg1();
               if (key.isnil()) {
                  break;
               }
               if (key.isstring()) {
                  MLuaNbtWrapper.set(persistentData, key.tojstring(), next.arg(2));
               }
            }
            MLuaDataManager.setEntityPersistentTag(raw, persistentData);
            return LuaValue.NONE;
         }
         MLuaNbtWrapper.set(persistentData, args.checkjstring(2), args.arg(3));
         MLuaDataManager.setEntityPersistentTag(raw, persistentData);
         return LuaValue.NONE;
      }));
      table.rawset("setNbt", table.get("SetNbt"));
      return MLuaServiceProperties.attach(table);
   }

    public static LuaTable entityTable(net.minecraft.world.entity.LivingEntity entity) {
      return entityTable(entity, MLuaRuntime.get());
    }

    public static LuaTable entityTable(net.minecraft.world.entity.LivingEntity entity, MLuaRegistration registration) {
      MCLivingEntity wrapped = new MCLivingEntityImpl(entity);
      MLuaEntityWrapperCache.Key cacheKey = MLuaEntityWrapperCache.key(entity);
      MLuaEntityWrapperCache cache = cacheFor(registration);
      LuaTable cached = cache.get(cacheKey);
      if (cached != null && cached.rawget("__entity").touserdata() == entity) return cached;
       LuaTable table = cached != null ? cached : new LuaTable();
       table.rawset("__side", LuaValue.valueOf("Shared"));
       table.rawset("__entity", LuaValue.userdataOf(entity));
       registration.attachClassInstance(table, "Entity", "Instance");
       if (cached == null) cache.put(cacheKey, table);
       table.rawset("GetId", luaFn(() -> {
          return LuaValue.valueOf(wrapped.getId());
       }));
       table.rawset("GetType", luaFn(() -> {
          return LuaValue.valueOf(entityTypeName(wrapped));
       }));
       table.rawset("GetName", luaFn(() -> {
          return LuaValue.valueOf(entityName(wrapped));
       }));
       table.rawset("GetUUID", luaFn(() -> {
          return LuaValue.valueOf(entityStringUuid(wrapped));
       }));
      table.rawset("GetPosition", luaFn(() -> {
         MCVec3 p = wrapped.position();
         return MLuaPlayerWrapper.positionTable(p.x(), p.y(), p.z());
      }));
      table.rawset("GetPositionX", luaFn(() -> LuaValue.valueOf(wrapped.position().x())));
      table.rawset("GetPositionY", luaFn(() -> LuaValue.valueOf(wrapped.position().y())));
      table.rawset("GetPositionZ", luaFn(() -> LuaValue.valueOf(wrapped.position().z())));
       table.rawset("GetEntityType", luaFn(() -> LuaValue.valueOf(entityTypeName(wrapped))));
      table.rawset("SetPosition", varArg((args) -> {
         double[] pos = MLuaPlayerWrapper.positionArgs(args, 2);
         ((MCEntityImpl)wrapped).inner().setPos(pos[0], pos[1], pos[2]);
         return LuaValue.NONE;
      }));
      table.rawset("Teleport", varArg((args) -> {
         double[] pos = MLuaPlayerWrapper.positionArgs(args, 2);
         ((MCEntityImpl)wrapped).inner().teleportTo(pos[0], pos[1], pos[2]);
         return LuaValue.NONE;
      }));
      table.rawset("GetVelocity", luaFn(() -> {
         MCVec3 v = wrapped.getDeltaMovement();
         return MLuaPlayerWrapper.positionTable(v.x(), v.y(), v.z());
      }));
      table.rawset("SetVelocity", varArg((args) -> {
         double[] vel = MLuaPlayerWrapper.positionArgs(args, 2);
         wrapped.setDeltaMovement(MCVec3.of(vel[0], vel[1], vel[2]));
         ((MCEntityImpl)wrapped).inner().hurtMarked = true;
         return LuaValue.NONE;
      }));
      table.rawset("GetHealth", luaFn(() -> {
         return LuaValue.valueOf((double)wrapped.getHealth());
      }));
      table.rawset("SetHealth", varArg((args) -> {
         wrapped.setHealth((float)args.checkdouble(2));
         return LuaValue.NONE;
      }));
      table.rawset("GetMaxHealth", luaFn(() -> {
         return LuaValue.valueOf((double)wrapped.getMaxHealth());
      }));
      table.rawset("GetArmor", luaFn(() -> {
         return LuaValue.valueOf(wrapped.getArmorValue());
      }));
      table.rawset("GetRotation", luaFn(() -> {
         return MLuaVector.vector2((double)wrapped.getXRot(), (double)wrapped.getYRot());
      }));
      table.rawset("GetRotationRadians", luaFn(() -> {
         return MLuaVector.vector2(Math.toRadians((double)wrapped.getXRot()), Math.toRadians((double)wrapped.getYRot()));
      }));
      table.rawset("SetRotationRadians", varArg((args) -> {
         LuaValue value = args.arg(2);
         if (!value.istable()) {
            throw new LuaError("[MLua] SetRotationRadians expects Vector2(pitch, yaw)");
         } else {
            LuaTable rotation = value.checktable();
            double pitch = rotation.get("X").isnumber() ? rotation.get("X").todouble() : rotation.get("x").checkdouble();
            double yaw = rotation.get("Y").isnumber() ? rotation.get("Y").todouble() : rotation.get("y").checkdouble();
            applyRotation(wrapped, MLuaVector.vector2(Math.toDegrees(pitch), Math.toDegrees(yaw)));
            return LuaValue.NONE;
         }
      }));
      table.rawset("GetCFrame", luaFn(() -> {
         return cframe(wrapped).toLua();
      }));
      table.rawset("GetLookVector", luaFn(() -> {
         MCVec3 look = lookAngle(wrapped);
         return MLuaPlayerWrapper.positionTable(look.x(), look.y(), look.z());
      }));
      table.rawset("SetCFrame", varArg((args) -> {
         MLuaCFrame cf = MLuaCFrame.fromLua(args.arg(2));
         applyCFrame(wrapped, cf);
         return LuaValue.NONE;
      }));
      table.rawset("SetRotation", varArg((args) -> {
         applyRotation(wrapped, args.arg(2));
         return LuaValue.NONE;
      }));
      table.rawset("LookAt", varArg((args) -> {
         double[] target = targetPosition(args.arg(2));
         MCVec3 p = wrapped.position();
         applyCFrame(wrapped, MLuaCFrame.lookAt(p.x(), p.y(), p.z(), target[0], target[1], target[2]));
         return LuaValue.NONE;
      }));
      table.rawset("Kill", luaFn(() -> {
         ((MCEntityImpl)wrapped).inner().kill();
         return LuaValue.NONE;
      }));
      table.rawset("Remove", luaFn(() -> {
         ((MCEntityImpl)wrapped).inner().remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
         return LuaValue.NONE;
      }));
       table.rawset("Damage", varArg((args) -> {
          float amount = (float)args.checkdouble(2);
          String sourceStr = args.optjstring(3, "generic");
          net.minecraft.world.entity.Entity attacker = entityFromLua(args.arg(4));
          net.minecraft.world.damagesource.DamageSource mcSource = MLuaCombatService.createDamageSource(((MCEntityImpl)wrapped).inner(), sourceStr, attacker);
          wrapped.hurt(new MCDamageSourceImpl(mcSource), amount);
          return LuaValue.NONE;
       }));
      table.rawset("GetAttribute", varArg((args) -> {
         String attrName = args.checkjstring(2);
         net.minecraft.core.Holder.Reference<net.minecraft.world.entity.ai.attributes.Attribute> attrHolder = BuiltInRegistries.ATTRIBUTE.getHolder(MLuaResourceLocations.parse(attrName)).orElse(null);
         if (attrHolder == null) {
            return LuaValue.NIL;
         }
         net.minecraft.world.entity.ai.attributes.AttributeInstance instance = ((net.minecraft.world.entity.LivingEntity)((MCLivingEntityImpl)wrapped).inner()).getAttribute(attrHolder);
          return instance == null ? LuaValue.NIL : LuaValue.valueOf(instance.getValue());
       }));
       table.rawset("SetAttribute", varArg((args) -> {
          String attrName = args.checkjstring(2);
          double value = args.checkdouble(3);
          net.minecraft.core.Holder.Reference<net.minecraft.world.entity.ai.attributes.Attribute> attrHolder = BuiltInRegistries.ATTRIBUTE.getHolder(MLuaResourceLocations.parse(attrName)).orElse(null);
          if (attrHolder == null) {
             return LuaValue.FALSE;
          }
          net.minecraft.world.entity.ai.attributes.AttributeInstance instance = ((net.minecraft.world.entity.LivingEntity)((MCLivingEntityImpl)wrapped).inner()).getAttribute(attrHolder);
         if (instance == null) {
            return LuaValue.FALSE;
         }
         instance.setBaseValue(value);
         return LuaValue.TRUE;
      }));
      table.rawset("GetEquipment", varArg((args) -> {
         net.minecraft.world.entity.EquipmentSlot slot = MLuaMinecraftEnums.resolveSlot(args.arg(2));
          return MLuaItemWrapper.itemTable(((net.minecraft.world.entity.LivingEntity)((MCLivingEntityImpl)wrapped).inner()).getItemBySlot(slot));
      }));
      table.rawset("SetEquipment", varArg((args) -> {
         net.minecraft.world.entity.EquipmentSlot slot = MLuaMinecraftEnums.resolveSlot(args.arg(2));
         net.minecraft.world.item.ItemStack stack = MLuaItemWrapper.toItemStack(args.arg(3));
         ((net.minecraft.world.entity.LivingEntity)((MCLivingEntityImpl)wrapped).inner()).setItemSlot(slot, stack);
         return LuaValue.NONE;
      }));
      table.rawset("DistanceTo", varArg((args) -> {
         double[] other = MLuaPlayerWrapper.positionArgs(args, 2);
         MCVec3 p = wrapped.position();
         double dx = p.x() - other[0];
         double dy = p.y() - other[1];
         double dz = p.z() - other[2];
         return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy + dz * dz));
      }));
      table.rawset("GetIsAlive", luaFn(() -> {
         return LuaValue.valueOf(wrapped.isAlive());
      }));
      table.rawset("GetIsOnGround", luaFn(() -> {
         return LuaValue.valueOf(((MCEntityImpl)wrapped).inner().onGround());
      }));
      table.rawset("GetIsInWater", luaFn(() -> {
         return LuaValue.valueOf(((MCEntityImpl)wrapped).inner().isInWater());
      }));
       table.rawset("GetIsOnFire", luaFn(() -> {
          return LuaValue.valueOf(((MCEntityImpl)wrapped).inner().isOnFire());
       }));
       table.rawset("GetIsMoving", luaFn(() -> {
          MCVec3 v = wrapped.getDeltaMovement();
          double hSpeed = Math.sqrt(v.x() * v.x() + v.z() * v.z());
          return LuaValue.valueOf(hSpeed > 0.05);
       }));
       table.rawset("GetIsJumping", luaFn(() -> {
          net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
          return LuaValue.valueOf(!raw.onGround() && raw.getDeltaMovement().y > 0.08);
       }));
       table.rawset("GetIsFalling", luaFn(() -> {
          net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
          return LuaValue.valueOf(!raw.onGround() && raw.getDeltaMovement().y < -0.08);
       }));
       table.rawset("GetIsSwimming", luaFn(() -> {
          return LuaValue.valueOf(((MCLivingEntityImpl)wrapped).inner().isSwimming());
       }));
      table.rawset("GetNbt", varArg((args) -> {
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
          if (args.narg() < 2 || args.arg(2).isnil()) {
            return MLuaDataManager.entityPersistentData(raw, !raw.level().isClientSide);
          }
          if (raw.level().isClientSide) return LuaValue.NIL;
          return MLuaNbtWrapper.get(MLuaDataManager.entityPersistentTag(raw), args.checkjstring(2));
       }));
       table.rawset("GetPersistentData", luaFn(() -> MLuaDataManager.entityPersistentData(((MCEntityImpl)wrapped).inner(), !((MCEntityImpl)wrapped).inner().level().isClientSide)));
       table.rawset("GetSyncedData", luaFn(() -> MLuaDataManager.entitySyncedData(((MCEntityImpl)wrapped).inner())));
       table.rawset("getNbt", table.get("GetNbt"));
      table.rawset("SetNbt", varArg((args) -> {
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
         if (raw.level().isClientSide) {
            throw new LuaError("[MLua] Entity persistent data is server-authoritative; use GetSyncedData for client-visible values.");
         }
         CompoundTag persistentData = MLuaDataManager.entityPersistentTag(raw);
          if (args.narg() == 2 && args.arg(2).istable()) {
             for (String key : new HashSet<>(persistentData.getAllKeys())) {
                persistentData.remove(key);
             }
             org.luaj.vm2.LuaTable replacement = args.arg(2).checktable();
             LuaValue key = LuaValue.NIL;
             while (true) {
                Varargs next = replacement.next(key);
                key = next.arg1();
                if (key.isnil()) {
                   break;
                }
                if (key.isstring()) {
                   MLuaNbtWrapper.set(persistentData, key.tojstring(), next.arg(2));
                }
             }
             MLuaDataManager.setEntityPersistentTag(raw, persistentData);
             return LuaValue.NONE;
          }
          MLuaNbtWrapper.set(persistentData, args.checkjstring(2), args.arg(3));
          MLuaDataManager.setEntityPersistentTag(raw, persistentData);
          return LuaValue.NONE;
       }));
      table.rawset("setNbt", table.get("SetNbt"));
      table.rawset("LoadAnimation", varArg((args) -> {
         LuaValue animVal = args.arg(2);
         if (animVal.istable() && animVal.checktable().get("__bbmodel").toboolean()) {
            int renderId = animVal.checktable().get("Id").checkint();
            MLuaClientRenderManager.attachToEntity(renderId, entity);
            return animVal;
         }
         MLuaAnimation anim;
         if (animVal.istable()) {
            LuaTable animTable = animVal.checktable();
            LuaValue animClass = animTable.get("__class");
            if ("Animation".equals(animClass.tojstring())) {
               LuaValue getKeyframes = animTable.get("GetKeyframes");
               if (getKeyframes.isfunction()) {
                  LuaValue kfResult = getKeyframes.call();
                  if (kfResult.istable()) {
                     anim = MLuaAnimation.fromLuaTable(kfResult.checktable());
                  } else {
                     throw new LuaError("[MLua] Animation:GetKeyframes() must return a keyframe table");
                  }
               } else {
                  throw new LuaError("[MLua] Invalid Animation object - missing GetKeyframes");
               }
            } else {
               anim = MLuaAnimation.fromLuaTable(animTable);
            }
         } else {
            throw new LuaError("[MLua] LoadAnimation expects an Animation or keyframe table");
         }
         LuaValue priorityVal = args.arg(3);
         dev.mlua.common.enums.AnimationPriority priority = dev.mlua.common.enums.AnimationPriority.Idle;
         if (priorityVal.isstring()) {
            try {
               priority = dev.mlua.common.enums.AnimationPriority.valueOf(priorityVal.tojstring());
            } catch (IllegalArgumentException e) {
               throw new LuaError("[MLua] Unknown AnimationPriority: " + priorityVal.tojstring());
            }
         }
         float fadeTime = (float) args.arg(4).optdouble(0.0);
         MLuaAnimationTrack track = MLuaAnimationController.loadAnimation(entity, anim, priority);
         if (fadeTime > 0.001f) {
            track.setFadeSpeed(1.0f / fadeTime);
            track.fadeTo(1.0f);
         }
         return MLuaAnimationBindings.wrapTrack(track);
      }));
      table.rawset("ResetAnimation", luaFn(() -> {
         MLuaClientRenderManager.resetEntityModel(entity);
         MLuaAnimationController.stopAll(entity);
         return LuaValue.NONE;
      }));
      table.rawset("LoadModel", table.get("LoadAnimation"));
      table.rawset("ResetModel", table.get("ResetAnimation"));
      table.rawset("PlayAnimation", varArg((args) -> {
         String name = args.checkjstring(2);
         LuaTable options = args.arg(3).opttable(new LuaTable());
         boolean loop = options.get("Loop").optboolean(true);
         float speed = (float) options.get("Speed").optdouble(1.0);
         MLuaClientRenderManager.playEntityAnimation(entity, name, loop, speed);
         return LuaValue.NONE;
      }));
      table.rawset("StopAnimation", luaFn(() -> {
         MLuaClientRenderManager.stopEntityAnimation(entity);
         return LuaValue.NONE;
      }));
      table.rawset("GetPlayingAnimationTracks", luaFn(() -> {
         LuaTable result = new LuaTable();
         int i = 1;
         for (MLuaAnimationTrack track : MLuaAnimationController.getTracks(entity)) {
            result.set(i++, MLuaAnimationBindings.wrapTrack(track));
         }
         return result;
      }));
      table.rawset("StopAllAnimations", luaFn(() -> {
         MLuaAnimationController.stopAll(entity);
         return LuaValue.NONE;
      }));
      table.rawset("SetBonePose", varArg((args) -> {
         String bone = args.checkjstring(2);
         MLuaBonePose.setPose(entity, bone, MLuaCFrame.fromLua(args.arg(3)));
         return LuaValue.NONE;
      }));
      table.rawset("GetBonePose", varArg((args) -> {
         MLuaCFrame pose = MLuaBonePose.getBonePose(entity, args.checkjstring(2));
         return pose != null ? pose.toLua() : LuaValue.NIL;
      }));
      table.rawset("GetBonePoses", luaFn(() -> {
         LuaTable poses = new LuaTable();
         for (Map.Entry<String, MLuaCFrame> entry : MLuaBonePose.getPose(entity).entrySet()) {
            poses.set(entry.getKey(), entry.getValue().toLua());
         }
         return poses;
      }));
      table.rawset("ClearBonePose", varArg((args) -> {
         if (args.narg() >= 2 && !args.arg(2).isnil()) {
            MLuaBonePose.clearBonePose(entity, args.checkjstring(2));
         } else {
            MLuaBonePose.clearPose(entity);
         }
         return LuaValue.NONE;
      }));
      return MLuaServiceProperties.attach(table);
   }

   private static MLuaEntityWrapperCache cacheFor(MLuaRegistration registration) {
      if (registration == null) return LEGACY_CACHE;
      synchronized (ENTITY_CACHES) {
         return ENTITY_CACHES.computeIfAbsent(registration, ignored -> new MLuaEntityWrapperCache());
      }
   }

   private static String entityTypeName(MCEntity entity) {
      try {
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)entity).inner();
         net.minecraft.resources.ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(raw.getType());
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
      net.minecraft.network.chat.Component component = ((MCEntityImpl)entity).inner().getName();
      return component != null ? component.getString() : "";
   }

   public static String entityDisplayName(MCEntity entity) {
      net.minecraft.network.chat.Component component = ((MCEntityImpl)entity).inner().getDisplayName();
      return component != null ? component.getString() : entityName(entity);
   }

   public static boolean isAlive(MCEntity entity) {
      return entity.isAlive();
   }

   public static MCVec3 position(MCEntity entity) {
      return entity.position();
   }

   public static MCVec3 lookAngle(MCEntity entity) {
      net.minecraft.world.phys.Vec3 look = ((MCEntityImpl)entity).inner().getLookAngle();
      return MCVec3Impl.fromReal(look);
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
      entity.setHealth(value);
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

   public static MLuaCFrame cframe(MCEntity entity) {
      MCVec3 look = lookAngle(entity);
      MCVec3 p = entity.position();
      return MLuaCFrame.fromEntity(p.x(), p.y(), p.z(), look.x(), look.y(), look.z());
   }

   public static void applyCFrame(MCEntity entity, MLuaCFrame cf) {
      net.minecraft.world.entity.Entity raw = ((MCEntityImpl)entity).inner();
      raw.teleportTo(cf.x(), cf.y(), cf.z());
      entity.setYRot(cf.minecraftYaw());
      entity.setXRot(cf.minecraftPitch());
      raw.setYHeadRot(cf.minecraftYaw());
      raw.setYBodyRot(cf.minecraftYaw());
      raw.hurtMarked = true;
   }

   public static void applyRotation(MCEntity entity, LuaValue value) {
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
         entity.setYRot((float)yaw);
         entity.setXRot((float)pitch);
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)entity).inner();
         raw.setYHeadRot((float)yaw);
         raw.setYBodyRot((float)yaw);
         raw.hurtMarked = true;
      }
   }

   public static double[] targetPosition(LuaValue value) {
      if (!value.istable()) {
         throw new LuaError("[MLua] Expected Vector3, Entity, or Player target");
      } else {
         LuaTable table = value.checktable();
         LuaValue pos = table.get("Position");
         if (!pos.isnil()) {
            return MLuaCFrame.vector(pos);
         } else {
            LuaValue getPosition = table.get("GetPosition");
            return getPosition.isfunction() ? MLuaCFrame.vector(getPosition.call(table)) : MLuaCFrame.vector(value);
         }
      }
   }

   private static net.minecraft.world.entity.Entity entityFromLua(LuaValue value) {
      if (!value.istable()) {
         return null;
      }
      LuaValue entityValue = value.checktable().rawget("__entity");
      return entityValue.isuserdata(net.minecraft.world.entity.Entity.class) ? (net.minecraft.world.entity.Entity) entityValue.touserdata(net.minecraft.world.entity.Entity.class) : null;
   }

   private static LuaValue luaFn(final Supplier<LuaValue> fn) {
      return new ZeroArgFunction() {
         public LuaValue call() {
            try {
               return (LuaValue)fn.get();
            } catch (LuaError var2) {
               throw var2;
            } catch (Exception var3) {
               throw new LuaError("[MLua] " + var3.getMessage());
            }
         }
      };
   }

   private static LuaValue varArg(final Function<Varargs, LuaValue> fn) {
      return new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            try {
               return (Varargs)fn.apply(args);
            } catch (LuaError var3) {
               throw var3;
            } catch (Exception var4) {
               throw new LuaError("[MLua] " + var4.getMessage());
            }
         }
      };
   }
}
