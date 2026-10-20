package dev.mlua.modules.players.classes;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.modules.combat.service.MLuaCombatService;
import dev.mlua.modules.effects.service.MLuaPotionEffectsService;
import dev.mlua.mc.impl.damage.MCDamageSourceImpl;
import dev.mlua.mcapi.entity.MCLivingEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Registers methods contributed by LivingEntity and Mob to their Lua classes. */
final class MLuaLivingEntityBindings {
   private MLuaLivingEntityBindings() { }

   static void bind(LuaTable table, LivingEntity entity, MCLivingEntity wrapped,
                    MLuaRegistration registration) {
      MLuaClassMethods.setInstanceMethod(table, "GetHealth", MLuaClassMethods.zeroArg(() -> {
         return LuaValue.valueOf((double)wrapped.getHealth());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetHealth", MLuaClassMethods.varArg((args) -> {
         MLuaEntityWrapper.setHealth(wrapped, (float)args.checkdouble(2));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetMaxHealth", MLuaClassMethods.zeroArg(() -> {
         return LuaValue.valueOf((double)wrapped.getMaxHealth());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetArmor", MLuaClassMethods.zeroArg(() -> {
         return LuaValue.valueOf(wrapped.getArmorValue());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetAbsorption", MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(entity.getAbsorptionAmount())));
      MLuaClassMethods.setInstanceMethod(table, "SetAbsorption", MLuaClassMethods.varArg((args) -> {
         MLuaEntityWrapper.setAbsorption(entity, (float)args.checkdouble(2));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GiveEffect", MLuaClassMethods.varArg((args) -> {
         return MLuaPotionEffectsService.giveEffect(entity, args);
      }));
      MLuaClassMethods.setInstanceMethod(table, "RemoveEffect", MLuaClassMethods.varArg((args) -> {
         //--version 1.21.1,26.1
         return LuaValue.valueOf(entity.removeEffect(MLuaPotionEffectsService.resolveHolderFromLua(args.arg(2))));
         //--endversion
         //--version 1.20.1
         return LuaValue.valueOf(entity.removeEffect(MLuaPotionEffectsService.resolveFromLua(args.arg(2))));
         //--endversion
      }));
      MLuaClassMethods.setInstanceMethod(table, "HasEffect", MLuaClassMethods.varArg((args) -> {
         //--version 1.21.1,26.1
         return LuaValue.valueOf(entity.hasEffect(MLuaPotionEffectsService.resolveHolderFromLua(args.arg(2))));
         //--endversion
         //--version 1.20.1
         return LuaValue.valueOf(entity.hasEffect(MLuaPotionEffectsService.resolveFromLua(args.arg(2))));
         //--endversion
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetEffects", MLuaClassMethods.zeroArg(() ->
         MLuaPotionEffectsService.effectsTable(entity, registration)));
      MLuaClassMethods.setInstanceMethod(table, "ClearEffects", MLuaClassMethods.zeroArg(() -> {
         entity.removeAllEffects();
         return LuaValue.NONE;
      }));
      if (entity instanceof Mob mob) {
         MLuaClassMethods.setInstanceMethod(table, "NavigateTo", MLuaClassMethods.varArg((args) -> {
            Level level = mob.level();
            if (level.isClientSide) throw new LuaError("[MLua] Mob:NavigateTo is server-side only.");
            double[] destination = MLuaCFrame.vector(args.arg(2));
            double speed = args.optdouble(3, 1.0);
            if (!Double.isFinite(speed) || speed <= 0) {
               throw new LuaError("[MLua] Mob:NavigateTo speed must be finite and greater than zero.");
            }
            return LuaValue.valueOf(mob.getNavigation().moveTo(destination[0], destination[1], destination[2], speed));
         }));
         MLuaClassMethods.setInstanceMethod(table, "StopNavigation", MLuaClassMethods.zeroArg(() -> {
            if (mob.level().isClientSide) throw new LuaError("[MLua] Mob:StopNavigation is server-side only.");
            mob.getNavigation().stop();
            return LuaValue.NONE;
         }));
         MLuaClassMethods.setInstanceMethod(table, "IsNavigationDone", MLuaClassMethods.zeroArg(() ->
            LuaValue.valueOf(mob.getNavigation().isDone())));
         MLuaClassMethods.setInstanceMethod(table, "GetTarget", MLuaClassMethods.zeroArg(() -> {
            LivingEntity target = mob.getTarget();
            return target == null ? LuaValue.NIL : MLuaEntityWrapper.entityTable(target, registration);
         }));
         MLuaClassMethods.setInstanceMethod(table, "SetTarget", MLuaClassMethods.varArg((args) -> {
            if (mob.level().isClientSide) throw new LuaError("[MLua] Mob:SetTarget is server-side only.");
            LuaValue targetValue = args.arg(2);
            if (targetValue.isnil()) {
               mob.setTarget(null);
               return LuaValue.NONE;
            }
            if (!targetValue.istable()) throw new LuaError("[MLua] Mob:SetTarget expects a LivingEntity or nil.");
            LuaValue rawTarget = targetValue.checktable().rawget("__entity");
            if (!rawTarget.isuserdata(LivingEntity.class)) {
               throw new LuaError("[MLua] Mob:SetTarget expects a LivingEntity or nil.");
            }
            LivingEntity target =
               (LivingEntity)rawTarget.touserdata(LivingEntity.class);
            if (target.level() != mob.level()) {
               throw new LuaError("[MLua] Mob:SetTarget expects an entity in the same dimension.");
            }
            mob.setTarget(target);
            return LuaValue.NONE;
         }));
         MLuaClassMethods.setInstanceMethod(table, "CanSee", MLuaClassMethods.varArg((args) -> {
            Entity target = entityFromLua(args.arg(2));
            return LuaValue.valueOf(target != null && mob.hasLineOfSight(target));
         }));
         MLuaClassMethods.setInstanceMethod(table, "CanAttack", MLuaClassMethods.varArg((args) -> {
            LuaValue targetValue = args.arg(2);
            if (!targetValue.istable()) return LuaValue.FALSE;
            LuaValue rawTarget = targetValue.checktable().rawget("__entity");
            return LuaValue.valueOf(rawTarget.isuserdata(LivingEntity.class)
               && mob.canAttack((LivingEntity)rawTarget.touserdata(LivingEntity.class)));
         }));
         MLuaClassMethods.setInstanceMethod(table, "GetPersistenceRequired", MLuaClassMethods.zeroArg(() -> LuaValue.valueOf(mob.isPersistenceRequired())));
         MLuaClassMethods.setInstanceMethod(table, "SetPersistenceRequired", MLuaClassMethods.zeroArg(() -> {
            if (mob.level().isClientSide) throw new LuaError("[MLua] Mob:SetPersistenceRequired is server-side only.");
            mob.setPersistenceRequired();
            return LuaValue.NONE;
         }));
      }
       MLuaClassMethods.setInstanceMethod(table, "Damage", MLuaClassMethods.varArg((args) -> {
          float amount = (float)args.checkdouble(2);
          if (!Float.isFinite(amount) || amount < 0) {
             throw new LuaError("[MLua] LivingEntity:Damage amount must be finite and non-negative.");
          }
          if (entity.level().isClientSide) {
             throw new LuaError("[MLua] LivingEntity:Damage is server-side only.");
          }
          LuaValue sourceValue = args.arg(3);
          if (!sourceValue.isnil() && !sourceValue.isstring()) {
             throw new LuaError("[MLua] LivingEntity:Damage sourceName must be a string when provided.");
          }
          String sourceStr = sourceValue.isnil() ? "generic" : sourceValue.checkjstring();
          Entity attacker = entityFromLua(args.arg(4));
          Object runtimeOwner = registration instanceof MLuaRuntime runtime ? runtime.session() : null;
          String damageOwner = "";
          String damageName = sourceStr;
          int namespaceSeparator = sourceStr.indexOf(':');
          if (namespaceSeparator > 0) {
             damageOwner = sourceStr.substring(0, namespaceSeparator);
             damageName = sourceStr.substring(namespaceSeparator + 1);
          }
          DamageSource mcSource = MLuaCombatService.createDamageSource(entity, damageName, attacker, runtimeOwner, damageOwner);
          return LuaValue.valueOf(wrapped.hurt(new MCDamageSourceImpl(mcSource), amount));
       }));
       MLuaClassMethods.setInstanceMethod(table, "Heal", MLuaClassMethods.varArg((args) -> {
          float amount = (float)args.checkdouble(2);
          if (!Float.isFinite(amount) || amount < 0) {
             throw new LuaError("[MLua] LivingEntity:Heal amount must be finite and non-negative.");
          }
          if (entity.level().isClientSide) throw new LuaError("[MLua] LivingEntity:Heal is server-side only.");
          entity.heal(amount);
          return LuaValue.NONE;
       }));
       MLuaClassMethods.setInstanceMethod(table, "GetAttribute", MLuaClassMethods.varArg((args) -> {
         String attrName = args.checkjstring(2);
         //--version 1.21.1
         Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.getHolder(MLuaResourceLocations.parse(attrName)).orElse(null);
         //--endversion
         //--version 26.1
         Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName)).orElse(null);
         //--endversion
         //--version 1.20.1
         Attribute attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName));
         //--endversion
         if (attr == null) {
            return LuaValue.NIL;
         }
         AttributeInstance instance = entity.getAttribute(attr);
          return instance == null ? LuaValue.NIL : LuaValue.valueOf(instance.getValue());
       }));
       MLuaClassMethods.setInstanceMethod(table, "GetAttributeInstance", MLuaClassMethods.varArg((args) -> {
          String attrName = args.checkjstring(2);
          //--version 1.21.1
          Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.getHolder(MLuaResourceLocations.parse(attrName)).orElse(null);
          //--endversion
          //--version 26.1
          Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName)).orElse(null);
          //--endversion
          //--version 1.20.1
          Attribute attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName));
          //--endversion
          if (attr == null) return LuaValue.NIL;
          AttributeInstance instance = entity.getAttribute(attr);
          return instance == null ? LuaValue.NIL : dev.mlua.minecraft.binding.MLuaAttributeInstance.create(registration,
             MLuaResourceLocations.parse(attrName).toString(), instance, args.arg1().checktable());
       }));
       MLuaClassMethods.setInstanceMethod(table, "SetAttribute", MLuaClassMethods.varArg((args) -> {
          String attrName = args.checkjstring(2);
          double value = dev.mlua.minecraft.binding.MLuaAttributeInstance.finiteValue(args.arg(3), "base value");
          //--version 1.21.1
          Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.getHolder(MLuaResourceLocations.parse(attrName)).orElse(null);
          //--endversion
          //--version 26.1
          Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName)).orElse(null);
          //--endversion
          //--version 1.20.1
          Attribute attr = BuiltInRegistries.ATTRIBUTE.get(MLuaResourceLocations.parse(attrName));
          //--endversion
          if (attr == null) {
             return LuaValue.FALSE;
          }
          AttributeInstance instance = entity.getAttribute(attr);
         if (instance == null) {
            return LuaValue.FALSE;
         }
         instance.setBaseValue(value);
         return LuaValue.TRUE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetEquipment", MLuaClassMethods.varArg((args) -> {
         EquipmentSlot slot = MLuaMinecraftEnums.resolveSlot(args.arg(2));
          return MLuaItemWrapper.itemTable(entity.getItemBySlot(slot), registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetEquipment", MLuaClassMethods.varArg((args) -> {
         EquipmentSlot slot = MLuaMinecraftEnums.resolveSlot(args.arg(2));
         ItemStack stack = MLuaItemWrapper.toItemStack(args.arg(3), registration);
         //--version 1.21.1
         entity.setItemSlot(slot, stack);
         //--endversion
         //--version 1.20.1
         entity.setItemSlot(slot, stack);
         //--endversion
         return LuaValue.NONE;
      }));
   }

   private static Entity entityFromLua(LuaValue value) {
      if (!value.istable()) return null;
      LuaValue raw = value.checktable().rawget("__entity");
      return raw.isuserdata(Entity.class) ? (Entity) raw.touserdata(Entity.class) : null;
   }




}
