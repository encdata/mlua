package dev.mlua.modules.effects.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.module.MLuaRegistration;
//--version 1.21.1,26.1
import net.minecraft.core.Holder;
//--endversion
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import dev.mlua.minecraft.scripted.MLuaScriptedMobEffect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

@MLuaDescription("Queries registered potion effects and exposes effect metadata to Lua.")
public final class MLuaPotionEffectsService extends MLuaServerThreadReflectiveService {
   public MLuaPotionEffectsService(MLuaService.Context context) {
      super(context);
   }

   @Override
   public String name() {
      return "PotionEffects";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Both");
      return table;
   }

   @MLuaDescription("Returns a PotionEffectType instance for a registered effect identifier. Raises a Lua error when the identifier is unknown.")
   public LuaTable Get(String id) {
      MobEffect effect = resolveOrThrow(id);
      return effectInstance(id, effect);
   }

   @MLuaDescription("Returns whether an effect identifier resolves to a registered potion effect; unknown or malformed identifiers return false.")
   public boolean Exists(String id) {
      return resolve(id) != null;
   }

   @MLuaDescription("Returns PotionEffectType instances for all registered potion effects in stable identifier order.")
   public LuaTable GetAll() {
      LuaTable values = new LuaTable();
      var effects = new ArrayList<Map.Entry<ResourceLocation, MobEffect>>();
      for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
         ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
         if (id != null) effects.add(java.util.Map.entry(id, effect));
      }
      effects.sort(Comparator.comparing(entry -> entry.getKey().toString()));
      int index = 1;
      for (Map.Entry<ResourceLocation, MobEffect> entry : effects) {
         values.set(index++, effectInstance(entry.getKey().toString(), entry.getValue()));
      }
      return values;
   }

   public static LuaTable effectTable(String id) {
      MobEffect effect = resolveOrThrow(id);
      return effectTable(id, effect);
   }

   public static LuaTable effectTable(String id, MobEffect effect) {
      LuaTable table = new LuaTable();
      fillEffectTable(table, id, effect);
      return table;
   }

   private LuaTable effectInstance(String id, MobEffect effect) {
      LuaTable instance = this.context.mlua().registration().createClassInstance("PotionEffectType");
      fillEffectTable(instance, id, effect);
      instance.freeze();
      return instance;
   }

   private static void fillEffectTable(LuaTable table, String id, MobEffect effect) {
      ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
      String fullId = key == null ? id : key.toString();
      table.rawset("Id", LuaValue.valueOf(fullId));
      table.rawset("Name", LuaValue.valueOf(Component.translatable(effect.getDescriptionId()).getString()));
      table.rawset("TranslationKey", LuaValue.valueOf(effect.getDescriptionId()));
      table.rawset("DescriptionId", LuaValue.valueOf(effect.getDescriptionId()));
      table.rawset("Color", LuaValue.valueOf(effect.getColor()));
      table.rawset("IsBeneficial", LuaValue.valueOf(effect.isBeneficial()));
      table.rawset("IsInstant", LuaValue.valueOf(effect.isInstantenous()));
      if (effect instanceof MLuaScriptedMobEffect scripted && scripted.getIconTexture() != null) {
         table.rawset("Icon", LuaValue.valueOf(scripted.getIconTexture().toString()));
      }
   }

   //--version 1.21.1,26.1
   public static LuaValue giveEffect(LivingEntity entity, Varargs args) {
      Holder<MobEffect> effect = resolveHolderFromLua(args.arg(2));
      int duration = MLuaLuaArguments.optionalWholeInt(args, 3, 600, "LivingEntity:GiveEffect", "duration");
      int amplifier = MLuaLuaArguments.optionalWholeInt(args, 4, 0, "LivingEntity:GiveEffect", "amplifier");
      if (amplifier < 0) throw new LuaError("[MLua] LivingEntity:GiveEffect amplifier must be at least zero.");
      boolean ambient = args.optboolean(5, false);
      boolean particles = args.optboolean(6, true);
      boolean icon = args.optboolean(7, true);
      boolean applied = entity.addEffect(new MobEffectInstance(effect, duration, amplifier, ambient, particles, icon));
      return LuaValue.valueOf(applied);
   }
   //--endversion
   //--version 1.20.1
   public static LuaValue giveEffect(LivingEntity entity, Varargs args) {
      MobEffect effect = resolveFromLua(args.arg(2));
      int duration = MLuaLuaArguments.optionalWholeInt(args, 3, 600, "LivingEntity:GiveEffect", "duration");
      int amplifier = MLuaLuaArguments.optionalWholeInt(args, 4, 0, "LivingEntity:GiveEffect", "amplifier");
      if (amplifier < 0) throw new LuaError("[MLua] LivingEntity:GiveEffect amplifier must be at least zero.");
      boolean ambient = args.optboolean(5, false);
      boolean particles = args.optboolean(6, true);
      boolean icon = args.optboolean(7, true);
      boolean applied = entity.addEffect(new MobEffectInstance(effect, duration, amplifier, ambient, particles, icon));
      return LuaValue.valueOf(applied);
   }
   //--endversion

   //--version 1.21.1,26.1
   public static LuaTable effectsTable(LivingEntity entity) {
      return effectsTable(entity, null);
   }

   public static LuaTable effectsTable(LivingEntity entity, MLuaRegistration registration) {
      LuaTable list = new LuaTable();
      int i = 1;
      for (MobEffectInstance instance : entity.getActiveEffects()) {
         LuaTable entry = activeEffectEntry(registration);
         MobEffect effect = instance.getEffect().value();
         ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
         entry.rawset("Id", LuaValue.valueOf(id != null ? id.toString() : "unknown"));
         entry.rawset("Duration", LuaValue.valueOf(instance.getDuration()));
         entry.rawset("Amplifier", LuaValue.valueOf(instance.getAmplifier()));
         entry.rawset("Ambient", LuaValue.valueOf(instance.isAmbient()));
         entry.rawset("ShowParticles", LuaValue.valueOf(instance.isVisible()));
         if (registration != null) entry.freeze();
         list.set(i++, entry);
      }

      return list;
   }
   //--endversion
   //--version 1.20.1
   public static LuaTable effectsTable(LivingEntity entity) {
      return effectsTable(entity, null);
   }

   public static LuaTable effectsTable(LivingEntity entity, MLuaRegistration registration) {
      LuaTable list = new LuaTable();
      int i = 1;
      for (MobEffectInstance instance : entity.getActiveEffects()) {
         LuaTable entry = activeEffectEntry(registration);
         MobEffect effect = instance.getEffect();
         ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
         entry.rawset("Id", LuaValue.valueOf(id != null ? id.toString() : "unknown"));
         entry.rawset("Duration", LuaValue.valueOf(instance.getDuration()));
         entry.rawset("Amplifier", LuaValue.valueOf(instance.getAmplifier()));
         entry.rawset("Ambient", LuaValue.valueOf(instance.isAmbient()));
         entry.rawset("ShowParticles", LuaValue.valueOf(instance.isVisible()));
         if (registration != null) entry.freeze();
         list.set(i++, entry);
      }

      return list;
   }
   //--endversion

   private static LuaTable activeEffectEntry(MLuaRegistration registration) {
      return registration == null ? new LuaTable() : registration.createClassInstance("ActivePotionEffect");
   }

   //--version 1.21.1,26.1
   public static Holder<MobEffect> resolveHolderFromLua(LuaValue value) {
      if (value.istable()) {
         LuaValue id = value.checktable().get("Id");
         if (id.isnil()) {
            id = value.checktable().get("Name");
         }

         if (id.isnil()) {
            throw new LuaError("[MLua] Potion effect table must contain an 'Id' or 'Name' field");
         }

         return resolveHolderOrThrow(id.checkjstring());
      } else {
         return resolveHolderOrThrow(value.checkjstring());
      }
   }

   public static Holder<MobEffect> resolveFromLua(LuaValue value) {
      return resolveHolderFromLua(value);
   }

   //--endversion
   //--version 1.21.1
   private static Holder<MobEffect> resolveHolderOrThrow(String id) {
      ResourceLocation key = effectKey(id);
      if (key == null) throw new LuaError("[MLua] Unknown potion effect: " + id);
      return BuiltInRegistries.MOB_EFFECT.getHolder(key).orElseThrow(() -> new LuaError("[MLua] Unknown potion effect: " + id));
   }
   //--endversion
   //--version 26.1
   private static Holder<MobEffect> resolveHolderOrThrow(String id) {
      ResourceLocation key = effectKey(id);
      if (key == null) throw new LuaError("[MLua] Unknown potion effect: " + id);
      return BuiltInRegistries.MOB_EFFECT.get(key).orElseThrow(() -> new LuaError("[MLua] Unknown potion effect: " + id));
   }
   //--endversion
   //--version 1.20.1
   public static MobEffect resolveFromLua(LuaValue value) {
      if (value.istable()) {
         LuaValue id = value.checktable().get("Id");
         if (id.isnil()) {
            id = value.checktable().get("Name");
         }

         if (id.isnil()) {
            throw new LuaError("[MLua] Potion effect table must contain an 'Id' or 'Name' field");
         }

         return resolveOrThrow(id.checkjstring());
      } else {
         return resolveOrThrow(value.checkjstring());
      }
   }
   //--endversion

   private static MobEffect resolveOrThrow(String id) {
      MobEffect effect = resolve(id);
      if (effect == null) {
         throw new LuaError("[MLua] Unknown potion effect: " + id);
      } else {
         return effect;
      }
   }

   //--version 1.20.1,1.21.1
   private static MobEffect resolve(String id) {
      ResourceLocation key = effectKey(id);
      return key == null ? null : (MobEffect)BuiltInRegistries.MOB_EFFECT.get(key);
   }
   //--endversion
   //--version 26.1
   private static MobEffect resolve(String id) {
      ResourceLocation key = effectKey(id);
      return key == null ? null : BuiltInRegistries.MOB_EFFECT.get(key).map(Holder.Reference::value).orElse(null);
   }
   //--endversion

   private static ResourceLocation effectKey(String id) {
      if (id == null || id.isBlank()) return null;
      return MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
   }
}
