package dev.mlua.modules.players.classes;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaAnimationBindings;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.entity.MCLivingEntity;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mc.impl.entity.MCLivingEntityImpl;
import dev.mlua.minecraft.scripted.MLuaScriptedEntity;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Creates Lua class instances for native and scripted Minecraft entities. */
final class MLuaEntityInstanceFactory {
   private MLuaEntityInstanceFactory() { }

    static LuaTable entityTable(Entity entity) {
      return entityTable(entity, MLuaRuntime.registrationForLevel(entity.level()));
    }

    static LuaTable entityTable(Entity entity, MLuaRegistration registration) {
      if (entity instanceof MLuaScriptedProjectile projectile && !projectile.isResolvingLuaHandle()) {
         return projectile.toLuaHandle(true);
      }
      if (entity instanceof Player player) {
         return MLuaPlayerWrapper.playerEntityTable(player, registration);
      }
      if (entity instanceof LivingEntity livingEntity) {
         return MLuaEntityInstanceFactory.entityTable(livingEntity, registration);
      }
      MCEntity wrapped = new MCEntityImpl(entity);
      MLuaEntityWrapperCache.Key cacheKey = MLuaEntityWrapperCache.key(entity);
      MLuaEntityWrapperCache cache = MLuaEntityInstanceCache.cacheFor(registration);
      LuaTable cached = cache.get(cacheKey);
      if (cached != null && hasEntity(cached, entity)) {
         return cached;
      }

      LuaTable table = cached != null ? cached : new LuaTable();
      table.rawset("__side", LuaValue.valueOf("Shared"));
      table.rawset("__entity", LuaValue.userdataOf(entity));
      attachEntityClass(registration, table);
      if (cached == null) cache.put(cacheKey, table);
      MLuaEntityWrapper.bindLevelMethods(table, entity.level(), registration);
      MLuaEntityTransformBindings.bind(table, wrapped);
      MLuaEntityActionBindings.bind(table, wrapped);
      MLuaEntityStateBindings.bindSharedState(table, wrapped);
      MLuaEntityStateBindings.bindPersistentData(table, entity);
      MLuaAnimationBindings.bindEntityMethods(table, entity, registration);
      return MLuaEntityWrapper.attachEntityProperties(table);
   }

    static LuaTable entityTable(LivingEntity entity) {
      return entityTable(entity, MLuaRuntime.registrationForLevel(entity.level()));
    }

    static LuaTable entityTable(LivingEntity entity, MLuaRegistration registration) {
      if (entity instanceof Player player) {
         return MLuaPlayerWrapper.playerEntityTable(player, registration);
      }
      return livingEntityTable(entity, registration);
    }

    static LuaTable livingEntityTable(LivingEntity entity, MLuaRegistration registration) {
      if (entity instanceof Player player) {
         return MLuaPlayerWrapper.playerEntityTable(player, registration);
      }
      return buildLivingEntityTable(entity, registration, true);
    }

    /** Builds the shared living-entity surface before a Player subclass is attached. */
    static LuaTable livingEntityTableBase(LivingEntity entity, MLuaRegistration registration) {
      return buildLivingEntityTable(entity, registration, false);
    }

    private static LuaTable buildLivingEntityTable(LivingEntity entity, MLuaRegistration registration,
                                                   boolean attachProperties) {
      MCLivingEntity wrapped = new MCLivingEntityImpl(entity);
      MLuaEntityWrapperCache.Key cacheKey = MLuaEntityWrapperCache.key(entity);
      MLuaEntityWrapperCache cache = MLuaEntityInstanceCache.cacheFor(registration);
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
      MLuaEntityTransformBindings.bind(table, wrapped);
      MLuaEntityWrapper.bindLevelMethods(table, entity.level(), registration);
      MLuaLivingEntityBindings.bind(table, entity, wrapped, registration);
      MLuaEntityActionBindings.bind(table, wrapped);
       MLuaEntityStateBindings.bindSharedState(table, wrapped);
       MLuaEntityStateBindings.bindLivingState(table, entity);
       MLuaEntityStateBindings.bindPersistentData(table, entity);
      MLuaAnimationBindings.bindEntityMethods(table, entity, registration);
      if (entity instanceof MLuaScriptedEntity scripted) scripted.attachLuaClass(table, registration);
      return attachProperties ? MLuaEntityWrapper.attachEntityProperties(table) : table;
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
      return value.isuserdata(Entity.class) && value.touserdata(Entity.class) == entity;
   }
}
