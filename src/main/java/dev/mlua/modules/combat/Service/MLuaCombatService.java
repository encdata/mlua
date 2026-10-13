package dev.mlua.modules.combat.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.modules.combat.MLuaDamageSourceRegistry;
import dev.mlua.core.MLuaRuntime;
import java.util.function.Function;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class MLuaCombatService extends MLuaServiceBase {
   public MLuaCombatService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "Combat";
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__side", "Both");
      table.set("GetDamageSourceNames", fn((args) -> {
         LuaTable result = new LuaTable();
         int i = 1;
         for (String name : MLuaDamageSourceRegistry.names(runtimeOwner(), ownerModId())) {
            result.set(i++, LuaValue.valueOf(name));
         }
         return result;
      }));
      table.set("DamageSourceExists", fn((args) -> {
         return LuaValue.valueOf(MLuaDamageSourceRegistry.contains(runtimeOwner(), ownerModId(), args.checkjstring(2)));
      }));
      table.set("GetDamageSourceInfo", fn((args) -> {
         MLuaDamageSourceRegistry.DamageSourceDef def = MLuaDamageSourceRegistry.get(runtimeOwner(), ownerModId(), args.checkjstring(2));
         if (def == null) {
            return LuaValue.NIL;
         }
         LuaTable info = new LuaTable();
         info.set("BypassesArmor", LuaValue.valueOf(def.bypassesArmor()));
         info.set("IsFire", LuaValue.valueOf(def.isFire()));
         info.set("IsExplosion", LuaValue.valueOf(def.isExplosion()));
         info.set("IsMagic", LuaValue.valueOf(def.isMagic()));
         info.set("IsProjectile", LuaValue.valueOf(def.isProjectile()));
         info.set("IsFall", LuaValue.valueOf(def.isFall()));
         info.set("IsLightning", LuaValue.valueOf(def.isLightning()));
         return info;
      }));
      return table;
   }

   public static DamageSource createDamageSource(Entity target, String name) {
      return createDamageSource(target, name, null);
   }

   public static DamageSource createDamageSource(Entity target, String name, Entity attacker) {
      return createDamageSource(target, name, attacker, null, "");
   }

   public static DamageSource createDamageSource(Entity target, String name, Entity attacker, Object runtimeOwner, String ownerModId) {
      if (name == null || name.isBlank() || name.equals("generic")) {
         return attackSource(target, attacker);
      }
       MLuaDamageSourceRegistry.DamageSourceDef def = MLuaDamageSourceRegistry.get(runtimeOwner, ownerModId, name);
       if (def == null && (ownerModId == null || ownerModId.isBlank())) {
          def = MLuaDamageSourceRegistry.getUnique(runtimeOwner, name);
       }
       if (def == null) {
          return attackSource(target, attacker);
       }
      if (def.isFire()) {
         return target.level().damageSources().onFire();
      }
      if (def.isExplosion()) {
         return target.level().damageSources().explosion(null);
      }
      if (def.isMagic()) {
         return target.level().damageSources().magic();
      }
      if (def.isFall()) {
         return target.level().damageSources().fall();
      }
      if (def.isProjectile()) {
         return target.level().damageSources().mobProjectile(attacker != null ? attacker : target, null);
      }
      if (def.isLightning()) {
         return target.level().damageSources().lightningBolt();
      }
      return attackSource(target, attacker);
   }

   private Object runtimeOwner() {
      return this.context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
   }

   private String ownerModId() {
      String owner = this.context.mlua().ownerModId();
      return owner == null ? "" : owner;
   }

   private void requireServer() {
      if (this.context.mlua().side() != Side.SERVER) {
         throw new LuaError("[MLua] Combat service methods are server-side only");
      }
   }

   private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
      return new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            try {
               return (Varargs)body.apply(args);
            } catch (LuaError var3) {
               throw var3;
            } catch (Exception var4) {
               throw new LuaError("[MLua] " + var4.toString());
            }
         }
      };
   }

   private static DamageSource attackSource(Entity target, Entity attacker) {
      if (attacker instanceof Player player) {
         return target.level().damageSources().playerAttack(player);
      }
      if (attacker instanceof LivingEntity living) {
         return target.level().damageSources().mobAttack(living);
      }
      return target.level().damageSources().generic();
   }
}
