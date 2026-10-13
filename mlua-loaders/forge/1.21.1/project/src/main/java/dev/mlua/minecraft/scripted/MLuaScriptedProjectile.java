package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.math.MLuaVector;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.modules.combat.Service.MLuaCombatService;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
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
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public class MLuaScriptedProjectile extends Entity {
   private final String luaClassName;
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

   public void init(Vec3 dir, double speed, double damage, boolean gravity, boolean piercing, double maxDistance, int maxTicks, float size, Entity owner, String damageSourceName, int fireTicks, double knockback, LuaValue onHitEntity, LuaValue onHitBlock, LuaValue onExpired, LuaValue onTick) {
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
      this.ticksAlive = 0;
      this.destroyed = false;
      this.origin = this.position();
      this.setDeltaMovement(this.direction.scale(this.speed * 0.05));
      this.entityData.set(DATA_DAMAGE, (float) damage);
   }

   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      builder.define(DATA_DAMAGE, 0.0f);
   }

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
      this.direction = new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ"));
      this.origin = new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ"));
   }

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

   public void tick() {
      super.tick();
      if (this.destroyed || !this.isAlive()) return;
      if (this.direction == null) {
         Vec3 delta = this.getDeltaMovement();
         this.direction = delta.lengthSqr() > 1.0E-8 ? delta.normalize() : Vec3.ZERO;
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

      EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, this, currentPos, newPos, moveBox, entity -> {
         if (!entity.isAlive()) return false;
         if (this.ownerUUID != null && entity.getUUID().toString().equals(this.ownerUUID)) return false;
         return entity instanceof LivingEntity;
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
             ctx.set("Entity", MLuaEntityWrapper.entityTable(target, MLuaRuntime.registrationForLevel(target.level())));
            ctx.set("Position", MLuaVector.vector3(hitPos.x, hitPos.y, hitPos.z));
            ctx.set("Damage", this.damage);
            this.onHitEntityCallback.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua projectile onHitEntity callback failed: {}", exception.getMessage());
         }
      }
      if (!level().isClientSide) {
         Entity owner = this.resolveOwner();
         DamageSource source = MLuaCombatService.createDamageSource(target, this.damageSourceName, owner);
         target.hurt(source, (float) this.damage);
         if (this.fireTicks > 0) {
            target.igniteForSeconds(this.fireTicks);
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
            this.onHitBlockCallback.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua projectile onHitBlock callback failed: {}", exception.getMessage());
         }
      }
   }

   private void fireExpired() {
      if (!this.onExpiredCallback.isnil()) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("Projectile", this.toLuaHandle(false));
            this.onExpiredCallback.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua projectile onExpired callback failed: {}", exception.getMessage());
         }
      }
   }

   private void fireTick() {
      try {
         LuaTable ctx = new LuaTable();
         ctx.set("Projectile", this.toLuaHandle(false));
         ctx.set("Tick", this.ticksAlive);
         this.onTickCallback.invoke(ctx);
      } catch (Exception exception) {
         MLuaLogger.LOGGER.error("MLua projectile onTick callback failed: {}", exception.getMessage());
      }
   }

   public void destroy() {
      this.destroyed = true;
      this.discard();
   }

   public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
      return new ClientboundAddEntityPacket(this, entity);
   }

   public EntityDimensions getDimensions(Pose pose) {
      return EntityDimensions.scalable(this.projectileSize, this.projectileSize);
   }

   public boolean isPickable() {
      return false;
   }

   public boolean isPushable() {
      return false;
   }

   public boolean hurt(DamageSource source, float amount) {
      return false;
   }

   public LuaTable toLua() {
      return this.toLuaHandle(true);
   }

   public LuaTable toLuaHandle(boolean includeOwner) {
      LuaTable table = new LuaTable();
      table.set("__side", "Server");
      Vec3 pos = this.position();
      table.set("Position", MLuaVector.vector3(pos.x, pos.y, pos.z));
      if (this.direction != null) {
         table.set("Direction", MLuaVector.vector3(this.direction.x, this.direction.y, this.direction.z));
      }
      table.set("Velocity", MLuaVector.vector3(this.getDeltaMovement().x, this.getDeltaMovement().y, this.getDeltaMovement().z));
      table.set("Speed", this.speed);
      table.set("Damage", this.damage);
      table.set("DamageSource", this.damageSourceName);
      table.set("FireTicks", this.fireTicks);
      table.set("Knockback", this.knockback);
      table.set("Tick", this.ticksAlive);
      if (includeOwner) {
         try {
            Entity owner = this.resolveOwner();
            if (owner instanceof net.minecraft.server.level.ServerPlayer player) {
               table.set("Owner", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), MLuaRuntime.registrationForLevel(player.level())));
            } else if (owner instanceof LivingEntity living) {
               table.set("Owner", MLuaEntityWrapper.entityTable(living, MLuaRuntime.registrationForLevel(living.level())));
            }
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("Failed to attach projectile owner wrapper", exception);
         }
      }
      table.set("Destroy", new ZeroArgFunction() {
         @Override
         public LuaValue call() {
            destroy();
            return LuaValue.NONE;
         }
      });
      table.set("GetPosition", new ZeroArgFunction() {
         @Override
         public LuaValue call() {
            Vec3 current = position();
            return MLuaVector.vector3(current.x, current.y, current.z);
         }
      });
      table.set("SetDamage", new OneArgFunction() {
         @Override
         public LuaValue call(LuaValue arg) {
            damage = arg.checkdouble();
            return LuaValue.NONE;
         }
      });
      table.set("SetDirection", new OneArgFunction() {
         @Override
         public LuaValue call(LuaValue arg) {
            LuaTable vector = arg.checktable();
            direction = new Vec3(vector.get("X").checkdouble(), vector.get("Y").checkdouble(), vector.get("Z").checkdouble()).normalize();
            return LuaValue.NONE;
         }
      });
      table.set("SetVelocity", new OneArgFunction() {
         @Override
         public LuaValue call(LuaValue arg) {
            LuaTable vector = arg.checktable();
            Vec3 velocity = new Vec3(vector.get("X").checkdouble(), vector.get("Y").checkdouble(), vector.get("Z").checkdouble());
            setDeltaMovement(velocity);
            speed = velocity.length() / 0.05;
            direction = velocity.lengthSqr() > 1.0E-8 ? velocity.normalize() : Vec3.ZERO;
            return LuaValue.NONE;
         }
      });
      return MLuaRuntime.registrationForLevel(this.level()).attachClassInstance(table, this.luaClassName, "Projectile");
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
