package dev.mlua.modules.players.Class;

import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.minecraft.animation.MLuaAnimationTrack;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.binding.MLuaAnimationBindings;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.modules.combat.Service.MLuaCombatService;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaNbtWrapper;
import dev.mlua.minecraft.scripted.MLuaScriptedEntity;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectile;
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
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
//--version 1.21.1,26.1
import net.minecraft.core.Holder;
//--endversion
import net.minecraft.core.registries.BuiltInRegistries;
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

   public static void clearSideCache(dev.mlua.common.enums.Side side) {
      if (side == null) return;
      LEGACY_CACHE.removeSide(side);
      synchronized (ENTITY_CACHES) { ENTITY_CACHES.values().forEach(cache -> cache.removeSide(side)); }
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
         MLuaClientModelManager.clearEntityForAllOwners(scope);
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
      return entityTable(entity, MLuaRuntime.registrationForLevel(entity.level()));
    }

    public static LuaTable entityTable(net.minecraft.world.entity.Entity entity, MLuaRegistration registration) {
      if (entity instanceof MLuaScriptedProjectile projectile && !projectile.isResolvingLuaHandle()) {
         return projectile.toLuaHandle(true);
      }
      if (entity instanceof net.minecraft.world.entity.player.Player player) {
         return MLuaPlayerWrapper.playerEntityTable(player, registration);
      }
      if (entity instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
         return entityTable(livingEntity, registration);
      }
      MCEntity wrapped = new MCEntityImpl(entity);
      MLuaEntityWrapperCache.Key cacheKey = MLuaEntityWrapperCache.key(entity);
      MLuaEntityWrapperCache cache = cacheFor(registration);
      LuaTable cached = cache.get(cacheKey);
      if (cached != null && hasEntity(cached, entity)) {
         return cached;
      }

      LuaTable table = cached != null ? cached : new LuaTable();
      table.rawset("__side", LuaValue.valueOf("Shared"));
      table.rawset("__entity", LuaValue.userdataOf(entity));
      attachEntityClass(registration, table);
      if (cached == null) cache.put(cacheKey, table);
      MLuaClassMethods.setInstanceMethod(table, "GetLevel", luaFn(() -> MLuaLevelWrapper.levelTable(entity.level(), registration)));
      MLuaClassMethods.setInstanceMethod(table, "GetDimension", luaFn(() -> LuaValue.valueOf(entity.level().dimension().location().toString())));
      MLuaClassMethods.setInstanceMethod(table, "GetPosition", luaFn(() -> {
         MCVec3 p = wrapped.getPosition();
         return MLuaPlayerWrapper.positionTable(p.x(), p.y(), p.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetPositionX", luaFn(() -> LuaValue.valueOf(wrapped.getPosition().x())));
      MLuaClassMethods.setInstanceMethod(table, "GetPositionY", luaFn(() -> LuaValue.valueOf(wrapped.getPosition().y())));
      MLuaClassMethods.setInstanceMethod(table, "GetPositionZ", luaFn(() -> LuaValue.valueOf(wrapped.getPosition().z())));
      MLuaClassMethods.setInstanceMethod(table, "SetPosition", varArg((args) -> {
         double[] pos = MLuaPlayerWrapper.positionArgs(args, 2);
         ((MCEntityImpl)wrapped).inner().setPos(pos[0], pos[1], pos[2]);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "Teleport", varArg((args) -> {
         double[] pos = MLuaPlayerWrapper.positionArgs(args, 2);
         ((MCEntityImpl)wrapped).inner().teleportTo(pos[0], pos[1], pos[2]);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetVelocity", luaFn(() -> {
         MCVec3 v = wrapped.getDeltaMovement();
         return MLuaPlayerWrapper.positionTable(v.x(), v.y(), v.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetVelocity", varArg((args) -> {
         double[] vel = MLuaPlayerWrapper.positionArgs(args, 2);
         wrapped.setDeltaMovement(MCVec3.of(vel[0], vel[1], vel[2]));
         ((MCEntityImpl)wrapped).inner().hurtMarked = true;
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetRotation", luaFn(() -> MLuaVector.vector2((double)wrapped.getXRot(), (double)wrapped.getYRot())));
      MLuaClassMethods.setInstanceMethod(table, "GetRotationRadians", luaFn(() -> MLuaVector.vector2(Math.toRadians((double)wrapped.getXRot()), Math.toRadians((double)wrapped.getYRot()))));
      MLuaClassMethods.setInstanceMethod(table, "SetRotationRadians", varArg((args) -> {
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
      MLuaClassMethods.setInstanceMethod(table, "GetCFrame", luaFn(() -> cframe(wrapped).toLua()));
      MLuaClassMethods.setInstanceMethod(table, "GetLookVector", luaFn(() -> {
         MCVec3 look = lookAngle(wrapped);
         return MLuaPlayerWrapper.positionTable(look.x(), look.y(), look.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetCFrame", varArg((args) -> {
         MLuaCFrame cf = MLuaCFrame.fromLua(args.arg(2));
         applyCFrame(wrapped, cf);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetRotation", varArg((args) -> {
         applyRotation(wrapped, args.arg(2));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "LookAt", varArg((args) -> {
         double[] target = targetPosition(args.arg(2));
         MCVec3 p = wrapped.getPosition();
         applyCFrame(wrapped, MLuaCFrame.lookAt(p.x(), p.y(), p.z(), target[0], target[1], target[2]));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "Kill", luaFn(() -> {
         ((MCEntityImpl)wrapped).inner().kill();
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "Remove", luaFn(() -> {
         ((MCEntityImpl)wrapped).inner().remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "DistanceTo", varArg((args) -> {
         double[] other = MLuaPlayerWrapper.positionArgs(args, 2);
         MCVec3 p = wrapped.getPosition();
         double dx = p.x() - other[0];
         double dy = p.y() - other[1];
         double dz = p.z() - other[2];
         return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy + dz * dz));
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetIsAlive", luaFn(() -> LuaValue.valueOf(wrapped.isAlive())));
      MLuaClassMethods.setInstanceMethod(table, "GetIsOnGround", luaFn(() -> LuaValue.valueOf(((MCEntityImpl)wrapped).inner().onGround())));
      MLuaClassMethods.setInstanceMethod(table, "GetIsInWater", luaFn(() -> LuaValue.valueOf(((MCEntityImpl)wrapped).inner().isInWater())));
      MLuaClassMethods.setInstanceMethod(table, "GetIsOnFire", luaFn(() -> LuaValue.valueOf(((MCEntityImpl)wrapped).inner().isOnFire())));
      MLuaClassMethods.setInstanceMethod(table, "GetIsMoving", luaFn(() -> {
         MCVec3 v = wrapped.getDeltaMovement();
         double hSpeed = Math.sqrt(v.x() * v.x() + v.z() * v.z());
         return LuaValue.valueOf(hSpeed > 0.05);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetIsFalling", luaFn(() -> {
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
         return LuaValue.valueOf(!raw.onGround() && raw.getDeltaMovement().y < -0.08);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetNbt", varArg((args) -> {
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
         if (args.narg() < 2 || args.arg(2).isnil()) {
            return MLuaDataManager.entityPersistentData(raw, !raw.level().isClientSide);
         }
         if (raw.level().isClientSide) return LuaValue.NIL;
         return MLuaNbtWrapper.get(MLuaDataManager.entityPersistentTag(raw), args.checkjstring(2));
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetPersistentData", luaFn(() -> MLuaDataManager.entityPersistentData(((MCEntityImpl)wrapped).inner(), !((MCEntityImpl)wrapped).inner().level().isClientSide)));
      MLuaClassMethods.setInstanceMethod(table, "GetSyncedData", luaFn(() -> MLuaDataManager.entitySyncedData(((MCEntityImpl)wrapped).inner())));
      MLuaClassMethods.setInstanceMethod(table, "getNbt", table.get("GetNbt"));
      MLuaClassMethods.setInstanceMethod(table, "SetNbt", varArg((args) -> {
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
         if (raw.level().isClientSide) {
            throw new LuaError("[MLua] Entity persistent data is server-authoritative; use GetSyncedData for client-visible values.");
         }
         if (args.narg() == 2 && args.arg(2).istable()) {
            MLuaDataManager.setEntityPersistentTag(raw, MLuaNbtWrapper.toCompound(args.arg(2)));
            return LuaValue.NONE;
         }
         net.minecraft.nbt.CompoundTag persistentData = MLuaDataManager.entityPersistentTag(raw);
         MLuaNbtWrapper.set(persistentData, args.checkjstring(2), args.arg(3));
         MLuaDataManager.setEntityPersistentTag(raw, persistentData);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "setNbt", table.get("SetNbt"));
      return attachEntityProperties(table);
   }

    public static LuaTable entityTable(net.minecraft.world.entity.LivingEntity entity) {
      return entityTable(entity, MLuaRuntime.registrationForLevel(entity.level()));
    }

    public static LuaTable entityTable(net.minecraft.world.entity.LivingEntity entity, MLuaRegistration registration) {
      if (entity instanceof net.minecraft.world.entity.player.Player player) {
         return MLuaPlayerWrapper.playerEntityTable(player, registration);
      }
      return livingEntityTable(entity, registration);
    }

    public static LuaTable livingEntityTable(net.minecraft.world.entity.LivingEntity entity, MLuaRegistration registration) {
      MCLivingEntity wrapped = new MCLivingEntityImpl(entity);
      MLuaEntityWrapperCache.Key cacheKey = MLuaEntityWrapperCache.key(entity);
      MLuaEntityWrapperCache cache = cacheFor(registration);
      LuaTable cached = cache.get(cacheKey);
      if (cached != null && hasEntity(cached, entity)) {
         if (entity instanceof MLuaScriptedEntity scripted) scripted.attachLuaClass(cached, registration);
         return cached;
      }
       LuaTable table = cached != null ? cached : new LuaTable();
       table.rawset("__side", LuaValue.valueOf("Shared"));
       table.rawset("__entity", LuaValue.userdataOf(entity));
       attachLivingEntityClass(registration, table);
       if (cached == null) cache.put(cacheKey, table);
      MLuaClassMethods.setInstanceMethod(table, "GetPosition", luaFn(() -> {
         MCVec3 p = wrapped.getPosition();
         return MLuaPlayerWrapper.positionTable(p.x(), p.y(), p.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetPositionX", luaFn(() -> LuaValue.valueOf(wrapped.getPosition().x())));
      MLuaClassMethods.setInstanceMethod(table, "GetPositionY", luaFn(() -> LuaValue.valueOf(wrapped.getPosition().y())));
      MLuaClassMethods.setInstanceMethod(table, "GetPositionZ", luaFn(() -> LuaValue.valueOf(wrapped.getPosition().z())));
      MLuaClassMethods.setInstanceMethod(table, "SetPosition", varArg((args) -> {
         double[] pos = MLuaPlayerWrapper.positionArgs(args, 2);
         ((MCEntityImpl)wrapped).inner().setPos(pos[0], pos[1], pos[2]);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "Teleport", varArg((args) -> {
         double[] pos = MLuaPlayerWrapper.positionArgs(args, 2);
         ((MCEntityImpl)wrapped).inner().teleportTo(pos[0], pos[1], pos[2]);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetVelocity", luaFn(() -> {
         MCVec3 v = wrapped.getDeltaMovement();
         return MLuaPlayerWrapper.positionTable(v.x(), v.y(), v.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetVelocity", varArg((args) -> {
         double[] vel = MLuaPlayerWrapper.positionArgs(args, 2);
         wrapped.setDeltaMovement(MCVec3.of(vel[0], vel[1], vel[2]));
         ((MCEntityImpl)wrapped).inner().hurtMarked = true;
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetHealth", luaFn(() -> {
         return LuaValue.valueOf((double)wrapped.getHealth());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetHealth", varArg((args) -> {
         wrapped.setHealth((float)args.checkdouble(2));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetMaxHealth", luaFn(() -> {
         return LuaValue.valueOf((double)wrapped.getMaxHealth());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetArmor", luaFn(() -> {
         return LuaValue.valueOf(wrapped.getArmorValue());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetRotation", luaFn(() -> {
         return MLuaVector.vector2((double)wrapped.getXRot(), (double)wrapped.getYRot());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetRotationRadians", luaFn(() -> {
         return MLuaVector.vector2(Math.toRadians((double)wrapped.getXRot()), Math.toRadians((double)wrapped.getYRot()));
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetRotationRadians", varArg((args) -> {
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
      MLuaClassMethods.setInstanceMethod(table, "GetCFrame", luaFn(() -> {
         return cframe(wrapped).toLua();
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetLookVector", luaFn(() -> {
         MCVec3 look = lookAngle(wrapped);
         return MLuaPlayerWrapper.positionTable(look.x(), look.y(), look.z());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetCFrame", varArg((args) -> {
         MLuaCFrame cf = MLuaCFrame.fromLua(args.arg(2));
         applyCFrame(wrapped, cf);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetRotation", varArg((args) -> {
         applyRotation(wrapped, args.arg(2));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "LookAt", varArg((args) -> {
         double[] target = targetPosition(args.arg(2));
         MCVec3 p = wrapped.getPosition();
         applyCFrame(wrapped, MLuaCFrame.lookAt(p.x(), p.y(), p.z(), target[0], target[1], target[2]));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "Kill", luaFn(() -> {
         ((MCEntityImpl)wrapped).inner().kill();
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "Remove", luaFn(() -> {
         ((MCEntityImpl)wrapped).inner().remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
         return LuaValue.NONE;
      }));
       MLuaClassMethods.setInstanceMethod(table, "Damage", varArg((args) -> {
          float amount = (float)args.checkdouble(2);
          String sourceStr = args.optjstring(3, "generic");
          net.minecraft.world.entity.Entity attacker = entityFromLua(args.arg(4));
          Object runtimeOwner = registration instanceof MLuaRuntime runtime ? runtime.session() : null;
          String damageOwner = "";
          String damageName = sourceStr;
          int namespaceSeparator = sourceStr.indexOf(':');
          if (namespaceSeparator > 0) {
             damageOwner = sourceStr.substring(0, namespaceSeparator);
             damageName = sourceStr.substring(namespaceSeparator + 1);
          }
          net.minecraft.world.damagesource.DamageSource mcSource = MLuaCombatService.createDamageSource(((MCEntityImpl)wrapped).inner(), damageName, attacker, runtimeOwner, damageOwner);
          wrapped.hurt(new MCDamageSourceImpl(mcSource), amount);
          return LuaValue.NONE;
       }));
       MLuaClassMethods.setInstanceMethod(table, "GetAttribute", varArg((args) -> {
         String attrName = args.checkjstring(2);
         //--version 1.21.1
         Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = BuiltInRegistries.ATTRIBUTE.getHolder(MLuaResourceLocations.parse(attrName)).orElse(null);
         //--endversion
         //--version 26.1
         Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName)).orElse(null);
         //--endversion
         //--version 1.20.1
         net.minecraft.world.entity.ai.attributes.Attribute attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName));
         //--endversion
         if (attr == null) {
            return LuaValue.NIL;
         }
         net.minecraft.world.entity.ai.attributes.AttributeInstance instance = ((net.minecraft.world.entity.LivingEntity)((MCLivingEntityImpl)wrapped).inner()).getAttribute(attr);
          return instance == null ? LuaValue.NIL : LuaValue.valueOf(instance.getValue());
       }));
       MLuaClassMethods.setInstanceMethod(table, "GetAttributeInstance", varArg((args) -> {
          String attrName = args.checkjstring(2);
          //--version 1.21.1
          Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = BuiltInRegistries.ATTRIBUTE.getHolder(MLuaResourceLocations.parse(attrName)).orElse(null);
          //--endversion
          //--version 26.1
          Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName)).orElse(null);
          //--endversion
          //--version 1.20.1
          net.minecraft.world.entity.ai.attributes.Attribute attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName));
          //--endversion
          if (attr == null) return LuaValue.NIL;
          net.minecraft.world.entity.ai.attributes.AttributeInstance instance = ((net.minecraft.world.entity.LivingEntity)((MCLivingEntityImpl)wrapped).inner()).getAttribute(attr);
          return instance == null ? LuaValue.NIL : dev.mlua.minecraft.binding.MLuaAttributeInstance.create(registration,
             MLuaResourceLocations.parse(attrName).toString(), instance, args.arg1().checktable());
       }));
       MLuaClassMethods.setInstanceMethod(table, "SetAttribute", varArg((args) -> {
          String attrName = args.checkjstring(2);
          double value = args.checkdouble(3);
          //--version 1.21.1
          Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = BuiltInRegistries.ATTRIBUTE.getHolder(MLuaResourceLocations.parse(attrName)).orElse(null);
          //--endversion
          //--version 26.1
          Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName)).orElse(null);
          //--endversion
          //--version 1.20.1
          net.minecraft.world.entity.ai.attributes.Attribute attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName));
          //--endversion
          if (attr == null) {
             return LuaValue.FALSE;
          }
          net.minecraft.world.entity.ai.attributes.AttributeInstance instance = ((net.minecraft.world.entity.LivingEntity)((MCLivingEntityImpl)wrapped).inner()).getAttribute(attr);
         if (instance == null) {
            return LuaValue.FALSE;
         }
         instance.setBaseValue(value);
         return LuaValue.TRUE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetEquipment", varArg((args) -> {
         net.minecraft.world.entity.EquipmentSlot slot = MLuaMinecraftEnums.resolveSlot(args.arg(2));
          return MLuaItemWrapper.itemTable(((net.minecraft.world.entity.LivingEntity)((MCLivingEntityImpl)wrapped).inner()).getItemBySlot(slot), registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetEquipment", varArg((args) -> {
         net.minecraft.world.entity.EquipmentSlot slot = MLuaMinecraftEnums.resolveSlot(args.arg(2));
         net.minecraft.world.item.ItemStack stack = MLuaItemWrapper.toItemStack(args.arg(3));
         //--version 1.21.1
         ((net.minecraft.world.entity.LivingEntity)((MCLivingEntityImpl)wrapped).inner()).setItemSlot(slot, stack);
         //--endversion
         //--version 1.20.1
         ((MCLivingEntityImpl)wrapped).inner().setItemSlot(slot, stack);
         //--endversion
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "DistanceTo", varArg((args) -> {
         double[] other = MLuaPlayerWrapper.positionArgs(args, 2);
         MCVec3 p = wrapped.getPosition();
         double dx = p.x() - other[0];
         double dy = p.y() - other[1];
         double dz = p.z() - other[2];
         return LuaValue.valueOf(Math.sqrt(dx * dx + dy * dy + dz * dz));
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetIsAlive", luaFn(() -> {
         return LuaValue.valueOf(wrapped.isAlive());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetIsOnGround", luaFn(() -> {
         return LuaValue.valueOf(((MCEntityImpl)wrapped).inner().onGround());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetIsInWater", luaFn(() -> {
         return LuaValue.valueOf(((MCEntityImpl)wrapped).inner().isInWater());
      }));
       MLuaClassMethods.setInstanceMethod(table, "GetIsOnFire", luaFn(() -> {
          return LuaValue.valueOf(((MCEntityImpl)wrapped).inner().isOnFire());
       }));
       MLuaClassMethods.setInstanceMethod(table, "GetIsMoving", luaFn(() -> {
          MCVec3 v = wrapped.getDeltaMovement();
          double hSpeed = Math.sqrt(v.x() * v.x() + v.z() * v.z());
          return LuaValue.valueOf(hSpeed > 0.05);
       }));
       MLuaClassMethods.setInstanceMethod(table, "GetIsJumping", luaFn(() -> {
          net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
          return LuaValue.valueOf(!raw.onGround() && raw.getDeltaMovement().y > 0.08);
       }));
       MLuaClassMethods.setInstanceMethod(table, "GetIsFalling", luaFn(() -> {
          net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
          return LuaValue.valueOf(!raw.onGround() && raw.getDeltaMovement().y < -0.08);
       }));
       MLuaClassMethods.setInstanceMethod(table, "GetIsSwimming", luaFn(() -> {
          return LuaValue.valueOf(((MCLivingEntityImpl)wrapped).inner().isSwimming());
       }));
       MLuaClassMethods.setInstanceMethod(table, "GetNbt", varArg((args) -> {
          net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
          if (args.narg() < 2 || args.arg(2).isnil()) {
             return MLuaDataManager.entityPersistentData(raw, !raw.level().isClientSide);
          }
          if (raw.level().isClientSide) return LuaValue.NIL;
          return MLuaNbtWrapper.get(MLuaDataManager.entityPersistentTag(raw), args.checkjstring(2));
       }));
      MLuaClassMethods.setInstanceMethod(table, "GetPersistentData", luaFn(() -> MLuaDataManager.entityPersistentData(((MCEntityImpl)wrapped).inner(), !((MCEntityImpl)wrapped).inner().level().isClientSide)));
      MLuaClassMethods.setInstanceMethod(table, "GetSyncedData", luaFn(() -> MLuaDataManager.entitySyncedData(((MCEntityImpl)wrapped).inner())));
      MLuaClassMethods.setInstanceMethod(table, "getNbt", table.get("GetNbt"));
      MLuaClassMethods.setInstanceMethod(table, "SetNbt", varArg((args) -> {
         net.minecraft.world.entity.Entity raw = ((MCEntityImpl)wrapped).inner();
          if (raw.level().isClientSide) {
             throw new LuaError("[MLua] Entity persistent data is server-authoritative; use GetSyncedData for client-visible values.");
          }
         if (args.narg() == 2 && args.arg(2).istable()) {
             MLuaDataManager.setEntityPersistentTag(raw, MLuaNbtWrapper.toCompound(args.arg(2)));
             return LuaValue.NONE;
          }
          net.minecraft.nbt.CompoundTag persistentData = MLuaDataManager.entityPersistentTag(raw);
          MLuaNbtWrapper.set(persistentData, args.checkjstring(2), args.arg(3));
          MLuaDataManager.setEntityPersistentTag(raw, persistentData);
          return LuaValue.NONE;
       }));
       MLuaClassMethods.setInstanceMethod(table, "setNbt", table.get("SetNbt"));
      MLuaClassMethods.setInstanceMethod(table, "LoadAnimation", varArg((args) -> {
         LuaValue animVal = args.arg(2);
         if (animVal.istable() && animVal.checktable().get("__bbmodel").toboolean()) {
            int renderId = animVal.checktable().get("Id").checkint();
            Object runtimeOwner = registration instanceof MLuaRuntime runtime ? runtime.session() : null;
            var renderOwner = new dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner(
                  runtimeOwner, animVal.checktable().get("__mluaOwnerModId").optjstring(""));
            if (!MLuaClientRenderManager.attachToEntity(renderId, entity, renderOwner)) {
               throw new LuaError("[MLua] BBModel render object belongs to another runtime");
            }
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
         MLuaAnimationTrack track = MLuaAnimationController.loadAnimation(entity, anim, priority, registration);
         if (fadeTime > 0.001f) {
            track.setFadeSpeed(1.0f / fadeTime);
            track.fadeTo(1.0f);
         }
         return MLuaAnimationBindings.wrapTrack(track, registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "ResetAnimation", luaFn(() -> {
         MLuaClientRenderManager.resetEntityModel(entity);
         MLuaAnimationController.stopAll(entity, registration);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "LoadModel", table.get("LoadAnimation"));
      MLuaClassMethods.setInstanceMethod(table, "ResetModel", table.get("ResetAnimation"));
      MLuaClassMethods.setInstanceMethod(table, "PlayAnimation", varArg((args) -> {
         String name = args.checkjstring(2);
         LuaTable options = args.arg(3).opttable(new LuaTable());
         boolean loop = options.get("Loop").optboolean(true);
         float speed = (float) options.get("Speed").optdouble(1.0);
         MLuaClientRenderManager.playEntityAnimation(entity, name, loop, speed);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "StopAnimation", luaFn(() -> {
         MLuaClientRenderManager.stopEntityAnimation(entity);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetPlayingAnimationTracks", luaFn(() -> {
         LuaTable result = new LuaTable();
         int i = 1;
         for (MLuaAnimationTrack track : MLuaAnimationController.getTracks(entity, registration)) {
            result.set(i++, MLuaAnimationBindings.wrapTrack(track, registration));
         }
         return result;
      }));
      MLuaClassMethods.setInstanceMethod(table, "StopAllAnimations", luaFn(() -> {
         MLuaAnimationController.stopAll(entity, registration);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetBonePose", varArg((args) -> {
         String bone = args.checkjstring(2);
         MLuaBonePose.setPose(entity, bone, MLuaCFrame.fromLua(args.arg(3)), registration);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetBonePose", varArg((args) -> {
         MLuaCFrame pose = MLuaBonePose.getBonePose(entity, args.checkjstring(2), registration);
         return pose != null ? pose.toLua() : LuaValue.NIL;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetBonePoses", luaFn(() -> {
         LuaTable poses = new LuaTable();
         for (Map.Entry<String, MLuaCFrame> entry : MLuaBonePose.getPose(entity, registration).entrySet()) {
            poses.set(entry.getKey(), entry.getValue().toLua());
         }
         return poses;
      }));
      MLuaClassMethods.setInstanceMethod(table, "ClearBonePose", varArg((args) -> {
         if (args.narg() >= 2 && !args.arg(2).isnil()) {
            MLuaBonePose.clearBonePose(entity, args.checkjstring(2), registration);
         } else {
            MLuaBonePose.clearPose(entity, registration);
         }
         return LuaValue.NONE;
      }));
      if (entity instanceof MLuaScriptedEntity scripted) scripted.attachLuaClass(table, registration);
      return attachEntityProperties(table);
   }

   static MLuaEntityWrapperCache cacheFor(MLuaRegistration registration) {
      if (registration == null) return LEGACY_CACHE;
      synchronized (ENTITY_CACHES) {
         return ENTITY_CACHES.computeIfAbsent(registration, ignored -> new MLuaEntityWrapperCache());
      }
   }

   public static LuaTable cachedPlayerTable(net.minecraft.world.entity.Entity player, MLuaRegistration registration) {
      return cacheFor(registration).getPlayer(MLuaEntityWrapperCache.key(player));
   }

   public static void cachePlayerTable(net.minecraft.world.entity.Entity player, MLuaRegistration registration, LuaTable table) {
      cacheFor(registration).putPlayer(MLuaEntityWrapperCache.key(player), table);
   }

   public static LuaTable attachEntityProperties(LuaTable table) {
      Map<String, LuaValue> getters = new java.util.HashMap<>();
      for (String property : new String[]{"Id", "Type", "EntityType", "Name", "UUID"}) {
         LuaValue getter = table.get("Get" + property);
         if (getter.isfunction()) getters.put(property, getter);
      }
      return MLuaServiceProperties.attach(table, getters, java.util.Map.of());
   }

   private static void attachEntityClass(MLuaRegistration registration, LuaTable table) {
      if (registration == null) throw new IllegalStateException("Lua registration is required for Entity instances");
      registration.attachClassInstance(table, "Entity", registration.requireInstanceBaseClassName());
   }

   private static void attachLivingEntityClass(MLuaRegistration registration, LuaTable table) {
      if (registration == null) throw new IllegalStateException("Lua registration is required for LivingEntity instances");
      registration.attachClassInstance(table, "LivingEntity", "Entity");
   }

   private static boolean hasEntity(LuaTable table, net.minecraft.world.entity.Entity entity) {
      LuaValue value = table.rawget("__entity");
      return value.isuserdata(net.minecraft.world.entity.Entity.class)
         && value.touserdata(net.minecraft.world.entity.Entity.class) == entity;
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
      return entity.getPosition();
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
      MCVec3 p = entity.getPosition();
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
