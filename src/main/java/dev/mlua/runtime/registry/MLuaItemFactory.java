package dev.mlua.runtime.registry;

import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.TreeMap;
//--version 1.21.1
import net.minecraft.core.component.DataComponents;
//--endversion
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class MLuaItemFactory {
   private static final MLuaRegisteredTypeCatalog<TypeProvider> REGISTERED_TYPES = new MLuaRegisteredTypeCatalog<>();
   private static final Map<String, Tier> TIERS = itemTiers();

   /** Registers a Java item type that Lua definitions can select with {@code kind = "typeName"}. */
   public static void registerType(String name, String description, TypeProvider provider) {
      String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Item");
      if (java.util.Set.of("item", "generic", "bow", "sword", "pickaxe", "pick", "axe", "shovel", "spade", "hoe").contains(key)) {
         throw new IllegalArgumentException("[MLua] Item type '" + key + "' is reserved by MLua");
      }
      if (provider == null) {
         throw new IllegalArgumentException("[MLua] Item type provider is required");
      }
      REGISTERED_TYPES.register(name, description, provider, "Item");
   }

   public static Map<String, String> registeredTypes() {
      Map<String, String> descriptions = new java.util.TreeMap<>();
      descriptions.put("item", "A standard registered item.");
      descriptions.put("generic", "Alias for item.");
      descriptions.put("bow", "A native animated bow with Minecraft draw and ammunition behavior.");
      descriptions.put("sword", "A tiered melee weapon.");
      descriptions.put("pickaxe", "A tiered mining tool.");
      descriptions.put("pick", "Alias for pickaxe.");
      descriptions.put("axe", "A tiered axe tool and weapon.");
      descriptions.put("shovel", "A tiered shovel tool.");
      descriptions.put("spade", "Alias for shovel.");
      descriptions.put("hoe", "A tiered hoe tool.");
      return REGISTERED_TYPES.descriptions(descriptions);
   }

   public static List<String> tierNames() {
      return TIERS.keySet().stream().sorted().toList();
   }

   public static List<String> rarityNames() {
      return java.util.Arrays.stream(Rarity.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).sorted().toList();
   }

   /** Validate a Lua definition while its Register call can still report a useful error. */
   public void validateTypeDefinition(Map<String, Object> properties) {
      TypeProvider extension = registeredType(properties);
      if (extension != null) {
         validateItemProperties(properties);
         return;
      }
      Object kind = properties.get("kind");
      if (kind != null && !(kind instanceof String)) {
         throw new IllegalArgumentException("[MLua] Item kind must be a string");
      }
      this.definition(properties);
      validateItemProperties(properties);
   }

   /** Validate the Java Item.Properties portion shared by items and block items. */
   public void validateItemProperties(Map<String, Object> properties) {
      validateCopyDefinition(properties);
      validateFoodProperty(properties);
      validateStackProperties(properties);
      validateCommonProperties(properties);
   }

   public Item createNamed(Map<String, Object> properties, String registryName) {
      validateCopyDefinition(properties);
      TypeProvider extension = registeredType(properties);
      if (extension != null) return extension.createNamed(Map.copyOf(properties), registryName);
      ItemDefinition definition = this.definition(properties);
      return switch (definition.kind()) {
         case ITEM -> new MLuaNamedItem(this.makeProperties(properties), registryName);
          case BOW -> new MLuaRegisteredBow(this.makeBowProperties(properties), registryName);
          //--version 1.21.1
          case SWORD -> new SwordItem(definition.tier(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case PICKAXE -> new PickaxeItem(definition.tier(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case AXE -> new AxeItem(definition.tier(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case SHOVEL -> new ShovelItem(definition.tier(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case HOE -> new HoeItem(definition.tier(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          //--endversion
          //--version 1.20.1
          case SWORD -> new SwordItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case PICKAXE -> new PickaxeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case AXE -> new AxeItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case SHOVEL -> new ShovelItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case HOE -> new HoeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          //--endversion
      };
   }

   public Item createScripted(Map<String, Object> properties, String behaviorKey) {
      validateCopyDefinition(properties);
      TypeProvider extension = registeredType(properties);
      if (extension != null) return extension.createScripted(Map.copyOf(properties), behaviorKey);
      ItemDefinition definition = this.definition(properties);
      return switch (definition.kind()) {
         case ITEM -> new dev.mlua.minecraft.scripted.MLuaScriptedItem(this.makeProperties(properties), behaviorKey);
         case BOW -> new MLuaRegisteredBow(this.makeBowProperties(properties), behaviorKey);
         //--version 1.21.1
         case SWORD -> new SwordItem(definition.tier(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }

            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
               invokeOnConsume(behaviorKey, stack, level, livingEntity);
               return super.finishUsingItem(stack, level, livingEntity);
            }

            public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
               InteractionResultHolder<ItemStack> handled = invokeOnUse(behaviorKey, level, player, hand);
               return handled != null ? handled : super.use(level, player, hand);
            }

            public InteractionResult useOn(UseOnContext context) {
               InteractionResult handled = invokeOnUseOnBlock(behaviorKey, context);
               return handled != null ? handled : super.useOn(context);
            }

            public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
               invokeOnInventoryTick(behaviorKey, stack, level, entity, slot, selected);
               super.inventoryTick(stack, level, entity, slot, selected);
            }

            public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
               return MLuaItemFactory.invokeOnAttackEntity(behaviorKey, stack, target, attacker)
                  && super.hurtEnemy(stack, target, attacker);
            }

            public void onCraftedBy(ItemStack stack, Level level, Player player) {
               invokeOnCrafted(behaviorKey, stack, level, player);
               super.onCraftedBy(stack, level, player);
            }
          };
          case PICKAXE -> new PickaxeItem(definition.tier(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }

            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
               invokeOnConsume(behaviorKey, stack, level, livingEntity);
               return super.finishUsingItem(stack, level, livingEntity);
            }

            public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
               InteractionResultHolder<ItemStack> handled = invokeOnUse(behaviorKey, level, player, hand);
               return handled != null ? handled : super.use(level, player, hand);
            }

            public InteractionResult useOn(UseOnContext context) {
               InteractionResult handled = invokeOnUseOnBlock(behaviorKey, context);
               return handled != null ? handled : super.useOn(context);
            }

            public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
               invokeOnInventoryTick(behaviorKey, stack, level, entity, slot, selected);
               super.inventoryTick(stack, level, entity, slot, selected);
            }

            public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
               return MLuaItemFactory.invokeOnAttackEntity(behaviorKey, stack, target, attacker)
                  && super.hurtEnemy(stack, target, attacker);
            }

            public void onCraftedBy(ItemStack stack, Level level, Player player) {
               invokeOnCrafted(behaviorKey, stack, level, player);
               super.onCraftedBy(stack, level, player);
            }
          };
          case AXE -> new AxeItem(definition.tier(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }

            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
               invokeOnConsume(behaviorKey, stack, level, livingEntity);
               return super.finishUsingItem(stack, level, livingEntity);
            }

            public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
               InteractionResultHolder<ItemStack> handled = invokeOnUse(behaviorKey, level, player, hand);
               return handled != null ? handled : super.use(level, player, hand);
            }

            public InteractionResult useOn(UseOnContext context) {
               InteractionResult handled = invokeOnUseOnBlock(behaviorKey, context);
               return handled != null ? handled : super.useOn(context);
            }

            public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
               invokeOnInventoryTick(behaviorKey, stack, level, entity, slot, selected);
               super.inventoryTick(stack, level, entity, slot, selected);
            }

            public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
               return MLuaItemFactory.invokeOnAttackEntity(behaviorKey, stack, target, attacker)
                  && super.hurtEnemy(stack, target, attacker);
            }

            public void onCraftedBy(ItemStack stack, Level level, Player player) {
               invokeOnCrafted(behaviorKey, stack, level, player);
               super.onCraftedBy(stack, level, player);
            }
          };
          case SHOVEL -> new ShovelItem(definition.tier(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }

            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
               invokeOnConsume(behaviorKey, stack, level, livingEntity);
               return super.finishUsingItem(stack, level, livingEntity);
            }

            public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
               InteractionResultHolder<ItemStack> handled = invokeOnUse(behaviorKey, level, player, hand);
               return handled != null ? handled : super.use(level, player, hand);
            }

            public InteractionResult useOn(UseOnContext context) {
               InteractionResult handled = invokeOnUseOnBlock(behaviorKey, context);
               return handled != null ? handled : super.useOn(context);
            }

            public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
               invokeOnInventoryTick(behaviorKey, stack, level, entity, slot, selected);
               super.inventoryTick(stack, level, entity, slot, selected);
            }

            public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
               return MLuaItemFactory.invokeOnAttackEntity(behaviorKey, stack, target, attacker)
                  && super.hurtEnemy(stack, target, attacker);
            }

            public void onCraftedBy(ItemStack stack, Level level, Player player) {
               invokeOnCrafted(behaviorKey, stack, level, player);
               super.onCraftedBy(stack, level, player);
            }
          };
          case HOE -> new HoeItem(definition.tier(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }

            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
               invokeOnConsume(behaviorKey, stack, level, livingEntity);
               return super.finishUsingItem(stack, level, livingEntity);
            }

            public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
               InteractionResultHolder<ItemStack> handled = invokeOnUse(behaviorKey, level, player, hand);
               return handled != null ? handled : super.use(level, player, hand);
            }

            public InteractionResult useOn(UseOnContext context) {
               InteractionResult handled = invokeOnUseOnBlock(behaviorKey, context);
               return handled != null ? handled : super.useOn(context);
            }

            public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
               invokeOnInventoryTick(behaviorKey, stack, level, entity, slot, selected);
               super.inventoryTick(stack, level, entity, slot, selected);
            }

            public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
               return MLuaItemFactory.invokeOnAttackEntity(behaviorKey, stack, target, attacker)
                  && super.hurtEnemy(stack, target, attacker);
            }

            public void onCraftedBy(ItemStack stack, Level level, Player player) {
               invokeOnCrafted(behaviorKey, stack, level, player);
               super.onCraftedBy(stack, level, player);
            }
          };
          //--endversion
          //--version 1.20.1
          case SWORD -> new SwordItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }

            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
               invokeOnConsume(behaviorKey, stack, level, livingEntity);
               return super.finishUsingItem(stack, level, livingEntity);
            }

            public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
               InteractionResultHolder<ItemStack> handled = invokeOnUse(behaviorKey, level, player, hand);
               return handled != null ? handled : super.use(level, player, hand);
            }

            public InteractionResult useOn(UseOnContext context) {
               InteractionResult handled = invokeOnUseOnBlock(behaviorKey, context);
               return handled != null ? handled : super.useOn(context);
            }

            public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
               invokeOnInventoryTick(behaviorKey, stack, level, entity, slot, selected);
               super.inventoryTick(stack, level, entity, slot, selected);
            }

            public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
               return MLuaItemFactory.invokeOnAttackEntity(behaviorKey, stack, target, attacker)
                  && super.hurtEnemy(stack, target, attacker);
            }

            public void onCraftedBy(ItemStack stack, Level level, Player player) {
               invokeOnCrafted(behaviorKey, stack, level, player);
               super.onCraftedBy(stack, level, player);
            }
          };
          case PICKAXE -> new PickaxeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }

            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
               invokeOnConsume(behaviorKey, stack, level, livingEntity);
               return super.finishUsingItem(stack, level, livingEntity);
            }

            public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
               InteractionResultHolder<ItemStack> handled = invokeOnUse(behaviorKey, level, player, hand);
               return handled != null ? handled : super.use(level, player, hand);
            }

            public InteractionResult useOn(UseOnContext context) {
               InteractionResult handled = invokeOnUseOnBlock(behaviorKey, context);
               return handled != null ? handled : super.useOn(context);
            }

            public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
               invokeOnInventoryTick(behaviorKey, stack, level, entity, slot, selected);
               super.inventoryTick(stack, level, entity, slot, selected);
            }

            public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
               return MLuaItemFactory.invokeOnAttackEntity(behaviorKey, stack, target, attacker)
                  && super.hurtEnemy(stack, target, attacker);
            }

            public void onCraftedBy(ItemStack stack, Level level, Player player) {
               invokeOnCrafted(behaviorKey, stack, level, player);
               super.onCraftedBy(stack, level, player);
            }
          };
          case AXE -> new AxeItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }

            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
               invokeOnConsume(behaviorKey, stack, level, livingEntity);
               return super.finishUsingItem(stack, level, livingEntity);
            }

            public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
               InteractionResultHolder<ItemStack> handled = invokeOnUse(behaviorKey, level, player, hand);
               return handled != null ? handled : super.use(level, player, hand);
            }

            public InteractionResult useOn(UseOnContext context) {
               InteractionResult handled = invokeOnUseOnBlock(behaviorKey, context);
               return handled != null ? handled : super.useOn(context);
            }

            public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
               invokeOnInventoryTick(behaviorKey, stack, level, entity, slot, selected);
               super.inventoryTick(stack, level, entity, slot, selected);
            }

            public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
               return MLuaItemFactory.invokeOnAttackEntity(behaviorKey, stack, target, attacker)
                  && super.hurtEnemy(stack, target, attacker);
            }

            public void onCraftedBy(ItemStack stack, Level level, Player player) {
               invokeOnCrafted(behaviorKey, stack, level, player);
               super.onCraftedBy(stack, level, player);
            }
          };
          case SHOVEL -> new ShovelItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }

            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
               invokeOnConsume(behaviorKey, stack, level, livingEntity);
               return super.finishUsingItem(stack, level, livingEntity);
            }

            public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
               InteractionResultHolder<ItemStack> handled = invokeOnUse(behaviorKey, level, player, hand);
               return handled != null ? handled : super.use(level, player, hand);
            }

            public InteractionResult useOn(UseOnContext context) {
               InteractionResult handled = invokeOnUseOnBlock(behaviorKey, context);
               return handled != null ? handled : super.useOn(context);
            }

            public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
               invokeOnInventoryTick(behaviorKey, stack, level, entity, slot, selected);
               super.inventoryTick(stack, level, entity, slot, selected);
            }

            public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
               return MLuaItemFactory.invokeOnAttackEntity(behaviorKey, stack, target, attacker)
                  && super.hurtEnemy(stack, target, attacker);
            }

            public void onCraftedBy(ItemStack stack, Level level, Player player) {
               invokeOnCrafted(behaviorKey, stack, level, player);
               super.onCraftedBy(stack, level, player);
            }
          };
          case HOE -> new HoeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeProperties(properties)) {
            public net.minecraft.network.chat.Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }

            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
               invokeOnConsume(behaviorKey, stack, level, livingEntity);
               return super.finishUsingItem(stack, level, livingEntity);
            }

            public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
               InteractionResultHolder<ItemStack> handled = invokeOnUse(behaviorKey, level, player, hand);
               return handled != null ? handled : super.use(level, player, hand);
            }

            public InteractionResult useOn(UseOnContext context) {
               InteractionResult handled = invokeOnUseOnBlock(behaviorKey, context);
               return handled != null ? handled : super.useOn(context);
            }

            public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
               invokeOnInventoryTick(behaviorKey, stack, level, entity, slot, selected);
               super.inventoryTick(stack, level, entity, slot, selected);
            }

            public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
               return MLuaItemFactory.invokeOnAttackEntity(behaviorKey, stack, target, attacker)
                  && super.hurtEnemy(stack, target, attacker);
            }

            public void onCraftedBy(ItemStack stack, Level level, Player player) {
               invokeOnCrafted(behaviorKey, stack, level, player);
               super.onCraftedBy(stack, level, player);
            }
          };
          //--endversion
       };
    }

   public static LuaValue invokeOnAttackEntityCallback(String behaviorKey, ItemStack stack,
         LivingEntity target, LivingEntity attacker) {
      LuaValue callback = MLuaBehaviorRegistry.getCallback("item", behaviorKey, "onAttackEntity");
      if (callback.isnil()) return LuaValue.NIL;
      try {
         var registration = MLuaRuntime.registrationForLevel(target.level());
         LuaTable targetTable = target instanceof ServerPlayer player
            ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
            : MLuaEntityWrapper.entityTable(target, registration);
         LuaTable attackerTable = attacker instanceof ServerPlayer player
            ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
            : MLuaEntityWrapper.entityTable(attacker, registration);
         LuaTable context = new LuaTable();
         context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
         context.set("Entity", targetTable);
         context.set("Target", targetTable);
         context.set("Attacker", attackerTable);
         if (attacker instanceof ServerPlayer) context.set("Player", attackerTable);
         context.set("ItemStack", behaviorKey);
         Varargs result = callback.invoke(context);
         return result.arg1();
      } catch (Exception exception) {
         MLuaLogger.LOGGER.error("MLua item onAttackEntity callback failed for '{}': {}", behaviorKey, exception.getMessage());
         return LuaValue.NIL;
      }
   }

   public static boolean invokeOnAttackEntity(String behaviorKey, ItemStack stack, LivingEntity target, LivingEntity attacker) {
      LuaValue result = invokeOnAttackEntityCallback(behaviorKey, stack, target, attacker);
      return !result.isboolean() || result.toboolean();
   }

   public Item.Properties makeProperties(Map<String, Object> properties) {
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
         //--version 1.21.1
         Item.Properties item = new Item.Properties();
         int maxDamage = source.components().getOrDefault(DataComponents.MAX_DAMAGE, 0);
         if (maxDamage > 0) {
            item.durability(maxDamage);
         } else {
            item.stacksTo(source.getDefaultMaxStackSize());
         }

         item.rarity(source.getDefaultInstance().getRarity());
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

   private static void validateCopyDefinition(Map<String, Object> properties) {
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

   private ItemDefinition definition(Map<String, Object> properties) {
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

   private static TypeProvider registeredType(Map<String, Object> properties) {
      Object kind = properties.get("kind");
      if (!(kind instanceof String name)) return null;
      return REGISTERED_TYPES.get(name, "Item");
   }

   public interface TypeProvider {
      Item createNamed(Map<String, Object> properties, String registryName);
      Item createScripted(Map<String, Object> properties, String behaviorKey);
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

   private static void invokeOnConsume(String behaviorKey, ItemStack stack, Level level, LivingEntity livingEntity) {
      if (level.isClientSide) return;
      LuaValue onConsume = MLuaBehaviorRegistry.getCallback("item", behaviorKey, "onConsume");
      if (!onConsume.isnil()) {
         try {
            LuaTable ctx = new LuaTable();
            if (livingEntity instanceof ServerPlayer sp) {
                ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
            }

            ctx.set("World", level.dimension().location().toString());
            ctx.set("Item", MLuaItemWrapper.itemTable(stack, MLuaRuntime.registrationForLevel(level)));
            onConsume.invoke(ctx);
         } catch (Exception var6) {
            MLuaLogger.LOGGER.error("MLua item onConsume callback failed for '{}': {}", behaviorKey, var6.getMessage());
         }
      }
   }

   static InteractionResultHolder<ItemStack> invokeOnUse(String behaviorKey, Level level, Player player, InteractionHand hand) {
      LuaValue onUse = MLuaBehaviorRegistry.getCallback("item", behaviorKey, "onUse");
      if (onUse.isnil()) {
         return null;
      }

      try {
         LuaTable ctx = new LuaTable();
         if (player instanceof ServerPlayer sp) {
            ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
            }

            ctx.set("World", player.level().dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable(player.position().x, player.position().y, player.position().z));
            ctx.set("Hand", hand.name().toLowerCase());
            ctx.set("Item", MLuaItemWrapper.itemTable(player.getItemInHand(hand), MLuaRuntime.registrationForLevel(level)));
            ctx.set("ItemStack", behaviorKey);
            Varargs result = onUse.invoke(ctx);
         if (result.arg1().isboolean() && result.arg1().toboolean()) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
         }
      } catch (Exception var7) {
         MLuaLogger.LOGGER.error("MLua item onUse callback failed for '{}'", behaviorKey, var7);
      }

      return null;
   }

   static InteractionResult invokeOnUseOnBlock(String behaviorKey, UseOnContext context) {
      LuaValue onUseOnBlock = MLuaBehaviorRegistry.getCallback("item", behaviorKey, "onUseOnBlock");
      if (onUseOnBlock.isnil()) {
         return null;
      }

      try {
         LuaTable ctx = new LuaTable();
         Player player = context.getPlayer();
          if (player instanceof ServerPlayer sp) {
             ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(context.getLevel())));
          }

          var pos = context.getClickedPos();
         ctx.set("World", context.getLevel().dimension().location().toString());
         ctx.set("Position", MLuaPlayerWrapper.positionTable((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5));
         ctx.set("Block", MLuaBlockWrapper.blockTable(context.getLevel(), pos, MLuaRuntime.registrationForLevel(context.getLevel())));
         ctx.set("Face", context.getClickedFace().getName());
         ctx.set("Hand", context.getHand().name().toLowerCase());
         ctx.set("Item", MLuaItemWrapper.itemTable(context.getItemInHand(), MLuaRuntime.registrationForLevel(context.getLevel())));
         ctx.set("ItemStack", behaviorKey);
         Varargs result = onUseOnBlock.invoke(ctx);
         if (result.arg1().isboolean() && result.arg1().toboolean()) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
         }
      } catch (Exception var6) {
         MLuaLogger.LOGGER.error("MLua item onUseOnBlock callback failed for '{}': {}", behaviorKey, var6.getMessage());
      }

      return null;
   }

   static void invokeOnInventoryTick(String behaviorKey, ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
      LuaValue onInventoryTick = MLuaBehaviorRegistry.getCallback("item", behaviorKey, "onInventoryTick");
      if (!onInventoryTick.isnil()) {
         try {
            LuaTable ctx = new LuaTable();
             if (entity instanceof ServerPlayer sp) {
                ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
             }

             ctx.set("World", level.dimension().location().toString());
             if (entity instanceof LivingEntity living) {
               ctx.set("Entity", MLuaEntityWrapper.entityTable(living, MLuaRuntime.registrationForLevel(level)));
            }
            ctx.set("Slot", slot);
            ctx.set("Selected", LuaValue.valueOf(selected));
            ctx.set("Item", MLuaItemWrapper.itemTable(stack, MLuaRuntime.registrationForLevel(level)));
            ctx.set("ItemStack", behaviorKey);
            onInventoryTick.invoke(ctx);
         } catch (Exception var8) {
            MLuaLogger.LOGGER.error("MLua item onInventoryTick callback failed for '{}': {}", behaviorKey, var8.getMessage());
         }
      }
   }

   static void invokeOnCrafted(String behaviorKey, ItemStack stack, Level level, Player player) {
      LuaValue onCrafted = MLuaBehaviorRegistry.getCallback("item", behaviorKey, "onCrafted");
      if (!onCrafted.isnil()) {
         try {
            LuaTable ctx = new LuaTable();
             if (player instanceof ServerPlayer sp) {
                ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
             }

             ctx.set("World", player.level().dimension().location().toString());
             ctx.set("Position", MLuaPlayerWrapper.positionTable(player.position().x, player.position().y, player.position().z));
             ctx.set("Hand", player.getUsedItemHand() == null ? "main_hand" : player.getUsedItemHand().name().toLowerCase());
             ctx.set("ItemStack", behaviorKey);
            ctx.set("Item", MLuaItemWrapper.itemTable(stack, MLuaRuntime.registrationForLevel(level)));
            onCrafted.invoke(ctx);
         } catch (Exception var6) {
            MLuaLogger.LOGGER.error("MLua item onCrafted callback failed for '{}': {}", behaviorKey, var6.getMessage());
         }
      }
   }

   private static record ItemDefinition(ItemKind kind, Tier tier, float attackDamage, float attackSpeed) {
   }

   private static enum ItemKind {
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
