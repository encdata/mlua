package dev.mlua.modules.effects.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
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

@MLuaDescription("Queries registered potion effects and exposes effect metadata to Lua.")
public final class MLuaPotionEffectsService extends MLuaReflectiveService {
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

   @MLuaDescription("Returns metadata for a potion effect identifier.")
   public LuaTable Get(String id) {
      return effectTable(id);
   }

   @MLuaDescription("Returns whether a potion effect identifier exists.")
   public boolean Exists(String id) {
      return resolve(id) != null;
   }

   @MLuaDescription("Returns metadata for all registered potion effects.")
   public LuaTable GetAll() {
      LuaTable values = new LuaTable();
      int index = 1;
      for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
         ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
         if (id != null) values.set(index++, effectTable(id.toString(), effect));
      }
      return values;
   }

   public static LuaTable effectTable(String id) {
      MobEffect effect = resolveOrThrow(id);
      return effectTable(id, effect);
   }

   public static LuaTable effectTable(String id, MobEffect effect) {
      ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
      String fullId = key == null ? id : key.toString();
      LuaTable table = new LuaTable();
      table.set("Id", fullId);
      table.set("Name", Component.translatable(effect.getDescriptionId()).getString());
      table.set("TranslationKey", effect.getDescriptionId());
      table.set("DescriptionId", effect.getDescriptionId());
      table.set("Color", effect.getColor());
      table.set("IsBeneficial", LuaValue.valueOf(effect.isBeneficial()));
      table.set("IsInstant", LuaValue.valueOf(effect.isInstantenous()));
      if (effect instanceof MLuaScriptedMobEffect scripted && scripted.getIconTexture() != null) {
         table.set("Icon", scripted.getIconTexture().toString());
      }

      return table;
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
      LuaTable list = new LuaTable();
      int i = 1;
      for (MobEffectInstance instance : entity.getActiveEffects()) {
         LuaTable entry = new LuaTable();
         MobEffect effect = instance.getEffect().value();
         ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
         entry.set("Id", id != null ? id.toString() : "unknown");
         entry.set("Duration", instance.getDuration());
         entry.set("Amplifier", instance.getAmplifier());
         entry.set("Ambient", instance.isAmbient() ? LuaValue.TRUE : LuaValue.FALSE);
         entry.set("ShowParticles", instance.isVisible() ? LuaValue.TRUE : LuaValue.FALSE);
         list.set(i++, entry);
      }

      return list;
   }
   //--endversion
   //--version 1.20.1
   public static LuaTable effectsTable(LivingEntity entity) {
      LuaTable list = new LuaTable();
      int i = 1;
      for (MobEffectInstance instance : entity.getActiveEffects()) {
         LuaTable entry = new LuaTable();
         MobEffect effect = instance.getEffect();
         ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
         entry.set("Id", id != null ? id.toString() : "unknown");
         entry.set("Duration", instance.getDuration());
         entry.set("Amplifier", instance.getAmplifier());
         entry.set("Ambient", instance.isAmbient() ? LuaValue.TRUE : LuaValue.FALSE);
         entry.set("ShowParticles", instance.isVisible() ? LuaValue.TRUE : LuaValue.FALSE);
         list.set(i++, entry);
      }

      return list;
   }
   //--endversion

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
      ResourceLocation key = MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
      if (key == null) throw new LuaError("[MLua] Unknown potion effect: " + id);
      return BuiltInRegistries.MOB_EFFECT.getHolder(key).orElseThrow(() -> new LuaError("[MLua] Unknown potion effect: " + id));
   }
   //--endversion
   //--version 26.1
   private static Holder<MobEffect> resolveHolderOrThrow(String id) {
      ResourceLocation key = MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
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
      ResourceLocation key = MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
      return key == null ? null : (MobEffect)BuiltInRegistries.MOB_EFFECT.get(key);
   }
   //--endversion
   //--version 26.1
   private static MobEffect resolve(String id) {
      ResourceLocation key = MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
      return key == null ? null : BuiltInRegistries.MOB_EFFECT.get(key).map(Holder.Reference::value).orElse(null);
   }
   //--endversion
}
