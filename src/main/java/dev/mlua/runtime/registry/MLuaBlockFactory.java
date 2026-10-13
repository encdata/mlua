package dev.mlua.runtime.registry;

import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import dev.mlua.minecraft.scripted.MLuaScriptedBlock;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.mcapi.MCApiRuntime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;

public final class MLuaBlockFactory {
   private static final ConcurrentMap<String, RegisteredType> REGISTERED_TYPES = new ConcurrentHashMap<>();

   /** Registers a Java block implementation selected by {@code type = "typeName"} in Lua. */
   public static void registerType(String name, String description, TypeProvider provider) {
      String key = normalizeTypeName(name);
      if ("default".equals(key)) throw new IllegalArgumentException("[MLua] Block type 'default' is reserved by MLua");
      if (description == null || description.isBlank()) {
         throw new IllegalArgumentException("[MLua] Block type description must be non-empty");
      }
      if (provider == null) throw new IllegalArgumentException("[MLua] Block type provider is required");
      RegisteredType previous = REGISTERED_TYPES.putIfAbsent(key, new RegisteredType(description.trim(), provider));
      if (previous != null) throw new IllegalStateException("[MLua] Block type '" + key + "' is already registered");
   }

   public static Map<String, String> registeredTypes() {
      Map<String, String> descriptions = new java.util.TreeMap<>();
      descriptions.put("default", "A standard block with configurable Minecraft properties and scripted callbacks.");
      REGISTERED_TYPES.forEach((name, type) -> descriptions.put(name, type.description()));
      return Map.copyOf(descriptions);
   }

   /** Validate the selected implementation during Lua registration. */
   public static void validateTypeDefinition(Map<String, Object> properties) {
      validateType(properties, registeredType(properties));
      Object rawType = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
      if (rawType != null && !(rawType instanceof String)) {
         throw new IllegalArgumentException("[MLua] Block type must be a string");
      }
   }

   public Block create(Map<String, Object> properties, String registryName, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      RegisteredType extension = registeredType(properties);
      validateType(properties, extension);
      if (extension != null) {
         return extension.provider().createNamed(Map.copyOf(properties), this.makeProperties(properties), registryName, blockEntityType);
      }
      return new MLuaNamedBlock(this.makeProperties(properties), registryName, blockEntityType);
   }

   public Block createScripted(Map<String, Object> properties, String behaviorKey, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      RegisteredType extension = registeredType(properties);
      validateType(properties, extension);
      if (extension != null) {
         return extension.provider().createScripted(Map.copyOf(properties), this.makeProperties(properties), behaviorKey, blockEntityType);
      }
      return MLuaScriptedBlock.create(this.makeProperties(properties), behaviorKey, blockEntityType);
   }

   private static RegisteredType registeredType(Map<String, Object> properties) {
      Object type = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
      if (!(type instanceof String name)) return null;
      String key = normalizeTypeName(name);
      return "default".equals(key) ? null : REGISTERED_TYPES.get(key);
   }

   private static void validateType(Map<String, Object> properties, RegisteredType registered) {
      Object rawType = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
      if (rawType instanceof String name && !name.isBlank() && !"default".equals(normalizeTypeName(name)) && registered == null) {
         throw new IllegalArgumentException("[MLua] Unknown block type: " + name);
      }
   }

   private static String normalizeTypeName(String name) {
      if (name == null || name.isBlank()) throw new IllegalArgumentException("[MLua] Block type name must be non-empty");
      String normalized = name.trim().toLowerCase(Locale.ROOT);
      if (!normalized.matches("[a-z][a-z0-9_]*")) {
         throw new IllegalArgumentException("[MLua] Invalid block type name: " + name);
      }
      return normalized;
   }

   private record RegisteredType(String description, TypeProvider provider) { }

   public interface TypeProvider {
      Block createNamed(Map<String, Object> definition, BlockBehaviour.Properties properties, String registryName,
                        Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType);
      Block createScripted(Map<String, Object> definition, BlockBehaviour.Properties properties, String behaviorKey,
                           Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType);
   }

