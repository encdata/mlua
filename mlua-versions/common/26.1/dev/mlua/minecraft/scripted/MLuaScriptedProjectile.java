package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.math.MLuaVector;
import dev.mlua.modules.combat.service.MLuaCombatService;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public final class MLuaScriptedProjectile extends Entity {
    private final String luaClassName;
    private final ThreadLocal<Boolean> resolvingLuaHandle = ThreadLocal.withInitial(() -> false);
    private Vec3 direction = Vec3.ZERO;
    private Vec3 origin;
    private double speed;
    private double damage;
    private double maxDistanceSqr = Double.MAX_VALUE;
    private double knockback;
    private int maxTicks = 200;
    private int ticksAlive;
    private int fireTicks;
    private float size = 0.25f;
    private boolean gravity;
    private boolean piercing;
    private String damageSourceName = "generic";
    private String ownerModId = "";
    private Entity owner;
    private Object runtimeOwner;
    private MLuaContext callbackContext;
    private LuaValue onHitEntity = LuaValue.NIL;
    private LuaValue onHitBlock = LuaValue.NIL;
    private LuaValue onExpired = LuaValue.NIL;
    private LuaValue onTick = LuaValue.NIL;
    private final Set<String> disabledCallbacks = new HashSet<>();

    public MLuaScriptedProjectile(EntityType<? extends MLuaScriptedProjectile> type, Level level) {
        this(type, level, "Projectile");
    }

    public MLuaScriptedProjectile(EntityType<? extends MLuaScriptedProjectile> type, Level level, String luaClassName) {
        super(type, level);
        this.luaClassName = luaClassName == null || luaClassName.isBlank() ? "Projectile" : luaClassName;
    }

    public void init(Vec3 direction, double speed, double damage, boolean gravity, boolean piercing,
                     double maxDistance, int maxTicks, float size, Entity owner, String damageSourceName,
                     int fireTicks, double knockback, LuaValue onHitEntity, LuaValue onHitBlock,
                     LuaValue onExpired, LuaValue onTick, Object runtimeOwner, String ownerModId,
                     MLuaContext callbackContext) {
        this.direction = direction != null && direction.lengthSqr() > 1.0E-8 ? direction.normalize() : Vec3.ZERO;
        this.speed = speed;
        this.damage = damage;
        this.gravity = gravity;
        this.piercing = piercing;
        this.maxDistanceSqr = maxDistance * maxDistance;
        this.maxTicks = maxTicks;
        this.size = size;
        this.owner = owner;
        this.damageSourceName = damageSourceName == null || damageSourceName.isBlank() ? "generic" : damageSourceName;
        this.fireTicks = fireTicks;
        this.knockback = knockback;
        this.onHitEntity = onHitEntity == null ? LuaValue.NIL : onHitEntity;
        this.onHitBlock = onHitBlock == null ? LuaValue.NIL : onHitBlock;
        this.onExpired = onExpired == null ? LuaValue.NIL : onExpired;
        this.onTick = onTick == null ? LuaValue.NIL : onTick;
        this.disabledCallbacks.clear();
        this.runtimeOwner = runtimeOwner;
        this.ownerModId = ownerModId == null ? "" : ownerModId;
        this.callbackContext = callbackContext;
        this.origin = position();
        setDeltaMovement(this.direction.scale(speed * 0.05));
    }

    /** Releases Lua closures owned by a retired runtime while allowing this projectile to finish. */
    public synchronized void clearCallbacks(Object expectedRuntimeOwner, String expectedOwnerModId) {
        if (this.runtimeOwner != expectedRuntimeOwner
                || !this.ownerModId.equals(expectedOwnerModId == null ? "" : expectedOwnerModId)) return;
        this.onHitEntity = LuaValue.NIL;
        this.onHitBlock = LuaValue.NIL;
        this.onExpired = LuaValue.NIL;
        this.onTick = LuaValue.NIL;
        this.callbackContext = null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.damage = input.getDoubleOr("Damage", 0.0);
        this.speed = input.getDoubleOr("Speed", 0.0);
        this.maxDistanceSqr = input.getDoubleOr("MaxDistanceSqr", Double.MAX_VALUE);
        this.knockback = input.getDoubleOr("Knockback", 0.0);
        this.maxTicks = input.getIntOr("MaxTicks", 200);
        this.ticksAlive = input.getIntOr("TicksAlive", 0);
        this.fireTicks = input.getIntOr("FireTicks", 0);
        this.size = input.getFloatOr("Size", 0.25f);
        this.gravity = input.getBooleanOr("Gravity", false);
        this.piercing = input.getBooleanOr("Piercing", false);
        this.damageSourceName = input.getStringOr("DamageSource", "generic");
        this.direction = new Vec3(input.getDoubleOr("DirX", 0), input.getDoubleOr("DirY", 0), input.getDoubleOr("DirZ", 0));
        this.origin = new Vec3(input.getDoubleOr("OriginX", getX()), input.getDoubleOr("OriginY", getY()), input.getDoubleOr("OriginZ", getZ()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putDouble("Damage", damage);
        output.putDouble("Speed", speed);
        output.putDouble("MaxDistanceSqr", maxDistanceSqr);
        output.putDouble("Knockback", knockback);
        output.putInt("MaxTicks", maxTicks);
        output.putInt("TicksAlive", ticksAlive);
        output.putInt("FireTicks", fireTicks);
        output.putFloat("Size", size);
        output.putBoolean("Gravity", gravity);
        output.putBoolean("Piercing", piercing);
        output.putString("DamageSource", damageSourceName);
        Vec3 savedDirection = direction != null ? direction : Vec3.ZERO;
        output.putDouble("DirX", savedDirection.x);
        output.putDouble("DirY", savedDirection.y);
        output.putDouble("DirZ", savedDirection.z);
        if (origin != null) {
            output.putDouble("OriginX", origin.x);
            output.putDouble("OriginY", origin.y);
            output.putDouble("OriginZ", origin.z);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive()) return;
        ticksAlive++;
        if (!onTick.isnil()) invoke("onTick", onTick, callback("Tick", LuaValue.valueOf(ticksAlive)));
        if (ticksAlive > maxTicks || origin != null && position().distanceToSqr(origin) > maxDistanceSqr) {
            expire();
            discard();
            return;
        }

        if (direction == null) {
            Vec3 delta = getDeltaMovement();
            direction = delta.lengthSqr() > 1.0E-8 ? delta.normalize() : Vec3.ZERO;
        }
        Vec3 start = position();
        Vec3 velocity = direction.scale(speed * 0.05);
        if (gravity) {
            velocity = velocity.add(0, -0.98 * 0.05, 0);
            if (velocity.lengthSqr() > 1.0E-8) direction = velocity.normalize();
        }
        Vec3 end = start.add(velocity);
        BlockHitResult blockHit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
            LuaTable args = callback("Position", MLuaVector.vector3(end.x, end.y, end.z));
            args.set("BlockX", blockHit.getBlockPos().getX());
            args.set("BlockY", blockHit.getBlockPos().getY());
            args.set("BlockZ", blockHit.getBlockPos().getZ());
            args.set("Face", blockHit.getDirection().getName());
            if (!onHitBlock.isnil()) invoke("onHitBlock", onHitBlock, args);
            if (!piercing) { discard(); return; }
        }

        AABB search = getBoundingBox().expandTowards(velocity).inflate(0.1);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(this, start, end, search,
            candidate -> candidate.isAlive() && candidate != owner && candidate instanceof LivingEntity,
            end.distanceToSqr(start));
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
            end = entityHit.getLocation();
            if (!onHitEntity.isnil()) {
                LuaTable args = callback("Position", MLuaVector.vector3(end.x, end.y, end.z));
                args.set("Entity", MLuaEntityWrapper.entityTable(target, callbackContext != null ? callbackContext.registration() : MLuaRuntime.registrationForLevel(this.level())));
                args.set("Damage", LuaValue.valueOf(damage));
                invoke("onHitEntity", onHitEntity, args);
            }
            if (level() instanceof ServerLevel serverLevel) {
                DamageSource source = MLuaCombatService.createDamageSource(target, damageSourceName, owner, runtimeOwner, ownerModId);
                if (target.hurtServer(serverLevel, source, (float) damage)) {
                    if (fireTicks > 0) target.igniteForSeconds(fireTicks);
                    if (knockback > 0 && direction.lengthSqr() > 1.0E-8) target.knockback(knockback, -direction.x, -direction.z);
                }
            }
            if (!piercing) { discard(); return; }
        }
        setPos(end.x, end.y, end.z);
        setDeltaMovement(velocity);
        if (velocity.lengthSqr() > 1.0E-8) direction = velocity.normalize();
        hurtMarked = true;
    }

    private LuaTable callback(String key, LuaValue value) {
        LuaTable args = new LuaTable();
        args.set("Projectile", toLuaHandle(false));
        args.set(key, value);
        return args;
    }

    private synchronized void invoke(String event, LuaValue callback, LuaTable args) {
        if (!(level() instanceof ServerLevel) || callback == null || callback.isnil()
                || this.disabledCallbacks.contains(event)) return;
        try {
            MLuaBehaviorRegistry.invokeCallback(this.callbackContext, callback, args);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            this.disabledCallbacks.add(event);
            MLuaLogger.LOGGER.error("MLua projectile {} callback failed for '{}'; disabled for this projectile",
                event, this.ownerModId, failure);
        }
    }

    private void expire() {
        if (!onExpired.isnil()) invoke("onExpired", onExpired, callback("Tick", LuaValue.valueOf(ticksAlive)));
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
                 discard();
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
                 LuaTable vector = args.checktable(2);
                 direction = new Vec3(vector.get("X").checkdouble(), vector.get("Y").checkdouble(), vector.get("Z").checkdouble()).normalize();
                 return LuaValue.NONE;
              }
           });
           MLuaClassMethods.setInstanceMethod(table, "SetVelocity", new VarArgFunction() {
              @Override
              public Varargs invoke(Varargs args) {
                 LuaTable vector = args.checktable(2);
                 Vec3 velocity = new Vec3(vector.get("X").checkdouble(), vector.get("Y").checkdouble(), vector.get("Z").checkdouble());
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
                 if (!(owner instanceof LivingEntity living) || !owner.isAlive()) return LuaValue.NIL;
                 if (owner instanceof ServerPlayer player) {
                    return MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration);
                 }
                 return MLuaEntityWrapper.entityTable(living, registration);
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
    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.scalable(size, size); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
}
