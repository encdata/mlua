package dev.mlua.runtime.registry;

import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import dev.mlua.minecraft.scripted.MLuaScriptedBlock;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.mcapi.MCApiRuntime;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.TreeMap;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public final class MLuaBlockFactory {
   private static final MLuaRegisteredTypeCatalog<TypeProvider> REGISTERED_TYPES = new MLuaRegisteredTypeCatalog<>();
   private static final Map<String, SoundType> SOUNDS = sounds();
   private static final Map<String, MapColor> MAP_COLORS = mapColors();

   /** Registers a Java block implementation selected by {@code type = "typeName"} in Lua. */
   public static void registerType(String name, String description, TypeProvider provider) {
      registerType("mlua", name, description, provider);
   }

   /** Registers an addon-owned Java block type so it can be removed when that addon unloads. */
   public static void registerType(String owner, String name, String description, TypeProvider provider) {
      String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Block");
      if ("default".equals(key)) throw new IllegalArgumentException("[MLua] Block type 'default' is reserved by MLua");
      if (provider == null) throw new IllegalArgumentException("[MLua] Block type provider is required");
      REGISTERED_TYPES.register(owner, key, description, provider, "Block");
   }

   /** Removes a block type only when it is still owned by the supplied addon. */
   public static boolean unregisterType(String owner, String name) {
      String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Block");
      if ("default".equals(key)) return false;
      return REGISTERED_TYPES.remove(owner, key, "Block");
   }

   /** Removes all block types registered by one addon. */
   public static int unregisterTypes(String owner) {
      return REGISTERED_TYPES.removeOwner(owner);
   }

   public static Map<String, String> registeredTypes() {
      Map<String, String> descriptions = new java.util.TreeMap<>();
      descriptions.put("default", "A standard block with configurable Minecraft properties and scripted callbacks.");
      return REGISTERED_TYPES.descriptions(descriptions);
   }

   /** Values accepted by the {@code sound} block property on this Minecraft version. */
   public static java.util.List<String> soundNames() {
      return SOUNDS.keySet().stream().sorted().toList();
   }

   /** Values accepted by the {@code mapColor} block property on this Minecraft version. */
   public static java.util.List<String> mapColorNames() {
      return MAP_COLORS.keySet().stream().sorted().toList();
   }

   /** Validate the selected implementation during Lua registration. */
   public static void validateTypeDefinition(Map<String, Object> properties) {
      validateType(properties, registeredType(properties));
      Object rawType = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
      if (rawType != null && !(rawType instanceof String)) {
         throw new IllegalArgumentException("[MLua] Block type must be a string");
      }
      validateCopyDefinition(properties);
      validateBlockProperties(properties);
   }

   public Block create(Map<String, Object> properties, String registryName, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      TypeProvider extension = registeredType(properties);
      validateType(properties, extension);
      validateCopyDefinition(properties);
      if (extension != null) {
         return extension.createNamed(Map.copyOf(properties), this.makeProperties(properties), registryName, blockEntityType);
      }
      return new MLuaNamedBlock(this.makeProperties(properties), registryName, blockEntityType);
   }

   public Block createScripted(Map<String, Object> properties, String behaviorKey, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      TypeProvider extension = registeredType(properties);
      validateType(properties, extension);
      validateCopyDefinition(properties);
      if (extension != null) {
         return extension.createScripted(Map.copyOf(properties), this.makeProperties(properties), behaviorKey, blockEntityType);
      }
      return MLuaScriptedBlock.create(this.makeProperties(properties), behaviorKey, blockEntityType);
   }

   private static TypeProvider registeredType(Map<String, Object> properties) {
      Object type = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
      if (!(type instanceof String name)) return null;
      String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Block");
      return "default".equals(key) ? null : REGISTERED_TYPES.get(key, "Block");
   }

   private static void validateType(Map<String, Object> properties, TypeProvider registered) {
      Object rawType = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
      if (rawType instanceof String name && !name.isBlank() && !"default".equals(MLuaRegisteredTypeCatalog.normalizeName(name, "Block")) && registered == null) {
         throw new IllegalArgumentException("[MLua] Unknown block type: " + name);
      }
   }

   private static void validateCopyDefinition(Map<String, Object> properties) {
      Object copy = properties.get("copy");
      if (copy != null && (!(copy instanceof String id) || id.isBlank())) {
         throw new IllegalArgumentException("[MLua] Block copy must be a non-empty block identifier");
      }
      if (copy instanceof String id && MLuaResourceLocations.tryParseOrNull(id) == null) {
         throw new IllegalArgumentException("[MLua] Invalid block copy identifier '" + id + "'");
      }
   }

   private static void validateBlockProperties(Map<String, Object> properties) {
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

   public interface TypeProvider {
      Block createNamed(Map<String, Object> definition, BlockBehaviour.Properties properties, String registryName,
                        Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType);
      Block createScripted(Map<String, Object> definition, BlockBehaviour.Properties properties, String behaviorKey,
                           Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType);
   }

   public BlockBehaviour.Properties makeProperties(Map<String, Object> properties) {
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
         final net.minecraft.resources.ResourceLocation location;
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
