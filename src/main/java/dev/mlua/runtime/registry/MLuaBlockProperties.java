package dev.mlua.runtime.registry;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.mcapi.MCApiRuntime;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.resources.ResourceLocation;

/** Owns block definition validation and construction of Minecraft block properties. */
final class MLuaBlockProperties {
   private static final Map<String, SoundType> SOUNDS = sounds();
   private static final Map<String, MapColor> MAP_COLORS = mapColors();
   private static final List<String> SOUND_NAMES = List.copyOf(SOUNDS.keySet());
   private static final List<String> MAP_COLOR_NAMES = List.copyOf(MAP_COLORS.keySet());

   static List<String> soundNames() {
      return SOUND_NAMES;
   }

   static List<String> mapColorNames() {
      return MAP_COLOR_NAMES;
   }

   static void validateCopyDefinition(Map<String, Object> properties) {
      Object copy = properties.get("copy");
      if (copy != null && (!(copy instanceof String id) || id.isBlank())) {
         throw new IllegalArgumentException("[MLua] Block copy must be a non-empty block identifier");
      }
      if (copy instanceof String id && MLuaResourceLocations.tryParseOrNull(id) == null) {
         throw new IllegalArgumentException("[MLua] Invalid block copy identifier '" + id + "'");
      }
   }

   static void validateBlockProperties(Map<String, Object> properties) {
      Object hardness = aliasedProperty(properties, "hardness", "strength");
      Object resistance = aliasedProperty(properties, "resistance", "explosionResistance");
      validateNumber(hardness, "hardness", -1.0, Float.MAX_VALUE, false);
      validateNumber(resistance, "resistance", 0.0, Float.MAX_VALUE, false);
      if (resistance != null && hardness == null) {
         throw new IllegalArgumentException("[MLua] Block resistance requires a hardness value");
      }
      validateNumber(properties, "lightLevel", 0.0, 15.0, true);
      validateNumber(properties, "friction", 0.0, Float.MAX_VALUE, false);
      validateNumber(properties, "speedFactor", 0.0, Float.MAX_VALUE, false);
      validateNumber(properties, "jumpFactor", 0.0, Float.MAX_VALUE, false);
      for (String property : new String[]{"requiresCorrectTool", "collision", "noCollision", "noOcclusion",
            "randomTicks", "ignitedByLava", "replaceable"}) {
         Object value = properties.get(property);
         if (value != null && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("[MLua] Block property '" + property + "' must be a boolean");
         }
      }
      if (Boolean.TRUE.equals(properties.get("collision")) && Boolean.TRUE.equals(properties.get("noCollision"))) {
         throw new IllegalArgumentException("[MLua] Block properties 'collision' and 'noCollision' conflict");
      }
      for (String property : new String[]{"sound", "mapColor"}) {
         Object value = properties.get(property);
         if (value != null && (!(value instanceof String name) || name.isBlank())) {
            throw new IllegalArgumentException("[MLua] Block property '" + property + "' must be a non-empty string");
         }
      }
      if (properties.get("sound") instanceof String name) sound(name);
      if (properties.get("mapColor") instanceof String name) mapColor(name);
      validateEnumProperty(properties, "pushReaction", PushReaction.class);
      validateEnumProperty(properties, "instrument", NoteBlockInstrument.class);
   }

   private static void validateNumber(Map<String, Object> properties, String name,
      double minimum, double maximum, boolean wholeNumber) {
      validateNumber(properties.get(name), name, minimum, maximum, wholeNumber);
   }

   private static void validateNumber(Object value, String name, double minimum, double maximum, boolean wholeNumber) {
      if (value == null) return;
      if (!(value instanceof Number number)) {
         throw new IllegalArgumentException("[MLua] Block property '" + name + "' must be numeric");
      }
      double converted = number.doubleValue();
      if (!Double.isFinite(converted) || converted < minimum || converted > maximum
         || wholeNumber && converted != Math.rint(converted)) {
         String range = wholeNumber ? "a whole number from " : "a finite number from ";
         throw new IllegalArgumentException("[MLua] Block property '" + name + "' must be " + range + minimum + " to " + maximum);
      }
   }