   public BlockBehaviour.Properties makeProperties(Map<String, Object> properties) {
      BlockBehaviour.Properties props = this.copyProperties(properties);
      Object strength = properties.get("strength");
      Object resistance = properties.get("explosionResistance");
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

      Object light = properties.get("lightLevel");
      if (light instanceof Number lightValue) {
         int level = Math.max(0, Math.min(15, lightValue.intValue()));
         props.lightLevel((state) -> {
            return level;
         });
      }

      Object friction = properties.get("friction");
      if (friction instanceof Number frictionValue) {
         props.friction(Math.max(0.0F, frictionValue.floatValue()));
      }

      Object speedFactor = properties.get("speedFactor");
      if (speedFactor instanceof Number speedFactorValue) {
         props.speedFactor(Math.max(0.0F, speedFactorValue.floatValue()));
      }

      Object jumpFactor = properties.get("jumpFactor");
      if (jumpFactor instanceof Number jumpFactorValue) {
         props.jumpFactor(Math.max(0.0F, jumpFactorValue.floatValue()));
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
         Block block = (Block)BuiltInRegistries.BLOCK.getOptional(MLuaResourceLocations.parse(id)).orElse(null);
         if (block != null) {
            return (Properties)MCApiRuntime.copyBlockProperties(block);
         }
      }

      return (Properties)MCApiRuntime.copyBlockProperties(Blocks.STONE);
   }

