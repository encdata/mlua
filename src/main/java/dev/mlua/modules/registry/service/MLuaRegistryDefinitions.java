package dev.mlua.modules.registry.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.item.alchemy.Potion;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.runtime.registry.MLuaAssetResolver;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;

/** Shared parsing and validation for Lua registry definitions. */
final class MLuaRegistryDefinitions {
   private MLuaRegistryDefinitions() { }

   record MiningProperties(String mineableTag, String requiredTierTag) { }

   static MiningProperties prepareMiningProperties(LuaTable propsTable, Map<String, Object> props) {
      String mineableWith = stringProperty(propsTable, "mineableWith", "MineableWith", "toolType", "ToolType", "effectiveToolTag", "EffectiveToolTag");
      String mineableTag = mineableWith == null || mineableWith.isBlank()
         ? null : "mineable/" + normalizeToolName(mineableWith);

      String requiredTier = stringProperty(propsTable, "requiredToolTier", "RequiredToolTier", "toolTier", "ToolTier", "minToolTier", "MinToolTier");
      if (requiredTier != null && !requiredTier.isBlank()) {
         requiredTier = requiredToolTierTag(requiredTier);
         props.put("requiresCorrectTool", true);
      }
      return new MiningProperties(mineableTag, requiredTier);
   }

   private static String normalizeToolName(String value) {
      return switch (value.trim().toLowerCase()) {
         case "pick", "pickaxe" -> "pickaxe";
         case "axe" -> "axe";
         case "shovel", "spade" -> "shovel";
         case "hoe" -> "hoe";
         default -> throw new LuaError("[MLua] Unsupported mineableWith/toolType value: " + value);
      };
   }

   private static String requiredToolTierTag(String value) {
      return switch (value.trim().toLowerCase()) {
         case "wood", "wooden", "stone" -> "needs_stone_tool";
         case "iron" -> "needs_iron_tool";
         case "diamond", "netherite" -> "needs_diamond_tool";
         default -> throw new LuaError("[MLua] Unsupported requiredToolTier/toolTier value: " + value);
      };
   }

   static void validateCallback(String name, LuaValue callback) {
      if (!callback.isnil() && !callback.isfunction()) {
         throw new LuaError("[MLua] Registry callback '" + name + "' must be a function");
      }
   }

   static String requireContentClass(dev.mlua.module.MLuaRegistration registration, LuaTable definition,
                                             String parentClass) {
      String className = stringProperty(definition, "className", "ClassName");
      if (className == null) return parentClass;
      if (className.isBlank() || registration.classTable(className) == null
          || !registration.isSubclassOf(className, parentClass)) {
         throw new LuaError("[MLua] Content class '" + className + "' must be registered and inherit from " + parentClass);
      }
      return className;
   }

   static void requireLocalRegistryName(String owner, String name, String operation) {
      try {
         dev.mlua.runtime.registry.MLuaResourceNameValidator.validate(owner);
         dev.mlua.runtime.registry.MLuaResourceNameValidator.validate(name);
         if (!isCanonicalLocalResourcePath(owner, name)) {
            throw new IllegalArgumentException("Invalid lowercase local registry path: " + name);
         }
      } catch (IllegalArgumentException invalid) {
         throw new LuaError("[MLua] " + operation
            + " name must be a valid lowercase registry path without a namespace: " + invalid.getMessage());
      }
   }

   private static boolean isCanonicalLocalResourcePath(String owner, String name) {
      var id = MLuaResourceLocations.tryParseOrNull(owner + ":" + name);
      return id != null && owner.equals(MLuaResourceLocations.namespace(id))
         && name.equals(MLuaResourceLocations.path(id));
   }

   static JsonObject optionalJsonObject(LuaTable table, String... keys) {
      if (table == null) {
         return null;
      }

      for (String key : keys) {
         LuaValue value = table.get(key);
         if (!value.isnil()) {
            return MLuaLuaJson.objectArg(value, key);
         }
      }

      return null;
   }

