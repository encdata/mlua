package dev.mlua.modules.effects.Service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.scripted.MLuaScriptedMobEffect;
import java.util.Iterator;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaPotionEffectsService extends MLuaServiceBase {
   public MLuaPotionEffectsService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "PotionEffects";
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__side", "Both");
      table.set("Get", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaPotionEffectsService.effectTable(args.checkjstring(2));
         }
      });
      table.set("Exists", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(MLuaPotionEffectsService.resolve(args.checkjstring(2)) != null);
         }
      });
      table.set("GetAll", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable values = new LuaTable();
            int index = 1;
            Iterator<MobEffect> iterator = BuiltInRegistries.MOB_EFFECT.iterator();

            while (iterator.hasNext()) {
               MobEffect effect = iterator.next();
               ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
               if (id != null) {
                  values.set(index++, MLuaPotionEffectsService.effectTable(id.toString(), effect));
               }
            }

            return values;
         }
      });
      return table;
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

   public static LuaValue giveEffect(LivingEntity entity, Varargs args) {
      Holder<MobEffect> effect = resolveHolderFromLua(args.arg(2));
      int duration = args.optint(3, 600);
      int amplifier = args.optint(4, 0);
      boolean ambient = args.optboolean(5, false);
      boolean particles = args.optboolean(6, true);
      boolean icon = args.optboolean(7, true);
      boolean applied = entity.addEffect(new MobEffectInstance(effect, duration, amplifier, ambient, particles, icon));
      return LuaValue.valueOf(applied);
   }

   public static LuaTable effectsTable(LivingEntity entity) {
      LuaTable list = new LuaTable();
      int index = 1;
      for (MobEffectInstance instance : entity.getActiveEffects()) {
         LuaTable entry = new LuaTable();
         MobEffect effect = instance.getEffect().value();
         ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
         entry.set("Id", id != null ? id.toString() : "unknown");
         entry.set("Duration", instance.getDuration());
         entry.set("Amplifier", instance.getAmplifier());
         entry.set("Ambient", instance.isAmbient() ? LuaValue.TRUE : LuaValue.FALSE);
         entry.set("ShowParticles", instance.isVisible() ? LuaValue.TRUE : LuaValue.FALSE);
         list.set(index++, entry);
      }

      return list;
   }

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

   private static MobEffect resolveOrThrow(String id) {
      MobEffect effect = resolve(id);
      if (effect == null) {
         throw new LuaError("[MLua] Unknown potion effect: " + id);
      } else {
         return effect;
      }
   }

   private static Holder<MobEffect> resolveHolderOrThrow(String id) {
      ResourceLocation key = MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
      if (key == null) {
         throw new LuaError("[MLua] Unknown potion effect: " + id);
      }
      return BuiltInRegistries.MOB_EFFECT.getHolder(key).orElseThrow(() -> new LuaError("[MLua] Unknown potion effect: " + id));
   }

   private static MobEffect resolve(String id) {
      ResourceLocation key = MLuaResourceLocations.tryParseOrNull(id.contains(":") ? id : "minecraft:" + id);
      return key == null ? null : BuiltInRegistries.MOB_EFFECT.get(key);
   }
}