   private static Object aliasedProperty(Map<String, Object> properties, String canonicalName, String alias) {
      Object canonical = properties.get(canonicalName);
      Object alternate = properties.get(alias);
      if (canonical != null && alternate != null) {
         throw new IllegalArgumentException("[MLua] Provide only one of block properties '" + canonicalName
            + "' and '" + alias + "'");
      }
      return canonical != null ? canonical : alternate;
   }

   BlockBehaviour.Properties makeProperties(Map<String, Object> properties) {
      validateCopyDefinition(properties);
      validateBlockProperties(properties);
      BlockBehaviour.Properties props = this.copyProperties(properties);
      Object strength = aliasedProperty(properties, "hardness", "strength");
      Object resistance = aliasedProperty(properties, "resistance", "explosionResistance");
      if (strength instanceof Number s && resistance instanceof Number r) {
         props.strength(s.floatValue(), r.floatValue());
      } else if (strength instanceof Number strengthValue) {
         props.strength(strengthValue.floatValue());
      }

      if (Boolean.TRUE.equals(properties.get("requiresCorrectTool"))) {
         props.requiresCorrectToolForDrops();
      }

      Object collision = properties.get("collision");
      if (Boolean.FALSE.equals(collision)) {
         props.noCollission();
      } else if (Boolean.TRUE.equals(properties.get("noCollision"))) {
         props.noCollission();
      }

      if (Boolean.TRUE.equals(properties.get("noOcclusion"))) {
         props.noOcclusion();
      }

      if (Boolean.TRUE.equals(properties.get("randomTicks"))) {
         props.randomTicks();
      }

      if (Boolean.TRUE.equals(properties.get("ignitedByLava"))) props.ignitedByLava();
      if (Boolean.TRUE.equals(properties.get("replaceable"))) props.replaceable();

      Object pushReaction = properties.get("pushReaction");
      if (pushReaction instanceof String name) props.pushReaction(pushReaction(name));

      Object instrument = properties.get("instrument");
      if (instrument instanceof String name) props.instrument(instrument(name));

      Object light = properties.get("lightLevel");
      if (light instanceof Number lightValue) {
         int level = lightValue.intValue();
         props.lightLevel((state) -> {
            return level;
         });
      }

      Object friction = properties.get("friction");
      if (friction instanceof Number frictionValue) {
         props.friction(frictionValue.floatValue());
      }

      Object speedFactor = properties.get("speedFactor");
      if (speedFactor instanceof Number speedFactorValue) {
         props.speedFactor(speedFactorValue.floatValue());
      }

      Object jumpFactor = properties.get("jumpFactor");
      if (jumpFactor instanceof Number jumpFactorValue) {
         props.jumpFactor(jumpFactorValue.floatValue());
      }

      Object sound = properties.get("sound");
      if (sound instanceof String name) {
         props.sound(this.sound(name));
      }

      Object mapColor = properties.get("mapColor");
      if (mapColor instanceof String name) {
         props.mapColor(this.mapColor(name));
      }

      return props;
   }

   private BlockBehaviour.Properties copyProperties(Map<String, Object> properties) {
      Object copy = properties.get("copy");
      if (copy instanceof String id) {
         final ResourceLocation location;
         try {
            location = MLuaResourceLocations.parse(id);
         } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("[MLua] Invalid block copy identifier '" + id + "'", error);
         }
         Block block = (Block)BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
         if (block == null) {
            throw new IllegalArgumentException("[MLua] Unknown block in copy property: " + id);
         }
         return (Properties)MCApiRuntime.copyBlockProperties(block);
      }