   static MLuaTextureDescriptor textureDescriptor(LuaTable table, String... keys) {
      if (table == null) {
         return MLuaTextureDescriptor.empty();
      } else {
         String[] var2 = keys;
         int var3 = keys.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String key = var2[var4];
            LuaValue value = table.get(key);
            if (!value.isnil()) {
               return MLuaTextureDescriptor.fromLua(value);
            }
         }

         return MLuaTextureDescriptor.empty();
      }
   }

   static List<String> stringValues(LuaValue value, String label) {
      List<String> values = new ArrayList<>();
      if (value.type() == LuaValue.TSTRING) {
         values.add(value.checkjstring());
      } else if (value.istable()) {
         LuaTable entries = value.checktable();
         int length = MLuaLuaJson.jsonArrayLength(entries);
         if (length < 0) throw new LuaError("[MLua] " + label + " table must use dense array indexes");
         for (int index = 1; index <= length; index++) {
            LuaValue entry = entries.get(index);
            if (entry.type() != LuaValue.TSTRING) throw new LuaError("[MLua] " + label + " entries must be strings");
            values.add(entry.tojstring());
         }
      } else {
         throw new LuaError("[MLua] " + label + " must be an id or an array of ids");
      }
      if (values.isEmpty() || values.stream().anyMatch(String::isBlank)) {
         throw new LuaError("[MLua] " + label + " must contain at least one non-empty id");
      }
      return List.copyOf(values);
   }

   static String qualifiedDataId(String owner, String value, boolean tag) {
      String candidate = value.trim();
      if (!candidate.contains(":")) candidate = owner + ":" + candidate;
      var parsed = MLuaResourceLocations.tryParseOrNull(candidate);
      if (parsed == null) throw new LuaError("[MLua] Invalid world generation id: " + value);
      return tag ? "#" + parsed : parsed.toString();
   }

   static String stringProperty(LuaTable table, String... keys) {
      if (table == null) {
         return null;
      } else {
         String[] var2 = keys;
         int var3 = keys.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String key = var2[var4];
            LuaValue value = table.get(key);
            if (value.type() == LuaValue.TSTRING) {
               return value.tojstring();
            }
         }

         return null;
      }
   }

   static String worldGenerationStepProperty(LuaTable definition) {
      LuaValue value = definition.get("step");
      if (value.isnil()) value = definition.get("Step");
      if (value.type() == LuaValue.TSTRING) return value.checkjstring();
      if (value.isnil()) return null;
      dev.mlua.loaderapi.GenerationDecorationStep step = dev.mlua.modules.enums.MLuaEnumValue.toJava(
         value, dev.mlua.loaderapi.GenerationDecorationStep.class);
      if (step == null) {
         throw new LuaError("[MLua] WorldGeneration.addFeature step expects a string or Enum.GenerationStep.Decoration value.");
      }
      return step.name();
   }

   static boolean booleanProperty(LuaTable table, String... keys) {
      if (table == null) {
         return false;
      }
      LuaValue selected = LuaValue.NIL;
      String selectedKey = null;
      for (String key : keys) {
         LuaValue value = table.get(key);
         if (value.isnil()) continue;
         if (selectedKey != null) {
            throw new LuaError("[MLua] Registry properties '" + selectedKey + "' and '" + key + "' are aliases; provide only one.");
         }
         if (!value.isboolean()) {
            throw new LuaError("[MLua] Registry property '" + key + "' must be a boolean.");
         }
         selected = value;
         selectedKey = key;
      }
      return !selected.isnil() && selected.toboolean();
   }

   static boolean hasProperty(LuaTable table, String... keys) {
      if (table == null) {
         return false;
      } else {
         String[] var2 = keys;
         int var3 = keys.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String key = var2[var4];
            if (!table.get(key).isnil()) {
               return true;
            }
         }

         return false;
      }
   }

   static LuaValue callbackProperty(LuaTable table, String... keys) {
      if (table == null) {
         return LuaValue.NIL;
      } else {
         String[] var2 = keys;
         int var3 = keys.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String key = var2[var4];
            LuaValue value = table.get(key);
            if (!value.isnil()) {
               return value;
            }
         }

         return LuaValue.NIL;
      }
   }

   static int wholeIntProperty(LuaTable table, int fallback, String... keys) {
      if (table == null) {
         return fallback;
      } else {
         String[] var3 = keys;
         int var4 = keys.length;

         for(int var5 = 0; var5 < var4; ++var5) {
            String key = var3[var5];
            LuaValue value = table.get(key);
            if (!value.isnil()) {
               return MLuaLuaArguments.wholeInt(value, "Registry.Effects:Register", key);
            }
         }

         return fallback;
      }
   }

   static int colorProperty(LuaTable table, int fallback, String... keys) {
      if (table == null) {
         return fallback;
      } else {
         String[] var3 = keys;
         int var4 = keys.length;

         for(int var5 = 0; var5 < var4; ++var5) {
            String key = var3[var5];
            LuaValue value = table.get(key);
            if (value.isnumber()) {
               return (int)value.tolong();
            }

            if (value.type() == LuaValue.TSTRING) {
               String text = value.tojstring().trim();
               if (text.startsWith("#")) {
                  text = text.substring(1);
               } else if (text.startsWith("0x") || text.startsWith("0X")) {
                  text = text.substring(2);
               }

               try {
                  return (int)Long.parseLong(text, 16);
               } catch (NumberFormatException var9) {
                  throw new LuaError("[MLua] Invalid color value: " + value.tojstring());
               }
            }
         }

         return fallback;
      }
   }

   static MobEffectCategory effectCategory(LuaTable table) {
      String value = stringProperty(table, "category", "Category");
      if (value == null || value.isBlank()) {
         return MobEffectCategory.NEUTRAL;
      } else {
         String normalized = value.trim().toLowerCase();
         switch (normalized) {
            case "beneficial":
            case "benefit":
            case "good":
               return MobEffectCategory.BENEFICIAL;
            case "harmful":
            case "bad":
            case "negative":
               return MobEffectCategory.HARMFUL;
            case "neutral":
               return MobEffectCategory.NEUTRAL;
            default:
               throw new LuaError("[MLua] Unknown mob effect category: " + value);
         }
      }
   }

   static String effectDescriptionId(String owner, String name, String explicit) {
      return explicit != null && !explicit.isBlank() ? explicit : "effect." + owner + "." + name;
   }

    static java.util.List<String> descriptionList(LuaTable table) {
        if (table == null) return null;
        LuaValue desc = table.get("description");
        if (desc.isnil()) {
            desc = table.get("tooltip");
            if (desc.isnil()) return null;
        }
        if (desc.type() == LuaValue.TSTRING) {
            return java.util.List.of(desc.tojstring());
        }
        if (desc.istable()) {
            LuaTable t = desc.checktable();
            int length = t.rawlen();
            java.util.ArrayList<String> lines = new java.util.ArrayList<>(length);
            int entries = 0;
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs entry = t.next(key);
                key = entry.arg1();
                if (key.isnil()) break;
                if (!key.isint() || key.toint() < 1 || key.toint() > length) {
                    throw new LuaError("[MLua] Content description must be a dense array of strings with no extra keys.");
                }
                entries++;
            }
            if (entries != length) {
                throw new LuaError("[MLua] Content description must be a dense array with no missing entries.");
            }
            for (int index = 1; index <= length; index++) {
                LuaValue line = t.rawget(index);
                if (!line.isstring()) {
                    throw new LuaError("[MLua] Content description array entries must be strings.");
                }
                lines.add(line.tojstring());
            }
            return lines.isEmpty() ? null : lines;
        }
        throw new LuaError("[MLua] Content description must be a string or a dense array of strings.");
    }

    static String resolvedModel(String owner, String defaultFolder, LuaTable table, String... keys) {
      String model = stringProperty(table, keys);
      return model != null && !model.isBlank() ? MLuaAssetResolver.resolveModelString(owner, model, defaultFolder) : null;
   }

   static List<MobEffectInstance> potionEffects(LuaTable propsTable) {
      LuaValue effectsValue = propsTable != null ? propsTable.get("effects") : LuaValue.NIL;
      if (effectsValue.isnil()) {
         return List.of();
      } else if (!effectsValue.istable()) {
         throw new LuaError("[MLua] Potion effects must be a table");
      } else {
         List<MobEffectInstance> effects = new ArrayList();
         LuaTable effectsTable = effectsValue.checktable();
         int i = 1;

         while(true) {
            LuaValue entry = effectsTable.get(i);
            if (entry.isnil()) {
               return effects;
            }

            effects.add(potionEffect(entry.checktable()));
            ++i;
         }
      }
   }

   private static MobEffectInstance potionEffect(LuaTable table) {
      String effectId = table.get("effect").checkjstring();
      //--version 1.21.1
      var effect = BuiltInRegistries.MOB_EFFECT.getHolder(MLuaResourceLocations.parse(effectId)).orElse(null);
      //--endversion
      //--version 26.1
      var effect = BuiltInRegistries.MOB_EFFECT.get(MLuaResourceLocations.parse(effectId)).orElse(null);
      //--endversion
      //--version 1.20.1
      MobEffect effect = (MobEffect)BuiltInRegistries.MOB_EFFECT.get(MLuaResourceLocations.parse(effectId));
      //--endversion
      if (effect == null) {
         throw new LuaError("[MLua] Unknown potion effect: " + effectId);
      } else {
         int duration = MLuaLuaArguments.optionalWholeInt(table.get("duration"), 600,
               "Registry.Potions:Register", "duration");
         int amplifier = MLuaLuaArguments.optionalWholeInt(table.get("amplifier"), 0,
               "Registry.Potions:Register", "amplifier");
         if (amplifier < 0) {
            throw new LuaError("[MLua] Registry.Potions:Register amplifier must be non-negative");
         }
         boolean ambient = table.get("ambient").optboolean(false);
         boolean visible = table.get("visible").optboolean(true);
         boolean showIcon = table.get("showIcon").optboolean(true);
         return new MobEffectInstance(effect, duration, amplifier, ambient, visible, showIcon);
      }
   }
}
