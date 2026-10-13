package dev.mlua.modules.combat;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectile;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectileType;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MLuaProjectileDefinition {
   private static final Map<Key, ProjectileDef> DEFINITIONS = new ConcurrentHashMap<>();

   private MLuaProjectileDefinition() {}

   public static void register(String name, LuaTable props) {
      register(null, "", name, props, null);
   }

   public static void register(Object runtimeOwner, String ownerModId, String name, LuaTable props, MLuaContext context) {
      if (name == null || name.isBlank()) {
         throw new LuaError("[MLua] Projectile name cannot be empty");
      }
      if (props == null) {
         throw new LuaError("[MLua] Projectile '" + name + "' requires a properties table");
      }
      ownerModId = ownerModId == null ? "" : ownerModId;
      Key key = new Key(runtimeOwner, ownerModId, name);
      double speed = props.get("Speed").optdouble(60.0);
      double damage = props.get("Damage").optdouble(10.0);
      boolean gravity = props.get("Gravity").optboolean(false);
      boolean piercing = props.get("Piercing").optboolean(false);
      double maxDistance = props.get("MaxDistance").optdouble(200.0);
      int maxTicks = props.get("MaxTicks").optint(200);
      float size = (float)props.get("Size").optdouble(0.25);
      String damageSource = props.get("DamageSource").optjstring("generic");
      int fireTicks = props.get("FireTicks").optint(0);
      double knockback = props.get("Knockback").optdouble(0.0);
      LuaValue onHitEntity = props.get("OnHitEntity");
      LuaValue onHitBlock = props.get("OnHitBlock");
      LuaValue onExpired = props.get("OnExpired");
      LuaValue onTick = props.get("OnTick");
      ProjectileDef definition = new ProjectileDef(speed, damage, gravity, piercing, maxDistance, maxTicks, size, damageSource, fireTicks, knockback,
         onHitEntity.isnil() ? LuaValue.NIL : onHitEntity,
         onHitBlock.isnil() ? LuaValue.NIL : onHitBlock,
         onExpired.isnil() ? LuaValue.NIL : onExpired,
         onTick.isnil() ? LuaValue.NIL : onTick, runtimeOwner, ownerModId, context);
      if (DEFINITIONS.putIfAbsent(key, definition) != null) {
         throw new LuaError("[MLua] Projectile '" + name + "' is already registered");
      }
      MLuaLogger.LOGGER.info("Registered projectile '{}'", name);
   }

   public static ProjectileDef get(String name) {
      return get(null, "", name);
   }

   public static ProjectileDef get(Object runtimeOwner, String ownerModId, String name) {
      return DEFINITIONS.get(new Key(runtimeOwner, ownerModId, name));
   }

   public static boolean exists(String name) {
      return DEFINITIONS.containsKey(new Key(null, "", name));
   }

   public static LuaTable createTypeLuaTable(String name) {
      return createTypeLuaTable(null, "", name, MLuaRuntime.get());
   }

   public static LuaTable createTypeLuaTable(Object runtimeOwner, String ownerModId, String name) {
      return createTypeLuaTable(runtimeOwner, ownerModId, name, MLuaRuntime.get());
   }

   public static LuaTable createTypeLuaTable(Object runtimeOwner, String ownerModId, String name,
                                             MLuaRegistration registration) {
      ProjectileDef def = DEFINITIONS.get(new Key(runtimeOwner, ownerModId, name));
      if (def == null) {
         throw new LuaError("[MLua] Unknown projectile type: " + name);
      }
      LuaTable table = new LuaTable();
      table.set("__side", "Server");
      table.set("Name", name);
      table.set("Speed", def.speed);
      table.set("Damage", def.damage);
      table.set("Gravity", LuaValue.valueOf(def.gravity));
      table.set("Piercing", LuaValue.valueOf(def.piercing));
      table.set("MaxDistance", def.maxDistance);
      table.set("Size", def.size);
      table.set("DamageSource", def.damageSource);
      table.set("FireTicks", def.fireTicks);
      table.set("Knockback", def.knockback);
      table.set("Fire", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            LuaValue self = args.arg(1);
            String typeName = self.get("Name").optjstring(null);
            if (typeName == null) {
               throw new LuaError("[MLua] Fire called on invalid projectile type table (missing Name)");
            }
            LuaTable fireConfig = args.arg(2).checktable();
            return fireProjectile(runtimeOwner, ownerModId, typeName, fireConfig);
         }
      });
      return registration.attachClassInstance(table, "ProjectileType", null);
   }

   private static LuaValue fireProjectile(Object runtimeOwner, String ownerModId, String typeName, LuaTable config) {
      try {
         ProjectileDef def = DEFINITIONS.get(new Key(runtimeOwner, ownerModId, typeName));
         if (def == null) {
            throw new LuaError("[MLua] Unknown projectile type: " + typeName);
         }

         LuaValue ownerVal = config.get("Owner");
         if (!ownerVal.istable()) {
            throw new LuaError("[MLua] Fire requires an Owner entity");
         }
         LuaValue entityVal = ownerVal.checktable().get("__entity");
         if (!entityVal.isuserdata(Entity.class)) {
            throw new LuaError("[MLua] Fire requires a valid Owner entity");
         }
         Entity owner = (Entity)entityVal.checkuserdata(Entity.class);
         Level level = owner.level();
         if (level.isClientSide()) {
            throw new LuaError("[MLua] FireProjectile must be called on the server");
         }

         double ox = optVector(config, "Position", "X", owner.getX());
         double oy = optVector(config, "Position", "Y", owner.getY() + 1.0);
         double oz = optVector(config, "Position", "Z", owner.getZ());

         LuaValue dirVal = config.get("Direction");
         Vec3 dir;
         if (dirVal.istable() && dirVal.checktable().get("X").isnumber()) {
            LuaTable dirT = dirVal.checktable();
            dir = new Vec3(dirT.get("X").checkdouble(), dirT.get("Y").checkdouble(), dirT.get("Z").checkdouble()).normalize();
         } else {
            dir = owner.getLookAngle();
         }

         double speed = config.get("Speed").optdouble(def.speed);
         double damage = config.get("Damage").optdouble(def.damage);
         boolean gravity = config.get("Gravity").optboolean(def.gravity);
         boolean piercing = config.get("Piercing").optboolean(def.piercing);
         double maxDistance = config.get("MaxDistance").optdouble(def.maxDistance);
         int maxTicks = config.get("MaxTicks").optint(def.maxTicks);
         float size = (float)config.get("Size").optdouble(def.size);
         String damageSource = config.get("DamageSource").optjstring(def.damageSource);
         int fireTicks = config.get("FireTicks").optint(def.fireTicks);
         double knockback = config.get("Knockback").optdouble(def.knockback);

         LuaValue onHitEntity = config.get("OnHitEntity").isnil() ? def.onHitEntity : config.get("OnHitEntity");
         LuaValue onHitBlock = config.get("OnHitBlock").isnil() ? def.onHitBlock : config.get("OnHitBlock");
         LuaValue onExpired = config.get("OnExpired").isnil() ? def.onExpired : config.get("OnExpired");
         LuaValue onTick = config.get("OnTick").isnil() ? def.onTick : config.get("OnTick");

         EntityType<MLuaScriptedProjectile> projType = MLuaScriptedProjectileType.get();
         if (projType == null) {
            throw new LuaError("[MLua] Projectile entity type not yet registered");
         }

         MLuaScriptedProjectile projectile = new MLuaScriptedProjectile(projType, level);
         projectile.setPos(ox, oy, oz);
         projectile.init(dir, speed, damage, gravity, piercing, maxDistance, maxTicks, size, owner, damageSource, fireTicks, knockback, onHitEntity, onHitBlock, onExpired, onTick, runtimeOwner, ownerModId, def.context);

         if (!level.addFreshEntity(projectile)) {
            throw new LuaError("[MLua] Minecraft rejected projectile '" + typeName + "'");
         }
         return projectile.toLuaHandle(false);
      } catch (LuaError error) {
         throw error;
      } catch (Exception exception) {
         MLuaLogger.LOGGER.error("Failed to fire projectile '{}'", typeName, exception);
         throw new LuaError("[MLua] Failed to fire projectile '" + typeName + "': " + exception.getMessage());
      }
   }

   private static double optVector(LuaTable table, String tableKey, String field, double fallback) {
      LuaValue inner = table.get(tableKey);
      if (inner.istable()) {
         LuaValue val = inner.checktable().get(field);
         return val.isnumber() ? val.todouble() : fallback;
      }
      return fallback;
   }

   public static void clearOwned(Object runtimeOwner, String ownerModId) {
      DEFINITIONS.keySet().removeIf(key -> key.runtimeOwner == runtimeOwner && key.ownerModId.equals(ownerModId));
   }

   public static void clearRuntime(Object runtimeOwner) {
      DEFINITIONS.keySet().removeIf(key -> key.runtimeOwner == runtimeOwner);
   }

   public record Key(Object runtimeOwner, String ownerModId, String name) {
      public Key { if (ownerModId == null) ownerModId = ""; }
   }

   public record ProjectileDef(double speed, double damage, boolean gravity, boolean piercing, double maxDistance, int maxTicks, float size, String damageSource, int fireTicks, double knockback, LuaValue onHitEntity, LuaValue onHitBlock, LuaValue onExpired, LuaValue onTick, Object runtimeOwner, String ownerModId, MLuaContext context) {}
}