      return (Properties)MCApiRuntime.copyBlockProperties(Blocks.STONE);
   }

   private static SoundType sound(String name) {
      String normalized = normalizePropertyName(name);
      SoundType value = SOUNDS.get(normalized);
      if (value == null) value = SOUNDS.get(normalized.replace("_", ""));
      if (value != null) return value;
      throw new IllegalArgumentException("[MLua] Unknown block sound: " + name
         + ". Use Registry.Blocks:GetSounds() to list values available in this Minecraft version.");
   }

   private static Map<String, SoundType> sounds() {
      Map<String, SoundType> values = new TreeMap<>(publicValues(SoundType.class, SoundType.class));
      alias(values, "slime", "slime_block");
      alias(values, "honey", "honey_block");
      alias(values, "bone", "bone_block");
      alias(values, "dripstone", "dripstone_block");
      alias(values, "netherite", "netherite_block");
      return Collections.unmodifiableMap(values);
   }

   private static Map<String, MapColor> mapColors() {
      Map<String, MapColor> values = new TreeMap<>(publicValues(MapColor.class, MapColor.class));
      for (Map.Entry<String, MapColor> entry : new java.util.ArrayList<>(values.entrySet())) {
         String name = entry.getKey();
         if (name.startsWith("color_")) values.putIfAbsent(name.substring("color_".length()), entry.getValue());
         if (name.startsWith("terracotta_")) values.putIfAbsent(name.substring("terracotta_".length()) + "_terracotta", entry.getValue());
         if (name.endsWith("_nylium")) values.putIfAbsent(name.substring(0, name.length() - "_nylium".length()), entry.getValue());
      }
      MapColor lime = values.get("color_light_green");
      if (lime != null) values.putIfAbsent("lime", lime);
      MapColor limeTerracotta = values.get("terracotta_light_green");
      if (limeTerracotta != null) values.putIfAbsent("lime_terracotta", limeTerracotta);
      return Collections.unmodifiableMap(values);
   }

   private static <T> Map<String, T> publicValues(Class<?> owner, Class<T> valueType) {
      Map<String, T> values = new TreeMap<>();
      for (Field field : owner.getFields()) {
         if (!Modifier.isStatic(field.getModifiers()) || !valueType.isAssignableFrom(field.getType())) continue;
         try {
            Object value = field.get(null);
            if (value != null) values.put(normalizePropertyName(field.getName()), valueType.cast(value));
         } catch (IllegalAccessException ignored) {
            // Public Java API fields should be accessible; inaccessible fields are omitted from this version's catalog.
         }
      }
      return Collections.unmodifiableMap(values);
   }

   private static String normalizePropertyName(String name) {
      return name.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
   }


   private static PushReaction pushReaction(String name) {
      return enumValue(name, PushReaction.class, "pushReaction");
   }

   private static NoteBlockInstrument instrument(String name) {
      return enumValue(name, NoteBlockInstrument.class, "instrument");
   }

   private static <T extends Enum<T>> T enumValue(String name, Class<T> enumType, String property) {
      try {
         return Enum.valueOf(enumType, name.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException invalid) {
         throw new IllegalArgumentException("[MLua] Invalid block " + property + " '" + name
            + "'. Use Registry.Blocks:Get" + (property.equals("instrument") ? "Instruments" : "PushReactions") + "() to list valid values.", invalid);
      }
   }

   private static <T extends Enum<T>> void validateEnumProperty(Map<String, Object> properties, String property,
                                                                  Class<T> enumType) {
      Object value = properties.get(property);
      if (value != null && (!(value instanceof String name) || name.isBlank())) {
         throw new IllegalArgumentException("[MLua] Block property '" + property + "' must be a non-empty string");
      }
      if (value instanceof String name) enumValue(name, enumType, property);
   }

   private static MapColor mapColor(String name) {
      String normalized = normalizePropertyName(name);
      MapColor value = MAP_COLORS.get(normalized);
      if (value == null) value = MAP_COLORS.get(normalized.replace("_", ""));
      if (value != null) return value;
      throw new IllegalArgumentException("[MLua] Unknown block mapColor: " + name
         + ". Use Registry.Blocks:GetMapColors() to list values available in this Minecraft version.");
   }

   private static <T> void alias(Map<String, T> values, String alias, String canonical) {
      T value = values.get(canonical);
      if (value != null) values.putIfAbsent(alias, value);
   }


}
