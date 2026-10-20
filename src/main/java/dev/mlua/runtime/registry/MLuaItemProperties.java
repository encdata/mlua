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
//--version 26.1
import net.minecraft.core.component.DataComponentInitializers;
//--endversion
//--version 1.21.1
import net.minecraft.core.component.DataComponents;
//--endversion
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import org.luaj.vm2.LuaError;

/** Owns built-in item definition validation and Minecraft Item.Properties construction. */
final class MLuaItemProperties {
   private static final Map<String, Tier> TIERS = itemTiers();
   private static final List<String> TIER_NAMES = List.copyOf(TIERS.keySet());
   private static final List<String> RARITY_NAMES = java.util.Arrays.stream(Rarity.values())
      .map(value -> value.name().toLowerCase(Locale.ROOT)).sorted().toList();

   static List<String> tierNames() {
      return TIER_NAMES;
   }

   static List<String> rarityNames() {
      return RARITY_NAMES;
   }

   void validateItemProperties(Map<String, Object> properties) {
      validateCopyDefinition(properties);
      validateFoodProperty(properties);
      validateStackProperties(properties);
      validateCommonProperties(properties);
   }

   Item.Properties makeProperties(Map<String, Object> properties) {
      validateItemProperties(properties);
      Item.Properties item;
      item = this.copyProperties(properties);
      Object durability = properties.get("durability");
      Object maxStack = properties.get("maxStackSize");
      if (durability instanceof Number number) {
         if (maxStack instanceof Number stack && stack.intValue() > 1) {
            throw new IllegalArgumentException("[MLua] Item property 'durability' cannot be combined with maxStackSize > 1");
         }
         item.durability(number.intValue());
      } else if (maxStack instanceof Number number) {
         item.stacksTo(number.intValue());
      }

      if (Boolean.TRUE.equals(properties.get("fireResistant"))) {
         item.fireResistant();
      }

      Object rarity = properties.get("rarity");
      if (rarity instanceof String name) {
         item.rarity(rarity(name));
      }

      Object craftRemainder = properties.get("craftRemainder");
      if (craftRemainder instanceof String id) {
         Item remainder = (Item) MCApiRuntime.lookupRegistry("item", id);
         if (remainder == null) {
            throw new IllegalArgumentException("[MLua] Unknown craftRemainder item: " + id);
         }

         item.craftRemainder(remainder);
      }

      Object food = properties.get("food");
      if (food instanceof Map<?, ?> foodProps) {
         item.food(this.food(foodProps));
      }

      return item;
   }

   Item.Properties makeRegisteredProperties(Map<String, Object> properties, String registryName) {
      return this.withRegistryId(this.makeProperties(properties), registryName);
   }

   Item.Properties makeRegisteredBowProperties(Map<String, Object> properties, String registryName) {
      return this.withRegistryId(this.makeBowProperties(properties), registryName);
   }

   private Item.Properties withRegistryId(Item.Properties properties, String registryName) {
      //--version 26.1
      properties.setId(ResourceKey.create(Registries.ITEM, MLuaResourceLocations.parse(registryName)));
      //--endversion
      return properties;
   }

   private Item.Properties makeBowProperties(Map<String, Object> properties) {
      if (properties.containsKey("durability") || properties.containsKey("copy")) {
         return this.makeProperties(properties);
      }
      Map<String, Object> bowProperties = new java.util.HashMap<>(properties);
      bowProperties.put("durability", 384);
      return this.makeProperties(bowProperties);
   }

   private Item.Properties copyProperties(Map<String, Object> properties) {
      Object copy = properties.get("copy");
      if (copy instanceof String id && !id.isBlank()) {
         final String normalizedId;
         try {
            normalizedId = MLuaResourceLocations.parse(id).toString();
         } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("[MLua] Invalid item copy identifier '" + id + "'", error);
         }
         Item source = (Item) MCApiRuntime.lookupRegistry("item", normalizedId);
         if (source == null) {
            throw new IllegalArgumentException("[MLua] Unknown item in copy property: " + id);
         }
         //--version 26.1
         Item.Properties item = new Item.Properties();
         this.copyComponentInitializers(item, ResourceKey.create(
            Registries.ITEM, MLuaResourceLocations.parse(normalizedId)));
         return item;
         //--endversion
         //--version 1.21.1
         Item.Properties item = new Item.Properties();
         int maxDamage = source.components().getOrDefault(DataComponents.MAX_DAMAGE, 0);
         if (maxDamage > 0) {
            item.durability(maxDamage);
         } else {
            item.stacksTo(source.getDefaultMaxStackSize());
         }

         item.rarity(source.getDefaultInstance().getRarity());
         if (source.components().has(DataComponents.FIRE_RESISTANT)) {
            item.fireResistant();
         }
         FoodProperties food = source.components().get(DataComponents.FOOD);
         if (food != null) {
            item.food(food);
         }

         if (source.hasCraftingRemainingItem()) {
            item.craftRemainder(source.getCraftingRemainingItem());
         }

         return item;
         //--endversion
         //--version 1.20.1
         Item.Properties item = new Item.Properties();
         int maxDamage = source.getMaxDamage();
         if (maxDamage > 0) {
            item.durability(maxDamage);
         } else {
            item.stacksTo(source.getMaxStackSize());
         }

         if (source.isFireResistant()) {
            item.fireResistant();
         }

         item.rarity(source.getRarity(ItemStack.EMPTY));
         FoodProperties food = source.getFoodProperties();
         if (food != null) {
            item.food(food);
         }

         if (source.hasCraftingRemainingItem()) {
            item.craftRemainder(source.getCraftingRemainingItem());
         }

         return item;
         //--endversion
      }

