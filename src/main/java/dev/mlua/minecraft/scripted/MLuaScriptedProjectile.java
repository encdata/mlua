package dev.mlua.minecraft.scripted;

import net.minecraft.server.level.ServerPlayer;

import dev.mlua.core.MLuaRuntime;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.math.MLuaVector;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.modules.combat.MLuaProjectileDefinition;
import dev.mlua.modules.combat.service.MLuaCombatService;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;

public class MLuaScriptedProjectile extends Entity {
   private final String luaClassName;
   private final ThreadLocal<Boolean> resolvingLuaHandle = ThreadLocal.withInitial(() -> false);
   private static final EntityDataAccessor<Float> DATA_DAMAGE = SynchedEntityData.defineId(MLuaScriptedProjectile.class, EntityDataSerializers.FLOAT);

   private Vec3 direction;
   private double speed;
   private double damage;
   private boolean gravity;
   private boolean piercing;
   private double maxDistanceSqr;
   private int maxTicks;
   private float projectileSize;
   private Vec3 origin;
   private int ticksAlive;
   private String ownerUUID;
   private int ownerEntityId;
   private String damageSourceName;
   private int fireTicks;
   private double knockback;
   private Entity ownerEntity;
   private LuaValue onHitEntityCallback;
   private LuaValue onHitBlockCallback;
   private LuaValue onExpiredCallback;
   private LuaValue onTickCallback;
   private boolean destroyed;
   private Object runtimeOwner;
   private String ownerModId = "";
   private MLuaContext callbackContext;

   public MLuaScriptedProjectile(EntityType<? extends MLuaScriptedProjectile> type, Level level) {
      this(type, level, "Projectile");
   }

   public MLuaScriptedProjectile(EntityType<? extends MLuaScriptedProjectile> type, Level level, String luaClassName) {
      super(type, level);
      this.luaClassName = luaClassName == null || luaClassName.isBlank() ? "Projectile" : luaClassName;
      this.projectileSize = 0.25f;
      this.damage = 0;
      this.speed = 0;
      this.gravity = false;
      this.piercing = false;
      this.maxDistanceSqr = Double.MAX_VALUE;
      this.maxTicks = 200;
      this.ticksAlive = 0;
      this.destroyed = false;
      this.ownerEntityId = -1;
      this.damageSourceName = "generic";
      this.onHitEntityCallback = LuaValue.NIL;
      this.onHitBlockCallback = LuaValue.NIL;
      this.onExpiredCallback = LuaValue.NIL;
      this.onTickCallback = LuaValue.NIL;
   }

   public void init(Vec3 dir, double speed, double damage, boolean gravity, boolean piercing, double maxDistance, int maxTicks, float size, Entity owner, String damageSourceName, int fireTicks, double knockback, LuaValue onHitEntity, LuaValue onHitBlock, LuaValue onExpired, LuaValue onTick, Object runtimeOwner, String ownerModId, MLuaContext callbackContext) {
      this.direction = dir != null && dir.lengthSqr() > 1.0E-8 ? dir.normalize() : Vec3.ZERO;
      this.speed = speed;
      this.damage = damage;
      this.gravity = gravity;
      this.piercing = piercing;
      this.maxDistanceSqr = maxDistance * maxDistance;
      this.maxTicks = maxTicks;
      this.projectileSize = size;
      this.ownerEntity = owner;
      this.ownerEntityId = owner != null ? owner.getId() : -1;
      this.ownerUUID = owner != null ? owner.getUUID().toString() : null;
      this.damageSourceName = damageSourceName != null && !damageSourceName.isBlank() ? damageSourceName : "generic";
      this.fireTicks = fireTicks;
      this.knockback = knockback;
      this.onHitEntityCallback = onHitEntity != null && !onHitEntity.isnil() ? onHitEntity : LuaValue.NIL;
      this.onHitBlockCallback = onHitBlock != null && !onHitBlock.isnil() ? onHitBlock : LuaValue.NIL;
      this.onExpiredCallback = onExpired != null && !onExpired.isnil() ? onExpired : LuaValue.NIL;
      this.onTickCallback = onTick != null && !onTick.isnil() ? onTick : LuaValue.NIL;
      this.runtimeOwner = runtimeOwner;
      this.ownerModId = ownerModId == null ? "" : ownerModId;
      this.callbackContext = callbackContext;
      this.ticksAlive = 0;
      this.destroyed = false;
      this.origin = this.position();
      this.setDeltaMovement(this.direction.scale(this.speed * 0.05));
      this.entityData.set(DATA_DAMAGE, (float)damage);
   }

//--version 1.20.1
   @Override
   protected void defineSynchedData() {
      this.entityData.define(DATA_DAMAGE, 0.0f);
   }
//--endversion
//--version 1.21.1
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      builder.define(DATA_DAMAGE, 0.0f);
   }
