package dev.mlua.minecraft.scripted;

import dev.mlua.minecraft.binding.MLuaLuaArguments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Binds movement, targeting, and visibility methods exposed by scripted entities. */
final class MLuaScriptedEntityNavigationApi {
    private MLuaScriptedEntityNavigationApi() { }

    static void bind(LuaTable methods, MLuaScriptedEntity entity) {
        methods.set("MoveTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return entity.navigateTo(args, "MoveTo"); }
        });
        methods.set("NavigateTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return entity.navigateTo(args, "NavigateTo"); }
        });
        methods.set("StopMoving", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                entity.requireServerSide("StopMoving");
                boolean wasMoving = !entity.getNavigation().isDone();
                entity.getNavigation().stop();
                return LuaValue.valueOf(wasMoving);
            }
        });
        methods.set("LookAt", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                entity.requireServerSide("LookAt");
                double[] position = MLuaLuaArguments.finiteVector3(args.arg(2), "ScriptedEntity:LookAt position");
                entity.getLookControl().setLookAt(position[0], position[1], position[2]);
                return LuaValue.NONE;
            }
        });
        methods.set("AttackTarget", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                entity.requireServerSide("AttackTarget");
                LuaValue targetValue = args.arg(2);
                if (targetValue.isnil()) {
                    entity.setTarget((LivingEntity) null);
                    return LuaValue.TRUE;
                }
                LivingEntity target = entity.resolveLivingEntity(targetValue);
                if (target == null) {
                    throw new LuaError("[MLua] AttackTarget expects a live LivingEntity or Player instance, or nil to clear the target.");
                }
                if (target.level() != entity.level()) {
                    throw new LuaError("[MLua] AttackTarget expects an entity in the same dimension.");
                }
                entity.setTarget(target);
                return LuaValue.TRUE;
            }
        });
        methods.set("GetTarget", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LivingEntity target = entity.getTarget();
                return target == null || target.isRemoved() ? LuaValue.NIL : entity.entityToLua(target);
            }
        });
        methods.set("CanSee", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Entity target = entity.resolveEntity(args.arg(2));
                if (target == null) {
                    throw new LuaError("[MLua] ScriptedEntity:CanSee expects a live entity in the same dimension.");
                }
                return LuaValue.valueOf(entity.hasLineOfSight(target));
            }
        });
        methods.set("RandomStroll", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                entity.requireServerSide("RandomStroll");
                double speed = positiveSpeed(args, 2, 1.0, "ScriptedEntity:RandomStroll");
                Level level = entity.level();
                for (int attempt = 0; attempt < 10; attempt++) {
                    int dx = entity.randomSource().nextInt(10) - 5;
                    int dz = entity.randomSource().nextInt(10) - 5;
                    BlockPos target = entity.blockPosition().offset(dx, 0, dz);
                    BlockPos below = target.below();
                    if (level.getBlockState(below).isSolid() && level.getBlockState(target).isAir()
                            && level.getBlockState(target.above()).isAir()) {
                        return LuaValue.valueOf(entity.getNavigation().moveTo(
                            target.getX(), target.getY(), target.getZ(), speed));
                    }
                }
                return LuaValue.FALSE;
            }
        });
        methods.set("Panic", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                entity.requireServerSide("Panic");
                double speed = positiveSpeed(args, 2, 1.5, "ScriptedEntity:Panic");
                DamageSource lastDamage = entity.getLastDamageSource();
                if (lastDamage == null) return LuaValue.FALSE;
                LivingEntity attacker = entity.getLastHurtByMob();
                if (attacker != null) {
                    Vec3 away = entity.position().subtract(attacker.position());
                    away = new Vec3(away.x, 0.0, away.z);
                    if (away.lengthSqr() < 1.0E-6) {
                        double angle = entity.randomSource().nextDouble() * Math.PI * 2.0;
                        away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
                    } else {
                        away = away.normalize();
                    }
                    BlockPos panicPosition = entity.blockPosition().offset(
                        (int) Math.round(away.x * 5.0), 0, (int) Math.round(away.z * 5.0));
                    return LuaValue.valueOf(entity.getNavigation().moveTo(
                        panicPosition.getX(), panicPosition.getY(), panicPosition.getZ(), speed));
                }
                int x = entity.randomSource().nextInt(20) - 10;
                int z = entity.randomSource().nextInt(20) - 10;
                BlockPos target = entity.blockPosition().offset(x, 0, z);
                return LuaValue.valueOf(entity.getNavigation().moveTo(
                    target.getX(), target.getY(), target.getZ(), speed));
            }
        });
    }

    private static double positiveSpeed(Varargs args, int index, double fallback, String method) {
        double speed = MLuaLuaArguments.optionalFiniteNumber(args, index, fallback, method, "speed");
        if (speed <= 0.0) {
            throw new LuaError("[MLua] " + method + " speed must be greater than zero.");
        }
        return speed;
    }
}
