package dev.mlua.modules.combat.Service;

import dev.mlua.api.MLuaDescription;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.modules.combat.MLuaDamageSourceRegistry;
import dev.mlua.core.MLuaRuntime;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

@MLuaDescription("Queries registered damage sources and applies damage to supported entities.")
public final class MLuaCombatService extends MLuaReflectiveService {
   public MLuaCombatService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "Combat";
   }

   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Both");
      return table;
   }

   @MLuaDescription("Returns the registered damage source identifiers.")
   public LuaTable GetDamageSourceNames() {
      LuaTable result = new LuaTable();
      int index = 1;
      for (String name : MLuaDamageSourceRegistry.names(runtimeOwner(), ownerModId())) {
         result.set(index++, LuaValue.valueOf(name));
      }
      return result;
   }

   @MLuaDescription("Returns whether a damage source identifier is registered.")
   public boolean DamageSourceExists(String name) {
      return MLuaDamageSourceRegistry.contains(runtimeOwner(), ownerModId(), name);
   }

   @MLuaDescription("Returns properties for a registered damage source.")
   public LuaValue GetDamageSourceInfo(String name) {
      MLuaDamageSourceRegistry.DamageSourceDef def = MLuaDamageSourceRegistry.get(runtimeOwner(), ownerModId(), name);
      if (def == null) return LuaValue.NIL;
      LuaTable info = new LuaTable();
      info.set("BypassesArmor", LuaValue.valueOf(def.bypassesArmor()));
      info.set("IsFire", LuaValue.valueOf(def.isFire()));
      info.set("IsExplosion", LuaValue.valueOf(def.isExplosion()));
      info.set("IsMagic", LuaValue.valueOf(def.isMagic()));
      info.set("IsProjectile", LuaValue.valueOf(def.isProjectile()));
      info.set("IsFall", LuaValue.valueOf(def.isFall()));
      info.set("IsLightning", LuaValue.valueOf(def.isLightning()));
      return info;
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