   private SoundType sound(String name) {
      SoundType var10000;
      switch (name.toLowerCase(Locale.ROOT)) {
         case "wood":
            var10000 = SoundType.WOOD;
            break;
         case "gravel":
            var10000 = SoundType.GRAVEL;
            break;
         case "grass":
            var10000 = SoundType.GRASS;
            break;
         case "lily_pad":
         case "lilypad":
            var10000 = SoundType.LILY_PAD;
            break;
         case "stone":
            var10000 = SoundType.STONE;
            break;
         case "metal":
            var10000 = SoundType.METAL;
            break;
         case "glass":
            var10000 = SoundType.GLASS;
            break;
         case "wool":
            var10000 = SoundType.WOOL;
            break;
         case "sand":
            var10000 = SoundType.SAND;
            break;
         case "snow":
            var10000 = SoundType.SNOW;
            break;
         case "powder_snow":
         case "powdersnow":
            var10000 = SoundType.POWDER_SNOW;
            break;
         case "ladder":
            var10000 = SoundType.LADDER;
            break;
         case "anvil":
            var10000 = SoundType.ANVIL;
            break;
         case "slime":
            var10000 = SoundType.SLIME_BLOCK;
            break;
         case "honey":
            var10000 = SoundType.HONEY_BLOCK;
            break;
         case "netherite":
            var10000 = SoundType.NETHERITE_BLOCK;
            break;
         case "bone":
            var10000 = SoundType.BONE_BLOCK;
            break;
         case "bamboo":
            var10000 = SoundType.BAMBOO;
            break;
         case "bamboo_sapling":
         case "bamboosapling":
            var10000 = SoundType.BAMBOO_SAPLING;
            break;
         case "scaffolding":
            var10000 = SoundType.SCAFFOLDING;
            break;
         case "sweet_berry_bush":
         case "sweetberrybush":
            var10000 = SoundType.SWEET_BERRY_BUSH;
            break;
         case "crop":
            var10000 = SoundType.CROP;
            break;
         case "hard_crop":
         case "hardcrop":
            var10000 = SoundType.HARD_CROP;
            break;
         case "vine":
            var10000 = SoundType.VINE;
            break;
         case "nether_wart":
         case "netherwart":
            var10000 = SoundType.NETHER_WART;
            break;
         case "lantern":
            var10000 = SoundType.LANTERN;
            break;
         case "stem":
            var10000 = SoundType.STEM;
            break;
         case "nylium":
            var10000 = SoundType.NYLIUM;
            break;
         case "fungus":
            var10000 = SoundType.FUNGUS;
            break;
         case "roots":
            var10000 = SoundType.ROOTS;
            break;
         case "shroomlight":
            var10000 = SoundType.SHROOMLIGHT;
            break;
         case "weeping_vines":
         case "weepingvines":
            var10000 = SoundType.WEEPING_VINES;
            break;
         case "twisting_vines":
         case "twistingvines":
            var10000 = SoundType.TWISTING_VINES;
            break;
         case "soul_sand":
         case "soulsand":
            var10000 = SoundType.SOUL_SAND;
            break;
         case "soul_soil":
         case "soulsoil":
            var10000 = SoundType.SOUL_SOIL;
            break;
         case "basalt":
            var10000 = SoundType.BASALT;
            break;
         case "wart_block":
         case "wartblock":
            var10000 = SoundType.WART_BLOCK;
            break;
         case "netherrack":
            var10000 = SoundType.NETHERRACK;
            break;
         case "nether_bricks":
         case "netherbricks":
            var10000 = SoundType.NETHER_BRICKS;
            break;
         case "nether_sprouts":
         case "nethersprouts":
            var10000 = SoundType.NETHER_SPROUTS;
            break;
         case "nether_ore":
         case "netherore":
            var10000 = SoundType.NETHER_ORE;
            break;
         case "candle":
            var10000 = SoundType.CANDLE;
            break;
         case "amethyst":
            var10000 = SoundType.AMETHYST;
            break;
         case "amethyst_cluster":
         case "amethystcluster":
            var10000 = SoundType.AMETHYST_CLUSTER;
            break;
         case "small_amethyst_bud":
         case "smallamethystbud":
            var10000 = SoundType.SMALL_AMETHYST_BUD;
            break;
         case "medium_amethyst_bud":
         case "mediumamethystbud":
            var10000 = SoundType.MEDIUM_AMETHYST_BUD;
            break;
         case "large_amethyst_bud":
         case "largeamethystbud":
            var10000 = SoundType.LARGE_AMETHYST_BUD;
            break;
         case "tuff":
            var10000 = SoundType.TUFF;
            break;
         case "calcite":
            var10000 = SoundType.CALCITE;
            break;
         case "dripstone":
            var10000 = SoundType.DRIPSTONE_BLOCK;
            break;
         case "copper":
            var10000 = SoundType.COPPER;
            break;
         case "cave_vines":
         case "cavevines":
            var10000 = SoundType.CAVE_VINES;
            break;
         case "spore_blossom":
         case "sporeblossom":
            var10000 = SoundType.SPORE_BLOSSOM;
            break;
         case "azalea":
            var10000 = SoundType.AZALEA;
            break;
         case "flowering_azalea":
         case "floweringazalea":
            var10000 = SoundType.FLOWERING_AZALEA;
            break;
         case "moss_carpet":
         case "mosscarpet":
            var10000 = SoundType.MOSS_CARPET;
            break;
         case "moss":
            var10000 = SoundType.MOSS;
            break;
         case "big_dripleaf":
         case "bigdripleaf":
            var10000 = SoundType.BIG_DRIPLEAF;
            break;
         case "small_dripleaf":
         case "smalldripleaf":
            var10000 = SoundType.SMALL_DRIPLEAF;
            break;
         case "rooted_dirt":
         case "rooteddirt":
            var10000 = SoundType.ROOTED_DIRT;
            break;
         case "hanging_roots":
         case "hangingroots":
            var10000 = SoundType.HANGING_ROOTS;
            break;
         case "azalea_leaves":
         case "azalealeaves":
            var10000 = SoundType.AZALEA_LEAVES;
            break;
         case "sculk_sensor":
         case "sculksensor":
            var10000 = SoundType.SCULK_SENSOR;
            break;
         case "sculk_catalyst":
         case "sculkcatalyst":
            var10000 = SoundType.SCULK_CATALYST;
            break;
         case "sculk":
            var10000 = SoundType.SCULK;
            break;
         case "sculk_vein":
         case "sculkvein":
            var10000 = SoundType.SCULK_VEIN;
            break;
         case "sculk_shrieker":
         case "sculkshrieker":
            var10000 = SoundType.SCULK_SHRIEKER;
            break;
         case "mud":
            var10000 = SoundType.MUD;
            break;
         case "mud_bricks":
         case "mudbricks":
            var10000 = SoundType.MUD_BRICKS;
            break;
         case "packed_mud":
         case "packedmud":
            var10000 = SoundType.PACKED_MUD;
            break;
         case "hanging_sign":
         case "hangingsign":
            var10000 = SoundType.HANGING_SIGN;
            break;
         case "nether_wood":
         case "netherwood":
            var10000 = SoundType.NETHER_WOOD;
            break;
         case "bamboo_wood":
         case "bamboowood":
            var10000 = SoundType.BAMBOO_WOOD;
            break;
         default:
            throw new IllegalArgumentException("[MLua] Unknown block sound: " + name);
      }

      return var10000;
   }