//--endversion

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
      this.damage = tag.getDouble("Damage");
      this.speed = tag.getDouble("Speed");
      this.gravity = tag.getBoolean("Gravity");
      this.piercing = tag.getBoolean("Piercing");
      this.maxDistanceSqr = tag.getDouble("MaxDistanceSqr");
      this.maxTicks = tag.getInt("MaxTicks");
      this.ticksAlive = tag.getInt("TicksAlive");
      this.projectileSize = tag.getFloat("Size");
      this.ownerUUID = tag.contains("OwnerUUID") ? tag.getString("OwnerUUID") : null;
      this.ownerEntityId = tag.getInt("OwnerEntityId");
      this.damageSourceName = tag.contains("DamageSource") ? tag.getString("DamageSource") : "generic";
      this.fireTicks = tag.getInt("FireTicks");
      this.knockback = tag.getDouble("Knockback");
      double dx = tag.getDouble("DirX");
      double dy = tag.getDouble("DirY");
      double dz = tag.getDouble("DirZ");
      this.direction = new Vec3(dx, dy, dz);
      double ox = tag.getDouble("OriginX");
      double oy = tag.getDouble("OriginY");
      double oz = tag.getDouble("OriginZ");
      this.origin = new Vec3(ox, oy, oz);
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
      tag.putDouble("Damage", this.damage);
      tag.putDouble("Speed", this.speed);
      tag.putBoolean("Gravity", this.gravity);
      tag.putBoolean("Piercing", this.piercing);
      tag.putDouble("MaxDistanceSqr", this.maxDistanceSqr);
      tag.putInt("MaxTicks", this.maxTicks);
      tag.putInt("TicksAlive", this.ticksAlive);
      tag.putFloat("Size", this.projectileSize);
      if (this.ownerUUID != null) tag.putString("OwnerUUID", this.ownerUUID);
      tag.putInt("OwnerEntityId", this.ownerEntityId);
      tag.putString("DamageSource", this.damageSourceName);
      tag.putInt("FireTicks", this.fireTicks);
      tag.putDouble("Knockback", this.knockback);
      if (this.direction != null) {
         tag.putDouble("DirX", this.direction.x);
         tag.putDouble("DirY", this.direction.y);
         tag.putDouble("DirZ", this.direction.z);
      }
      if (this.origin != null) {
         tag.putDouble("OriginX", this.origin.x);
         tag.putDouble("OriginY", this.origin.y);
         tag.putDouble("OriginZ", this.origin.z);
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (this.destroyed || !this.isAlive()) return;
      if (this.direction == null) {
         Vec3 delta = this.getDeltaMovement();
         if (delta.lengthSqr() > 1.0E-8) {
            this.direction = delta.normalize();
         } else {
            this.direction = Vec3.ZERO;
         }
      }
      if (this.origin == null) {
         this.origin = this.position();
      }
      this.ticksAlive++;
      if (!this.onTickCallback.isnil()) {
         fireTick();
      }

      if (this.ticksAlive > this.maxTicks) {
         fireExpired();
         this.discard();
         return;
      }

      Vec3 currentPos = this.position();
      if (this.origin != null && currentPos.distanceToSqr(this.origin) > this.maxDistanceSqr) {
         fireExpired();
         this.discard();
         return;
      }

      Vec3 velocity = this.direction.scale(this.speed * 0.05);
      if (this.gravity) {
         velocity = velocity.add(0, -0.98 * 0.05, 0);
         if (velocity.lengthSqr() > 1.0E-8) {
            this.direction = velocity.normalize();
         }
      }

      Vec3 newPos = currentPos.add(velocity);
      this.setDeltaMovement(velocity);

      AABB moveBox = this.getBoundingBox().expandTowards(velocity).inflate(0.1);
      Level level = this.level();

      BlockHitResult blockHit = level.clip(new ClipContext(currentPos, newPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      if (blockHit.getType() != HitResult.Type.MISS) {
         newPos = blockHit.getLocation();
         fireHitBlock(blockHit);
         if (!this.piercing) {
            this.discard();
            return;
         }
      }

      EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, this, currentPos, newPos, moveBox, (e) -> {
         if (!e.isAlive()) return false;
         if (this.ownerUUID != null && e.getUUID().toString().equals(this.ownerUUID)) return false;
         return e instanceof LivingEntity;
      });
      if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
         newPos = entityHit.getLocation();
         fireHitEntity(target, newPos);
         if (!this.piercing) {
            this.discard();
            return;
         }
      }

      this.setPos(newPos.x, newPos.y, newPos.z);
      this.setDeltaMovement(velocity);
      this.hasImpulse = true;
   }

   private void fireHitEntity(LivingEntity target, Vec3 hitPos) {
      if (!this.onHitEntityCallback.isnil()) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("Projectile", this.toLuaHandle(false));
            ctx.set("Entity", MLuaEntityWrapper.entityTable(target, this.callbackContext != null ? callbackContext.registration() : MLuaRuntime.registrationForLevel(this.level())));
            ctx.set("Position", MLuaVector.vector3(hitPos.x, hitPos.y, hitPos.z));
            ctx.set("Damage", this.damage);
            invokeCallback("onHitEntity", this.onHitEntityCallback, ctx);
         } catch (Exception e) {
            MLuaLogger.LOGGER.error("MLua projectile onHitEntity callback failed", e);
         }
    }
    if (!level().isClientSide) {
         Entity owner = this.resolveOwner();
         DamageSource source = MLuaCombatService.createDamageSource(target, this.damageSourceName, owner, this.runtimeOwner, this.ownerModId);
         target.hurt(source, (float)this.damage);
         if (this.fireTicks > 0) {
//--version 1.20.1
            target.setSecondsOnFire(this.fireTicks);
//--endversion
//--version 1.21.1
            target.igniteForSeconds(this.fireTicks);
//--endversion
         }
         if (this.knockback > 0.0 && this.direction != null) {
            target.knockback(this.knockback, -this.direction.x, -this.direction.z);
         }
      }
   }

   private void fireHitBlock(BlockHitResult hit) {
      if (!this.onHitBlockCallback.isnil()) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("Projectile", this.toLuaHandle(false));
            Vec3 pos = hit.getLocation();
            ctx.set("Position", MLuaVector.vector3(pos.x, pos.y, pos.z));
            ctx.set("BlockX", hit.getBlockPos().getX());
            ctx.set("BlockY", hit.getBlockPos().getY());
            ctx.set("BlockZ", hit.getBlockPos().getZ());
            ctx.set("Face", hit.getDirection().getName());
            invokeCallback("onHitBlock", this.onHitBlockCallback, ctx);
         } catch (Exception e) {
            MLuaLogger.LOGGER.error("MLua projectile onHitBlock callback failed", e);
          }
       }
    }

    private void fireExpired() {
       if (!this.onExpiredCallback.isnil()) {
          try {
             LuaTable ctx = new LuaTable();
             ctx.set("Projectile", this.toLuaHandle(false));
             invokeCallback("onExpired", this.onExpiredCallback, ctx);
          } catch (Exception e) {
             MLuaLogger.LOGGER.error("MLua projectile onExpired callback failed", e);
          }
       }
    }

   private void fireTick() {
      try {
         LuaTable ctx = new LuaTable();
         ctx.set("Projectile", this.toLuaHandle(false));
         ctx.set("Tick", this.ticksAlive);
         invokeCallback("onTick", this.onTickCallback, ctx);
      } catch (Exception e) {
         MLuaLogger.LOGGER.error("MLua projectile onTick callback failed", e);
      }
   }

   private synchronized void invokeCallback(String event, LuaValue callback, LuaTable args) {
      if (this.level().isClientSide || callback == null || callback.isnil()
            || callbackFor(event) != callback) return;
      try {
         MLuaBehaviorRegistry.invokeCallback(this.callbackContext, callback, args);
      } catch (VirtualMachineError | ThreadDeath fatal) {
         throw fatal;
      } catch (Throwable failure) {
         disableCallback(event, callback);
         MLuaLogger.LOGGER.error("MLua projectile {} callback failed and was disabled", event, failure);
      }
   }

   private LuaValue callbackFor(String event) {
      return switch (event) {
         case "onHitEntity" -> this.onHitEntityCallback;
         case "onHitBlock" -> this.onHitBlockCallback;
         case "onExpired" -> this.onExpiredCallback;
         case "onTick" -> this.onTickCallback;
         default -> LuaValue.NIL;
      };
   }

   private void disableCallback(String event, LuaValue callback) {
      if (callbackFor(event) != callback) return;
      switch (event) {
         case "onHitEntity" -> this.onHitEntityCallback = LuaValue.NIL;
         case "onHitBlock" -> this.onHitBlockCallback = LuaValue.NIL;
         case "onExpired" -> this.onExpiredCallback = LuaValue.NIL;
         case "onTick" -> this.onTickCallback = LuaValue.NIL;
         default -> { }
      }
   }

   /** Drops script-owned closures when their runtime is retired while allowing the projectile to finish its flight. */
   public synchronized void clearCallbacks(Object runtimeOwner, String ownerModId) {
      if (this.runtimeOwner != runtimeOwner || !this.ownerModId.equals(ownerModId == null ? "" : ownerModId)) return;
      this.onHitEntityCallback = LuaValue.NIL;
      this.onHitBlockCallback = LuaValue.NIL;
      this.onExpiredCallback = LuaValue.NIL;
      this.onTickCallback = LuaValue.NIL;
      this.callbackContext = null;
   }

   public void destroy() {
      this.destroyed = true;
      this.discard();
   }