      return new Item.Properties();
   }

   //--version 26.1
   @SuppressWarnings("unchecked")
   private void copyComponentInitializers(Item.Properties target, ResourceKey<Item> sourceKey) {
      try {
         Field initializersField = DataComponentInitializers.class.getDeclaredField("initializers");
         initializersField.setAccessible(true);
         List<?> entries = (List<?>) initializersField.get(BuiltInRegistries.DATA_COMPONENT_INITIALIZERS);

         DataComponentInitializers.Initializer<Item> copied = null;
         for (Object entry : entries) {
            Class<?> entryType = entry.getClass();
            java.lang.reflect.Method keyAccessor = entryType.getDeclaredMethod("key");
            java.lang.reflect.Method initializerAccessor = entryType.getDeclaredMethod("initializer");
            keyAccessor.setAccessible(true);
            initializerAccessor.setAccessible(true);
            if (!sourceKey.equals(keyAccessor.invoke(entry))) continue;

            DataComponentInitializers.Initializer<Item> initializer =
               (DataComponentInitializers.Initializer<Item>) initializerAccessor.invoke(entry);
            copied = copied == null ? initializer : copied.andThen(initializer);
         }
         if (copied == null) {
            throw new IllegalArgumentException("[MLua] No item component defaults are registered for copy source " + sourceKey.location());
         }
         DataComponentInitializers.Initializer<Item> copiedInitializer = copied;

         Field targetInitializerField = Item.Properties.class.getDeclaredField("componentInitializer");
         targetInitializerField.setAccessible(true);
         DataComponentInitializers.Initializer<Item> targetInitializer =
            (DataComponentInitializers.Initializer<Item>) targetInitializerField.get(target);
         DataComponentInitializers.Initializer<Item> combined = (components, registries, targetKey) -> {
            if (targetInitializer != null) targetInitializer.run(components, registries, targetKey);
            copiedInitializer.run(components, registries, sourceKey);
         };
         targetInitializerField.set(target, combined);
      } catch (ReflectiveOperationException | SecurityException error) {
         throw new IllegalStateException("[MLua] Could not copy deferred item component defaults from " + sourceKey.location(), error);
      }
   }
   //--endversion

   static void validateCopyDefinition(Map<String, Object> properties) {
      Object copy = properties.get("copy");
      if (copy != null && (!(copy instanceof String id) || id.isBlank())) {
         throw new IllegalArgumentException("[MLua] Item copy must be a non-empty item identifier");
      }
      if (copy instanceof String id && MLuaResourceLocations.tryParseOrNull(id) == null) {
         throw new IllegalArgumentException("[MLua] Invalid item copy identifier '" + id + "'");
      }
   }

   private static void validateCommonProperties(Map<String, Object> properties) {
      Object fireResistant = properties.get("fireResistant");
      if (fireResistant != null && !(fireResistant instanceof Boolean)) {
         throw new IllegalArgumentException("[MLua] Item property 'fireResistant' must be a boolean");
      }
      Object rawRarity = properties.get("rarity");
      if (rawRarity != null) {
         if (!(rawRarity instanceof String name) || name.isBlank()) {
            throw new IllegalArgumentException("[MLua] Item property 'rarity' must be a non-empty string");
         }
         rarity(name);
      }
      Object rawRemainder = properties.get("craftRemainder");
      if (rawRemainder != null && (!(rawRemainder instanceof String id) || id.isBlank())) {
         throw new IllegalArgumentException("[MLua] Item property 'craftRemainder' must be a non-empty item identifier");
      }
      if (rawRemainder instanceof String id && MLuaResourceLocations.tryParseOrNull(id) == null) {
         throw new IllegalArgumentException("[MLua] Invalid craftRemainder item identifier '" + id + "'");
      }
   }

   ItemDefinition definition(Map<String, Object> properties) {
      String kindName = properties.get("kind") instanceof String kind ? kind : "item";
      ItemKind kind = ItemKind.from(kindName);
      boolean tieredTool = switch (kind) {
         case SWORD, PICKAXE, AXE, SHOVEL, HOE -> true;
         case ITEM, BOW -> false;
      };
      if (!tieredTool && properties.get("tier") != null) {
         throw new IllegalArgumentException("[MLua] Item property 'tier' is only valid for tiered tool kinds");
      }
      if (!tieredTool && (properties.get("attackDamage") != null || properties.get("attackSpeed") != null)) {
         throw new IllegalArgumentException("[MLua] Item attackDamage and attackSpeed are only valid for tiered tool kinds");
      }
      Tier tier = tieredTool ? this.tier(properties) : Tiers.WOOD;
      float attackDamage = finiteFloat(properties, "attackDamage", defaultAttackDamage(kind));
      float attackSpeed = finiteFloat(properties, "attackSpeed", defaultAttackSpeed(kind));
      return new ItemDefinition(kind, tier, attackDamage, attackSpeed);
   }

   private static float finiteFloat(Map<String, Object> properties, String name, float defaultValue) {
      Object rawValue = properties.get(name);
      if (rawValue == null) return defaultValue;
      if (!(rawValue instanceof Number number)) {
         throw new IllegalArgumentException("[MLua] Item property '" + name + "' must be numeric");
      }
      double value = number.doubleValue();
      if (!Double.isFinite(value) || value < -Float.MAX_VALUE || value > Float.MAX_VALUE) {
         throw new IllegalArgumentException("[MLua] Item property '" + name + "' must be a finite float");
      }
      return (float)value;
   }

   private Tier tier(Map<String, Object> properties) {
      Object rawTier = properties.get("tier");
      if (rawTier == null) {
         return Tiers.WOOD;
      }
      if (!(rawTier instanceof String tierName) || tierName.isBlank()) {
         throw new IllegalArgumentException("[MLua] Item property 'tier' must be a non-empty string");
      }

      String normalized = tierName.trim().toLowerCase(Locale.ROOT);
      Tier value = TIERS.get(normalized);
      if (value == null) value = TIERS.get(normalized.replace("_", ""));
      if (value == null) throw new IllegalArgumentException("[MLua] Unknown item tier: " + tierName
         + ". Use Registry.Items:GetTiers() to list values available in this Minecraft version.");
      return value;
   }

   private static Rarity rarity(String name) {
      try {
         return Rarity.valueOf(name.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException invalid) {
         throw new IllegalArgumentException("[MLua] Unknown item rarity: " + name
            + ". Use Registry.Items:GetRarities() to list values available in this Minecraft version.", invalid);
      }
   }

   private static Map<String, Tier> itemTiers() {
      Map<String, Tier> tiers = new TreeMap<>();
      for (Field field : Tiers.class.getFields()) {
         if (!Modifier.isStatic(field.getModifiers()) || !Tier.class.isAssignableFrom(field.getType())) continue;
         try {
            Object value = field.get(null);
            if (value instanceof Tier tier) tiers.put(field.getName().toLowerCase(Locale.ROOT), tier);
         } catch (IllegalAccessException ignored) {
            // Public game API fields should be accessible; inaccessible fields are omitted from the runtime choices.
         }
      }
      aliasTier(tiers, "wooden", "wood");
      aliasTier(tiers, "golden", "gold");
      return Collections.unmodifiableMap(tiers);
   }

   private static void aliasTier(Map<String, Tier> tiers, String alias, String canonical) {
      Tier value = tiers.get(canonical);
      if (value != null) tiers.putIfAbsent(alias, value);
   }

   private FoodProperties food(Map<?, ?> properties) {
      validateFoodDefinition(properties);
      FoodProperties.Builder builder = new FoodProperties.Builder();
      Object nutrition = properties.get("nutrition");
      builder.nutrition(((Number)nutrition).intValue());
      Object saturation = properties.get("saturation");
      boolean alwaysEat = Boolean.TRUE.equals(properties.get("alwaysEat"))
         || Boolean.TRUE.equals(properties.get("alwaysEdible"));
      //--version 1.20.1
      builder.saturationMod(saturation instanceof Number s ? s.floatValue() : 0.0F);
      if (Boolean.TRUE.equals(properties.get("meat"))) {
         builder.meat();
      }
      //--endversion
      //--version 1.21.1
      builder.saturationModifier(saturation instanceof Number s ? s.floatValue() : 0.0F);
      //--endversion

      //--version 1.21.1
      if (alwaysEat) {
         builder.alwaysEdible();
      }
      //--endversion
      //--version 1.20.1
      if (alwaysEat) {
         builder.alwaysEat();
      }
      //--endversion

      if (Boolean.TRUE.equals(properties.get("fast"))) {
         builder.fast();
      }

      return builder.build();
   }

   private static void validateFoodProperty(Map<?, ?> itemProperties) {
      Object rawFood = itemProperties.get("food");
      if (rawFood == null) return;
      if (!(rawFood instanceof Map<?, ?> food)) {
         throw new IllegalArgumentException("[MLua] Item property 'food' must be a table");
      }
      validateFoodDefinition(food);
   }

   private static void validateFoodDefinition(Map<?, ?> food) {
      Object rawNutrition = food.get("nutrition");
      if (!(rawNutrition instanceof Number nutrition)) {
         throw new IllegalArgumentException("[MLua] food.nutrition is required and must be a whole number");
      }
      double nutritionValue = nutrition.doubleValue();
      if (!Double.isFinite(nutritionValue) || nutritionValue != Math.rint(nutritionValue)
         || nutritionValue < 0 || nutritionValue > Integer.MAX_VALUE) {
         throw new IllegalArgumentException("[MLua] food.nutrition must be a whole number from 0 to " + Integer.MAX_VALUE);
      }

      Object rawSaturation = food.get("saturation");
      if (rawSaturation != null) {
         if (!(rawSaturation instanceof Number saturation)) {
            throw new IllegalArgumentException("[MLua] food.saturation must be a non-negative number");
         }
         double saturationValue = saturation.doubleValue();
         if (!Double.isFinite(saturationValue) || saturationValue < 0 || saturationValue > Float.MAX_VALUE) {
            throw new IllegalArgumentException("[MLua] food.saturation must be finite and within the non-negative float range");
         }
      }

      for (String property : List.of("meat", "alwaysEat", "alwaysEdible", "fast")) {
         Object value = food.get(property);
         if (value != null && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("[MLua] food." + property + " must be a boolean");
         }
      }
   }

   private static void validateStackProperties(Map<String, Object> properties) {
      Object rawDurability = properties.get("durability");
      Object rawStack = properties.get("maxStackSize");
      int maxStack = rawStack == null ? -1 : wholePositiveInteger(rawStack, "maxStackSize", 64);
      if (rawDurability != null) {
         wholePositiveInteger(rawDurability, "durability", Integer.MAX_VALUE);
         if (maxStack > 1) {
            throw new IllegalArgumentException("[MLua] Item property 'durability' cannot be combined with maxStackSize > 1");
         }
      }
   }

   private static int wholePositiveInteger(Object value, String property, int maximum) {
      if (!(value instanceof Number number)) {
         throw new IllegalArgumentException("[MLua] Item property '" + property + "' must be a whole number from 1 to " + maximum);
      }
      double numericValue = number.doubleValue();
      if (!Double.isFinite(numericValue) || numericValue != Math.rint(numericValue)
         || numericValue < 1 || numericValue > maximum) {
         throw new IllegalArgumentException("[MLua] Item property '" + property + "' must be a whole number from 1 to " + maximum);
      }
      return (int)numericValue;
   }

   private static float defaultAttackDamage(ItemKind kind) {
      return switch (kind) {
         case ITEM -> 0.0F;
         case BOW -> 0.0F;
         case SWORD -> 3.0F;
         case PICKAXE -> 1.0F;
         case AXE -> 6.0F;
         case SHOVEL -> 1.5F;
         case HOE -> -2.0F;
      };
   }

   private static float defaultAttackSpeed(ItemKind kind) {
      return switch (kind) {
         case ITEM -> 0.0F;
         case BOW -> 0.0F;
         case SWORD -> -2.4F;
         case PICKAXE -> -2.8F;
         case AXE -> -3.1F;
         case SHOVEL -> -3.0F;
         case HOE -> -1.0F;
      };
   }


   static record ItemDefinition(ItemKind kind, Tier tier, float attackDamage, float attackSpeed) {
   }

   enum ItemKind {
      ITEM,
      BOW,
      SWORD,
      PICKAXE,
      AXE,
      SHOVEL,
      HOE;

      public static ItemKind from(String value) {
         return switch (value.toLowerCase(Locale.ROOT)) {
            case "item", "generic" -> ITEM;
            case "bow" -> BOW;
            case "sword" -> SWORD;
            case "pickaxe", "pick" -> PICKAXE;
            case "axe" -> AXE;
            case "shovel", "spade" -> SHOVEL;
            case "hoe" -> HOE;
            default -> throw new IllegalArgumentException("[MLua] Unknown item kind: " + value);
         };
      }
   }
}
