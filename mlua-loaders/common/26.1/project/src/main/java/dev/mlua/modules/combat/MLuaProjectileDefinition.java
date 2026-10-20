package dev.mlua.modules.combat;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectile;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectileType;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MLuaProjectileDefinition {
   private static final String TYPE_STATE = "__mlua_projectile_type_state";
   private static final Map<Key, ProjectileDef> DEFINITIONS = new ConcurrentHashMap<>();
   private static final Set<MLuaScriptedProjectile> ACTIVE_PROJECTILES =
      Collections.newSetFromMap(new WeakHashMap<>());

   private MLuaProjectileDefinition() {}

   public static ProjectileDef register(String name, LuaTable props) {
      return register(null, "", name, props, null);
   }

   public static ProjectileDef register(Object runtimeOwner, String ownerModId, String name, LuaTable props, MLuaContext context) {
      if (name == null || name.isBlank()) {
         throw new LuaError("[MLua] Projectile name cannot be empty");
      }
      if (props == null) {
         throw new LuaError("[MLua] Projectile '" + name + "' requires a properties table");
      }
      ownerModId = ownerModId == null ? "" : ownerModId;
      if (!ownerModId.isBlank()) dev.mlua.runtime.registry.MLuaResourceNameValidator.validate(ownerModId);
      dev.mlua.runtime.registry.MLuaResourceNameValidator.validate(name);
      Key key = new Key(runtimeOwner, ownerModId, name);
      double speed = nonNegativeOption(props, "Speed", 60.0, "Registry.Projectiles:Register");
      double damage = nonNegativeOption(props, "Damage", 10.0, "Registry.Projectiles:Register");
      boolean gravity = booleanOption(props, "Gravity", false, "Registry.Projectiles:Register");
      boolean piercing = booleanOption(props, "Piercing", false, "Registry.Projectiles:Register");
      double maxDistance = positiveOption(props, "MaxDistance", 200.0, "Registry.Projectiles:Register");
      int maxTicks = MLuaLuaArguments.optionalWholeInt(props.get("MaxTicks"), 200, "Registry.Projectiles:Register", "MaxTicks");
      float size = positiveFloatOption(props, "Size", 0.25f, "Registry.Projectiles:Register");
      String damageSource = stringOption(props, "DamageSource", "generic", "Registry.Projectiles:Register");
      LuaValue classNameValue = props.get("className");
      if (classNameValue.isnil()) classNameValue = props.get("ClassName");
      String className = stringValue(classNameValue, "Projectile", "Registry.Projectiles:Register", "className");
      MLuaRegistration registration = context == null ? MLuaRuntime.get() : context.registration();
      if (registration != null && (registration.classTable(className) == null || !registration.isSubclassOf(className, "Projectile"))) {
         throw new LuaError("[MLua] Projectile class '" + className + "' must be registered and inherit from Projectile");
      }
      int fireTicks = MLuaLuaArguments.optionalWholeInt(props.get("FireTicks"), 0, "Registry.Projectiles:Register", "FireTicks");
      validateTickOptions(maxTicks, fireTicks, "Registry.Projectiles:Register");
      double knockback = nonNegativeOption(props, "Knockback", 0.0, "Registry.Projectiles:Register");
      LuaValue onHitEntity = callbackOption(props, "OnHitEntity", LuaValue.NIL, "Registry.Projectiles:Register");
      LuaValue onHitBlock = callbackOption(props, "OnHitBlock", LuaValue.NIL, "Registry.Projectiles:Register");
      LuaValue onExpired = callbackOption(props, "OnExpired", LuaValue.NIL, "Registry.Projectiles:Register");
      LuaValue onTick = callbackOption(props, "OnTick", LuaValue.NIL, "Registry.Projectiles:Register");
      ProjectileDef definition = new ProjectileDef(speed, damage, gravity, piercing, maxDistance, maxTicks, size, damageSource, fireTicks, knockback,
         onHitEntity.isnil() ? LuaValue.NIL : onHitEntity,
         onHitBlock.isnil() ? LuaValue.NIL : onHitBlock,
         onExpired.isnil() ? LuaValue.NIL : onExpired,
         onTick.isnil() ? LuaValue.NIL : onTick, runtimeOwner, ownerModId, context, className);
      if (DEFINITIONS.putIfAbsent(key, definition) != null) {
         throw new LuaError("[MLua] Projectile '" + name + "' is already registered");
      }
      MLuaLogger.LOGGER.info("Registered projectile '{}'", name);
      return definition;
   }

   public static boolean unregister(Object runtimeOwner, String ownerModId, String name, ProjectileDef expected) {
      if (expected == null) return false;
      Key key = new Key(runtimeOwner, ownerModId == null ? "" : ownerModId, name);
      final boolean[] removed = {false};
      DEFINITIONS.computeIfPresent(key, (ignored, current) -> {
         if (current == expected) {
            removed[0] = true;
            return null;
         }
         return current;
      });
      return removed[0];
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

   public static java.util.List<String> registeredIds(Object runtimeOwner, String namespace) {
      return DEFINITIONS.keySet().stream()
         .filter(key -> key.runtimeOwner() == runtimeOwner)
         .filter(key -> namespace == null || namespace.equals(key.ownerModId()))
         .map(key -> key.ownerModId().isBlank() ? key.name() : key.ownerModId() + ":" + key.name())
         .sorted()
         .toList();
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
      table.set("ClassName", def.className);
      table.rawset(TYPE_STATE, LuaValue.userdataOf(new TypeState(runtimeOwner, ownerModId, name, def.speed,
         def.damage, def.gravity, def.piercing, def.maxDistance, def.size, def.damageSource, def.fireTicks, def.knockback)));
      registration.attachClassInstance(table, "ProjectileType", null);
      MLuaClassMethods.setInstanceMethod(table, "Fire", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            TypeState state = typeState(args.arg1());
            LuaTable fireConfig = args.arg(2).checktable();
            return fireProjectile(state.runtimeOwner, state.ownerModId, state.name, fireConfig);
         }
      });
      return table;
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
         MLuaRegistration registration = MLuaRuntime.registrationForLevel(owner.level());
         if (registration == null || !registration.isInstanceOf(ownerVal, "Entity")) {
            throw new LuaError("[MLua] Fire requires a registered Entity instance as Owner");
         }
         Level level = owner.level();
         if (level.isClientSide()) {
            throw new LuaError("[MLua] FireProjectile must be called on the server");
         }

         double ox = owner.getX();
         double oy = owner.getY() + 1.0;
         double oz = owner.getZ();
         LuaValue position = config.get("Position");
         if (!position.isnil()) {
            double[] components = MLuaLuaArguments.finiteVector3(position, "Projectile Position");
            ox = components[0];
            oy = components[1];
            oz = components[2];
         }

         LuaValue dirVal = config.get("Direction");
         Vec3 dir;
         if (!dirVal.isnil()) {
            if (!MLuaVector.isVector3(dirVal)) throw new LuaError("[MLua] Projectile Direction requires a Vector3 value");
            double[] components = MLuaLuaArguments.finiteVector3(dirVal, "Projectile Direction");
            dir = new Vec3(components[0], components[1], components[2]).normalize();
            if (dir.lengthSqr() < 1.0E-12) throw new LuaError("[MLua] Projectile Direction must not be zero.");
         } else {
            dir = owner.getLookAngle();
         }

         double speed = nonNegativeOption(config, "Speed", def.speed, "ProjectileType:Fire");
         double damage = nonNegativeOption(config, "Damage", def.damage, "ProjectileType:Fire");
         boolean gravity = booleanOption(config, "Gravity", def.gravity, "ProjectileType:Fire");
         boolean piercing = booleanOption(config, "Piercing", def.piercing, "ProjectileType:Fire");
         double maxDistance = positiveOption(config, "MaxDistance", def.maxDistance, "ProjectileType:Fire");
         int maxTicks = MLuaLuaArguments.optionalWholeInt(config.get("MaxTicks"), def.maxTicks, "ProjectileType:Fire", "MaxTicks");
         float size = positiveFloatOption(config, "Size", def.size, "ProjectileType:Fire");
         String damageSource = stringOption(config, "DamageSource", def.damageSource, "ProjectileType:Fire");
         int fireTicks = MLuaLuaArguments.optionalWholeInt(config.get("FireTicks"), def.fireTicks, "ProjectileType:Fire", "FireTicks");
         validateTickOptions(maxTicks, fireTicks, "ProjectileType:Fire");
         double knockback = nonNegativeOption(config, "Knockback", def.knockback, "ProjectileType:Fire");

         LuaValue onHitEntity = callbackOption(config, "OnHitEntity", def.onHitEntity, "ProjectileType:Fire");
         LuaValue onHitBlock = callbackOption(config, "OnHitBlock", def.onHitBlock, "ProjectileType:Fire");
         LuaValue onExpired = callbackOption(config, "OnExpired", def.onExpired, "ProjectileType:Fire");
         LuaValue onTick = callbackOption(config, "OnTick", def.onTick, "ProjectileType:Fire");

         EntityType<MLuaScriptedProjectile> projType = MLuaScriptedProjectileType.get();
         if (projType == null) {
            throw new LuaError("[MLua] Projectile entity type not yet registered");
         }

         MLuaScriptedProjectile projectile = new MLuaScriptedProjectile(projType, level, def.className);
         projectile.setPos(ox, oy, oz);
         projectile.init(dir, speed, damage, gravity, piercing, maxDistance, maxTicks, size, owner, damageSource, fireTicks, knockback, onHitEntity, onHitBlock, onExpired, onTick, runtimeOwner, ownerModId, def.context);

         if (!level.addFreshEntity(projectile)) {
            throw new LuaError("[MLua] Minecraft rejected projectile '" + typeName + "'");
         }
         synchronized (ACTIVE_PROJECTILES) {
            ACTIVE_PROJECTILES.add(projectile);
         }
         return projectile.toLuaHandle(false);
      } catch (LuaError error) {
         throw error;
      } catch (Exception exception) {
         MLuaLogger.LOGGER.error("Failed to fire projectile '{}'", typeName, exception);
         throw new LuaError("[MLua] Failed to fire projectile '" + typeName + "': " + exception.getMessage());
      }
   }

   private static void validateTickOptions(int maxTicks, int fireTicks, String method) {
      if (maxTicks < 1) throw new LuaError("[MLua] " + method + " MaxTicks must be at least 1 tick");
      if (fireTicks < 0) throw new LuaError("[MLua] " + method + " FireTicks must be non-negative");
   }

   private static double numberOption(LuaTable values, String key, double fallback, String method) {
      LuaValue value = values.get(key);
      if (value.isnil()) return fallback;
      if (!value.isnumber()) throw new LuaError("[MLua] " + method + " " + key + " must be a number.");
      double number = value.todouble();
      if (!Double.isFinite(number)) throw new LuaError("[MLua] " + method + " " + key + " must be finite.");
      return number;
   }

   private static double nonNegativeOption(LuaTable values, String key, double fallback, String method) {
      double number = numberOption(values, key, fallback, method);
      if (number < 0.0) throw new LuaError("[MLua] " + method + " " + key + " must be non-negative.");
      return number;
   }

   private static double positiveOption(LuaTable values, String key, double fallback, String method) {
      double number = numberOption(values, key, fallback, method);
      if (number <= 0.0) throw new LuaError("[MLua] " + method + " " + key + " must be greater than zero.");
      return number;
   }

   private static float positiveFloatOption(LuaTable values, String key, float fallback, String method) {
      double number = positiveOption(values, key, fallback, method);
      float converted = (float)number;
      if (!Float.isFinite(converted)) throw new LuaError("[MLua] " + method + " " + key + " exceeds the supported range.");
      return converted;
   }

   private static boolean booleanOption(LuaTable values, String key, boolean fallback, String method) {
      LuaValue value = values.get(key);
      if (value.isnil()) return fallback;
      if (!value.isboolean()) throw new LuaError("[MLua] " + method + " " + key + " must be a boolean.");
      return value.toboolean();
   }

   private static String stringOption(LuaTable values, String key, String fallback, String method) {
      return stringValue(values.get(key), fallback, method, key);
   }

   private static String stringValue(LuaValue value, String fallback, String method, String parameter) {
      if (value.isnil()) return fallback;
      if (!value.isstring() || value.tojstring().isBlank()) throw new LuaError("[MLua] " + method + " " + parameter + " must be a non-empty string.");
      return value.tojstring();
   }

   private static LuaValue callbackOption(LuaTable values, String key, LuaValue fallback, String method) {
      LuaValue value = values.get(key);
      if (value.isnil()) return fallback;
      if (!value.isfunction()) throw new LuaError("[MLua] " + method + " " + key + " must be a function.");
      return value;
   }

   public static void clearOwned(Object runtimeOwner, String ownerModId) {
      if (runtimeOwner == null || ownerModId == null || ownerModId.isBlank()) return;
      DEFINITIONS.keySet().removeIf(key -> key.runtimeOwner == runtimeOwner && key.ownerModId.equals(ownerModId));
      java.util.List<MLuaScriptedProjectile> active;
      synchronized (ACTIVE_PROJECTILES) {
         active = new java.util.ArrayList<>(ACTIVE_PROJECTILES);
      }
      active.forEach(projectile -> projectile.clearCallbacks(runtimeOwner, ownerModId));
   }

   public static void clearRuntime(Object runtimeOwner) {
      if (runtimeOwner == null) return;
      DEFINITIONS.keySet().removeIf(key -> key.runtimeOwner == runtimeOwner);
      java.util.List<MLuaScriptedProjectile> active;
      synchronized (ACTIVE_PROJECTILES) {
         active = new java.util.ArrayList<>(ACTIVE_PROJECTILES);
      }
      active.forEach(projectile -> projectile.clearCallbacks(runtimeOwner, null));
   }

   public static void registerTypeProperties(MLuaRegistration registration) {
      registerTypeProperty(registration, "Name", "string", "The registered projectile type name.", state -> LuaValue.valueOf(state.name));
      registerTypeProperty(registration, "Speed", "number", "The default launch speed in blocks per tick.", state -> LuaValue.valueOf(state.speed));
      registerTypeProperty(registration, "Damage", "number", "The default hit damage.", state -> LuaValue.valueOf(state.damage));
      registerTypeProperty(registration, "Gravity", "boolean", "Whether gravity affects the projectile by default.", state -> LuaValue.valueOf(state.gravity));
      registerTypeProperty(registration, "Piercing", "boolean", "Whether the projectile can pass through entities by default.", state -> LuaValue.valueOf(state.piercing));
      registerTypeProperty(registration, "MaxDistance", "number", "The maximum travel distance in blocks.", state -> LuaValue.valueOf(state.maxDistance));
      registerTypeProperty(registration, "Size", "number", "The projectile hitbox size.", state -> LuaValue.valueOf(state.size));
      registerTypeProperty(registration, "DamageSource", "string", "The registered damage source used for hits.", state -> LuaValue.valueOf(state.damageSource));
      registerTypeProperty(registration, "FireTicks", "number", "The default fire duration applied to hit entities.", state -> LuaValue.valueOf(state.fireTicks));
      registerTypeProperty(registration, "Knockback", "number", "The default knockback applied to hit entities.", state -> LuaValue.valueOf(state.knockback));
   }

   private static void registerTypeProperty(MLuaRegistration registration, String name, String type,
                                           String description, java.util.function.Function<TypeState, LuaValue> getter) {
      registration.registerClassProperty("ProjectileType", name, new OneArgFunction() {
         @Override public LuaValue call(LuaValue self) { return getter.apply(typeState(self)); }
      }, LuaValue.NIL, type, "server:Server", description);
   }

   private static TypeState typeState(LuaValue value) {
      if (!value.istable()) throw new LuaError("[MLua] Expected a ProjectileType instance.");
      LuaValue stateValue = value.checktable().rawget(TYPE_STATE);
      Object rawState = stateValue.isuserdata() ? stateValue.touserdata() : null;
      if (!(rawState instanceof TypeState state)) throw new LuaError("[MLua] Invalid ProjectileType instance.");
      return state;
   }

   public record Key(Object runtimeOwner, String ownerModId, String name) {
      public Key { if (ownerModId == null) ownerModId = ""; }
   }

   private record TypeState(Object runtimeOwner, String ownerModId, String name, double speed, double damage,
                            boolean gravity, boolean piercing, double maxDistance, float size,
                            String damageSource, int fireTicks, double knockback) {}

   public record ProjectileDef(double speed, double damage, boolean gravity, boolean piercing, double maxDistance, int maxTicks, float size, String damageSource, int fireTicks, double knockback, LuaValue onHitEntity, LuaValue onHitBlock, LuaValue onExpired, LuaValue onTick, Object runtimeOwner, String ownerModId, MLuaContext context, String className) {}
}