   private MapColor mapColor(String name) {
      MapColor var10000;
      switch (name.toLowerCase(Locale.ROOT)) {
         case "grass":
            var10000 = MapColor.GRASS;
            break;
         case "sand":
            var10000 = MapColor.SAND;
            break;
         case "wool":
            var10000 = MapColor.WOOL;
            break;
         case "fire":
            var10000 = MapColor.FIRE;
            break;
         case "ice":
            var10000 = MapColor.ICE;
            break;
         case "metal":
            var10000 = MapColor.METAL;
            break;
         case "plant":
            var10000 = MapColor.PLANT;
            break;
         case "snow":
            var10000 = MapColor.SNOW;
            break;
         case "clay":
            var10000 = MapColor.CLAY;
            break;
         case "dirt":
            var10000 = MapColor.DIRT;
            break;
         case "stone":
            var10000 = MapColor.STONE;
            break;
         case "water":
            var10000 = MapColor.WATER;
            break;
         case "wood":
            var10000 = MapColor.WOOD;
            break;
         case "quartz":
            var10000 = MapColor.QUARTZ;
            break;
         case "color_orange":
         case "orange":
            var10000 = MapColor.COLOR_ORANGE;
            break;
         case "color_magenta":
         case "magenta":
            var10000 = MapColor.COLOR_MAGENTA;
            break;
         case "color_light_blue":
         case "light_blue":
         case "lightblue":
            var10000 = MapColor.COLOR_LIGHT_BLUE;
            break;
         case "color_yellow":
         case "yellow":
            var10000 = MapColor.COLOR_YELLOW;
            break;
         case "color_light_green":
         case "light_green":
         case "lightgreen":
         case "lime":
            var10000 = MapColor.COLOR_LIGHT_GREEN;
            break;
         case "color_pink":
         case "pink":
            var10000 = MapColor.COLOR_PINK;
            break;
         case "color_gray":
         case "gray":
            var10000 = MapColor.COLOR_GRAY;
            break;
         case "color_light_gray":
         case "light_gray":
         case "lightgray":
            var10000 = MapColor.COLOR_LIGHT_GRAY;
            break;
         case "color_cyan":
         case "cyan":
            var10000 = MapColor.COLOR_CYAN;
            break;
         case "color_purple":
         case "purple":
            var10000 = MapColor.COLOR_PURPLE;
            break;
         case "color_blue":
         case "blue":
            var10000 = MapColor.COLOR_BLUE;
            break;
         case "color_brown":
         case "brown":
            var10000 = MapColor.COLOR_BROWN;
            break;
         case "color_green":
         case "green":
            var10000 = MapColor.COLOR_GREEN;
            break;
         case "color_red":
         case "red":
            var10000 = MapColor.COLOR_RED;
            break;
         case "color_black":
         case "black":
            var10000 = MapColor.COLOR_BLACK;
            break;
         case "gold":
            var10000 = MapColor.GOLD;
            break;
         case "diamond":
            var10000 = MapColor.DIAMOND;
            break;
         case "lapis":
            var10000 = MapColor.LAPIS;
            break;
         case "emerald":
            var10000 = MapColor.EMERALD;
            break;
         case "podzol":
            var10000 = MapColor.PODZOL;
            break;
         case "nether":
            var10000 = MapColor.NETHER;
            break;
         case "terracotta_white":
         case "white_terracotta":
            var10000 = MapColor.TERRACOTTA_WHITE;
            break;
         case "terracotta_orange":
         case "orange_terracotta":
            var10000 = MapColor.TERRACOTTA_ORANGE;
            break;
         case "terracotta_magenta":
         case "magenta_terracotta":
            var10000 = MapColor.TERRACOTTA_MAGENTA;
            break;
         case "terracotta_light_blue":
         case "light_blue_terracotta":
            var10000 = MapColor.TERRACOTTA_LIGHT_BLUE;
            break;
         case "terracotta_yellow":
         case "yellow_terracotta":
            var10000 = MapColor.TERRACOTTA_YELLOW;
            break;
         case "terracotta_light_green":
         case "lime_terracotta":
            var10000 = MapColor.TERRACOTTA_LIGHT_GREEN;
            break;
         case "terracotta_pink":
         case "pink_terracotta":
            var10000 = MapColor.TERRACOTTA_PINK;
            break;
         case "terracotta_gray":
         case "gray_terracotta":
            var10000 = MapColor.TERRACOTTA_GRAY;
            break;
         case "terracotta_light_gray":
         case "light_gray_terracotta":
            var10000 = MapColor.TERRACOTTA_LIGHT_GRAY;
            break;
         case "terracotta_cyan":
         case "cyan_terracotta":
            var10000 = MapColor.TERRACOTTA_CYAN;
            break;
         case "terracotta_purple":
         case "purple_terracotta":
            var10000 = MapColor.TERRACOTTA_PURPLE;
            break;
         case "terracotta_blue":
         case "blue_terracotta":
            var10000 = MapColor.TERRACOTTA_BLUE;
            break;
         case "terracotta_brown":
         case "brown_terracotta":
            var10000 = MapColor.TERRACOTTA_BROWN;
            break;
         case "terracotta_green":
         case "green_terracotta":
            var10000 = MapColor.TERRACOTTA_GREEN;
            break;
         case "terracotta_red":
         case "red_terracotta":
            var10000 = MapColor.TERRACOTTA_RED;
            break;
         case "terracotta_black":
         case "black_terracotta":
            var10000 = MapColor.TERRACOTTA_BLACK;
            break;
         case "crimson_nylium":
         case "crimson":
            var10000 = MapColor.CRIMSON_NYLIUM;
            break;
         case "crimson_stem":
            var10000 = MapColor.CRIMSON_STEM;
            break;
         case "crimson_hyphae":
            var10000 = MapColor.CRIMSON_HYPHAE;
            break;
         case "warped_nylium":
         case "warped":
            var10000 = MapColor.WARPED_NYLIUM;
            break;
         case "warped_stem":
            var10000 = MapColor.WARPED_STEM;
            break;
         case "warped_hyphae":
            var10000 = MapColor.WARPED_HYPHAE;
            break;
         case "warped_wart_block":
            var10000 = MapColor.WARPED_WART_BLOCK;
            break;
         case "deepslate":
            var10000 = MapColor.DEEPSLATE;
            break;
         case "raw_iron":
            var10000 = MapColor.RAW_IRON;
            break;
         case "glow_lichen":
            var10000 = MapColor.GLOW_LICHEN;
            break;
         default:
            throw new IllegalArgumentException("[MLua] Unknown block mapColor: " + name);
      }

      return var10000;
   }
}