//--version 1.20.1
   @Override
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return new ClientboundAddEntityPacket(this);
   }
//--endversion
//--version 1.21.1
   public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
      return new ClientboundAddEntityPacket(this, entity);
   }
//--endversion

   @Override
   public EntityDimensions getDimensions(Pose pose) {
      return EntityDimensions.scalable(this.projectileSize, this.projectileSize);
   }

   @Override
   public boolean isPickable() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      return false;
   }

   public LuaTable toLua() {
      return this.toLuaHandle(true);
   }

   public LuaTable toLuaHandle(boolean includeOwner) {
      boolean previous = this.resolvingLuaHandle.get();
      this.resolvingLuaHandle.set(true);
      try {
         var registration = this.callbackContext != null ? this.callbackContext.registration() : MLuaRuntime.registrationForLevel(this.level());
         LuaTable table = MLuaEntityWrapper.entityTable(this, registration);
         registration.attachClassInstance(table, this.luaClassName, "Projectile");
         MLuaClassMethods.setInstanceMethod(table, "Destroy", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               destroy();
               return LuaValue.NONE;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "SetDamage", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               damage = args.checkdouble(2);
               return LuaValue.NONE;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "SetDirection", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               if (args.arg(2).isnil()) {
                  direction = null;
                  return LuaValue.NONE;
               }
               if (!MLuaVector.isVector3(args.arg(2))) throw new LuaError("[MLua] Projectile direction requires a Vector3 value");
               double[] components = MLuaCFrame.vector(args.arg(2));
               direction = new Vec3(components[0], components[1], components[2]).normalize();
               return LuaValue.NONE;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "SetVelocity", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               if (!MLuaVector.isVector3(args.arg(2))) throw new LuaError("[MLua] Projectile velocity requires a Vector3 value");
               double[] components = MLuaCFrame.vector(args.arg(2));
               Vec3 velocity = new Vec3(components[0], components[1], components[2]);
               setDeltaMovement(velocity);
               speed = velocity.length() / 0.05;
               direction = velocity.lengthSqr() > 1.0E-8 ? velocity.normalize() : Vec3.ZERO;
               return LuaValue.NONE;
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetDirection", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               return direction == null ? LuaValue.NIL : MLuaVector.vector3(direction.x, direction.y, direction.z);
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetSpeed", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               return LuaValue.valueOf(speed);
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetDamage", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               return LuaValue.valueOf(damage);
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetDamageSource", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               return LuaValue.valueOf(damageSourceName);
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetFireTicks", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               return LuaValue.valueOf(fireTicks);
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetKnockback", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               return LuaValue.valueOf(knockback);
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetTick", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               return LuaValue.valueOf(ticksAlive);
            }
         });
         MLuaClassMethods.setInstanceMethod(table, "GetOwner", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
               if (!includeOwner) return LuaValue.NIL;
               Entity owner = resolveOwner();
               if (owner == null) return LuaValue.NIL;
               if (owner instanceof ServerPlayer player) {
                  return MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration);
               }
               return MLuaEntityWrapper.entityTable(owner, registration);
            }
         });
         return table;
      } finally {
         this.resolvingLuaHandle.set(previous);
      }
   }

   public boolean isResolvingLuaHandle() {
      return this.resolvingLuaHandle.get();
   }

   private Entity resolveOwner() {
      if (this.ownerEntity != null && this.ownerEntity.isAlive()) {
         return this.ownerEntity;
      }
      if (this.ownerEntityId >= 0) {
         Entity resolved = this.level().getEntity(this.ownerEntityId);
         if (resolved != null) {
            this.ownerEntity = resolved;
            return resolved;
         }
      }
      return null;
   }
}
