package dev.mlua.modules.registry.service;

import java.util.List;
import java.util.Map;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.scripted.MLuaScriptedMobEffect;
import dev.mlua.runtime.registry.MLuaAssetResolver;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;

/** Installs particle, potion, and scripted mob-effect registration categories. */
final class MLuaRegistryVisualContentApi {
   private MLuaRegistryVisualContentApi() { }

   static void install(MLuaRegistryService service, LuaTable particles, LuaTable potions, LuaTable effects) {
      particles.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String name = args.checkjstring(2);
            MLuaRegistryContentValidation.requireLocalRegistryName(service.apiContext().mlua().ownerModId(),
               name, "Registry.Particles:Register");
            LuaTable propsTable = args.arg(3).opttable((LuaTable)null);
            boolean overrideLimiter = propsTable != null && propsTable.get("overrideLimiter").optboolean(false);
            String owner = service.apiContext().mlua().ownerModId();
            MLuaLoaderRegistry.registerParticle(owner, name, overrideLimiter);
            return service.registryEntry(RegistryKind.PARTICLE, owner, name);
         }
      });
      potions.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String name = args.checkjstring(2);
            MLuaRegistryContentValidation.requireLocalRegistryName(service.apiContext().mlua().ownerModId(),
               name, "Registry.Potions:Register");
            LuaTable propsTable = args.arg(3).opttable((LuaTable)null);
            List<MobEffectInstance> effects = MLuaRegistryEffectDefinitions.potionEffects(propsTable);
            String owner = service.apiContext().mlua().ownerModId();
            MLuaLoaderRegistry.register(RegistryKind.POTION, owner, name, () -> {
               //--version 26.1
               return new Potion(owner + ":" + name,
                  effects.toArray(MobEffectInstance[]::new));
               //--endversion
               //--version 1.20.1,1.21.1
               return new Potion((MobEffectInstance[])effects.toArray((x$0) -> {
                  return new MobEffectInstance[x$0];
               }));
               //--endversion
            });
            return service.registryEntry(RegistryKind.POTION, owner, name);
         }
      });
      effects.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String name = args.checkjstring(2);
            String owner = service.apiContext().mlua().ownerModId();
            MLuaRegistryContentValidation.requireLocalRegistryName(owner, name, "Registry.Effects:Register");
            LuaTable propsTable = args.arg(3).opttable((LuaTable)null);
            String key = owner + ":" + name;
            LuaValue onTick = MLuaRegistryPropertyReaders.callbackProperty(propsTable, "onTick", "OnTick");
            LuaValue onApplied = MLuaRegistryPropertyReaders.callbackProperty(propsTable, "onApplied", "OnApplied");
            LuaValue onEnding = MLuaRegistryPropertyReaders.callbackProperty(propsTable, "onEnding", "OnEnding");
            MLuaRegistryContentValidation.validateCallback("onTick", onTick);
            MLuaRegistryContentValidation.validateCallback("onApplied", onApplied);
            MLuaRegistryContentValidation.validateCallback("onEnding", onEnding);
            MobEffectCategory category = MLuaRegistryEffectDefinitions.category(propsTable);
            int color = MLuaRegistryPropertyReaders.colorProperty(propsTable, 16777215, "color", "Color");
            String descriptionId = MLuaRegistryPropertyReaders.stringProperty(propsTable, "translation", "Translation", "descriptionId", "DescriptionId", "name", "Name");
            String iconValue = MLuaRegistryPropertyReaders.stringProperty(propsTable, "icon", "Icon");
            boolean visibleInInventory = MLuaRegistryPropertyReaders.hasProperty(propsTable, "visibleInInventory", "VisibleInInventory") ? MLuaRegistryPropertyReaders.booleanProperty(propsTable, "visibleInInventory", "VisibleInInventory") : true;
            boolean visibleInGui = MLuaRegistryPropertyReaders.hasProperty(propsTable, "visibleInGui", "VisibleInGui") ? MLuaRegistryPropertyReaders.booleanProperty(propsTable, "visibleInGui", "VisibleInGui") : true;
            boolean instant = MLuaRegistryPropertyReaders.booleanProperty(propsTable, "instant", "Instant");
            int tickInterval = MLuaRegistryPropertyReaders.wholeIntProperty(propsTable, 1, "tickInterval", "TickInterval");
            if (tickInterval < 1) {
               throw new LuaError("[MLua] Registry.Effects:Register tickInterval must be at least 1 tick");
            }
            MLuaLoaderRegistry.register(RegistryKind.MOB_EFFECT, owner, name, () -> {
               return new MLuaScriptedMobEffect(category, color, key, MLuaRegistryEffectDefinitions.descriptionId(owner, name, descriptionId), iconValue != null && !iconValue.isBlank() ? MLuaAssetResolver.resolveTexture(owner, iconValue, "mob_effect") : null, visibleInInventory, visibleInGui, instant, tickInterval);
            });
            service.replaceBehaviors("effect", key, Map.of(
               "onTick", onTick, "onApplied", onApplied, "onEnding", onEnding));
            return service.registryEntry(RegistryKind.MOB_EFFECT, owner, name);
         }
      });
   }
}
