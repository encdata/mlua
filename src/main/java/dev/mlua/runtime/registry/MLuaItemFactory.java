package dev.mlua.runtime.registry;

import java.util.List;
import java.util.Map;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaItemFactory {
   private static final MLuaRegisteredTypeCatalog<TypeProvider> REGISTERED_TYPES = new MLuaRegisteredTypeCatalog<>();
   private static final MLuaItemProperties ITEM_PROPERTIES = new MLuaItemProperties();

   /** Registers a Java item type that Lua definitions can select with {@code kind = "typeName"}. */
   public static void registerType(String name, String description, TypeProvider provider) {
      registerType("mlua", name, description, provider);
   }

   /** Registers an addon-owned Java item type so it can be removed when that addon unloads. */
   public static void registerType(String owner, String name, String description, TypeProvider provider) {
      registerTypeHandle(owner, name, description, provider);
   }

   /** Registers a type and returns an identity-checked handle for deterministic addon cleanup. */
   public static dev.mlua.api.MLuaApi.Registration registerTypeHandle(
         String owner, String name, String description, TypeProvider provider) {
      String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Item");
      if (java.util.Set.of("item", "generic", "bow", "sword", "pickaxe", "pick", "axe", "shovel", "spade", "hoe").contains(key)) {
         throw new IllegalArgumentException("[MLua] Item type '" + key + "' is reserved by MLua");
      }
      if (provider == null) {
         throw new IllegalArgumentException("[MLua] Item type provider is required");
      }
      REGISTERED_TYPES.register(owner, key, description, provider, "Item");
      return () -> REGISTERED_TYPES.remove(owner, key, "Item", provider);
   }

   /** Removes an item type only when it is still owned by the supplied addon. */
   public static boolean unregisterType(String owner, String name) {
      String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Item");
      if (java.util.Set.of("item", "generic", "bow", "sword", "pickaxe", "pick", "axe", "shovel", "spade", "hoe").contains(key)) {
         return false;
      }
      return REGISTERED_TYPES.remove(owner, key, "Item");
   }

   /** Removes all item types registered by one addon. */
   public static int unregisterTypes(String owner) {
      return REGISTERED_TYPES.removeOwner(owner);
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
      return MLuaItemProperties.tierNames();
   }

   public static List<String> rarityNames() {
      return MLuaItemProperties.rarityNames();
   }

   /** Validate a Lua definition while its Register call can still report a useful error. */
   public void validateTypeDefinition(Map<String, Object> properties) {
      TypeProvider extension = registeredType(properties);
      if (extension != null) {
         validateItemProperties(properties);
         extension.validateDefinition(MLuaDefinitionSnapshot.freeze(properties));
         return;
      }
      Object kind = properties.get("kind");
      if (kind != null && !(kind instanceof String)) {
         throw new IllegalArgumentException("[MLua] Item kind must be a string");
      }
      this.definition(properties);
      validateItemProperties(properties);
   }

   private static TypeProvider registeredType(Map<String, Object> properties) {
      Object kind = properties.get("kind");
      if (!(kind instanceof String name)) return null;
      return REGISTERED_TYPES.get(name, "Item");
   }

   /** Validate the Java Item.Properties portion shared by items and block items. */
   public void validateItemProperties(Map<String, Object> properties) {
      ITEM_PROPERTIES.validateItemProperties(properties);
   }

   public Item createNamed(Map<String, Object> properties, String registryName) {
      MLuaItemProperties.validateCopyDefinition(properties);
      TypeProvider extension = registeredType(properties);
      if (extension != null) {
         validateItemProperties(properties);
         Map<String, Object> definition = MLuaDefinitionSnapshot.validateAndFreeze(
            properties, extension::validateDefinition);
         Item item = extension.createNamed(definition, this.makeRegisteredProperties(properties, registryName), registryName);
         if (item == null) throw new IllegalStateException("[MLua] Item type provider returned null for '" + registryName + "'");
         return item;
      }
      MLuaItemProperties.ItemDefinition definition = this.definition(properties);
      return switch (definition.kind()) {
         case ITEM -> new MLuaNamedItem(this.makeRegisteredProperties(properties, registryName), registryName);
          case BOW -> new MLuaRegisteredBow(this.makeRegisteredBowProperties(properties, registryName), registryName);
          //--version 1.21.1
          case SWORD -> new SwordItem(definition.tier(), this.makeRegisteredProperties(properties, registryName)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case PICKAXE -> new PickaxeItem(definition.tier(), this.makeRegisteredProperties(properties, registryName)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case AXE -> new AxeItem(definition.tier(), this.makeRegisteredProperties(properties, registryName)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case SHOVEL -> new ShovelItem(definition.tier(), this.makeRegisteredProperties(properties, registryName)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case HOE -> new HoeItem(definition.tier(), this.makeRegisteredProperties(properties, registryName)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
               super.appendHoverText(stack, context, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          //--endversion
          //--version 1.20.1
          case SWORD -> new SwordItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeRegisteredProperties(properties, registryName)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case PICKAXE -> new PickaxeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeRegisteredProperties(properties, registryName)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case AXE -> new AxeItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(), this.makeRegisteredProperties(properties, registryName)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case SHOVEL -> new ShovelItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(), this.makeRegisteredProperties(properties, registryName)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
               MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
               super.appendHoverText(stack, level, tooltip, flag);
            }

            public void initializeClient(java.util.function.Consumer consumer) {
               MLuaItemHooks.initializeClient(consumer);
            }
          };
          case HOE -> new HoeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeRegisteredProperties(properties, registryName)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(registryName);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
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
      MLuaItemProperties.validateCopyDefinition(properties);
      TypeProvider extension = registeredType(properties);
      if (extension != null) {
         validateItemProperties(properties);
         Map<String, Object> definition = MLuaDefinitionSnapshot.validateAndFreeze(
            properties, extension::validateDefinition);
         Item item = extension.createScripted(definition,
            this.makeRegisteredProperties(properties, behaviorKey), behaviorKey);
         if (item == null) throw new IllegalStateException("[MLua] Item type provider returned null for scripted item '" + behaviorKey + "'");
         return item;
      }
      MLuaItemProperties.ItemDefinition definition = this.definition(properties);
      return switch (definition.kind()) {
         case ITEM -> new dev.mlua.minecraft.scripted.MLuaScriptedItem(this.makeRegisteredProperties(properties, behaviorKey), behaviorKey);
         case BOW -> new MLuaRegisteredBow(this.makeRegisteredBowProperties(properties, behaviorKey), behaviorKey);
         //--version 1.21.1
         case SWORD -> new SwordItem(definition.tier(), this.makeRegisteredProperties(properties, behaviorKey)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
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
          case PICKAXE -> new PickaxeItem(definition.tier(), this.makeRegisteredProperties(properties, behaviorKey)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
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
          case AXE -> new AxeItem(definition.tier(), this.makeRegisteredProperties(properties, behaviorKey)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
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
          case SHOVEL -> new ShovelItem(definition.tier(), this.makeRegisteredProperties(properties, behaviorKey)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
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
          case HOE -> new HoeItem(definition.tier(), this.makeRegisteredProperties(properties, behaviorKey)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
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
          case SWORD -> new SwordItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeRegisteredProperties(properties, behaviorKey)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
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
          case PICKAXE -> new PickaxeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeRegisteredProperties(properties, behaviorKey)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
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
          case AXE -> new AxeItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(), this.makeRegisteredProperties(properties, behaviorKey)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
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
          case SHOVEL -> new ShovelItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(), this.makeRegisteredProperties(properties, behaviorKey)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
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
          case HOE -> new HoeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(), this.makeRegisteredProperties(properties, behaviorKey)) {
            public Component getName(ItemStack stack) {
               return MLuaItemHooks.itemName(behaviorKey);
            }

            public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
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
      return MLuaItemBehaviorDispatcher.onAttackEntityCallback(behaviorKey, stack, target, attacker);
   }

   public static boolean invokeOnAttackEntity(String behaviorKey, ItemStack stack, LivingEntity target, LivingEntity attacker) {
      return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker);
   }

   public Item.Properties makeProperties(Map<String, Object> properties) {
      return ITEM_PROPERTIES.makeProperties(properties);
   }

   public Item.Properties makeRegisteredProperties(Map<String, Object> properties, String registryName) {
      return ITEM_PROPERTIES.makeRegisteredProperties(properties, registryName);
   }

   private Item.Properties makeRegisteredBowProperties(Map<String, Object> properties, String registryName) {
      return ITEM_PROPERTIES.makeRegisteredBowProperties(properties, registryName);
   }

   private MLuaItemProperties.ItemDefinition definition(Map<String, Object> properties) {
      return ITEM_PROPERTIES.definition(properties);
   }

   private static void invokeOnConsume(String behaviorKey, ItemStack stack, Level level, LivingEntity livingEntity) {
      MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
   }

   static InteractionResultHolder<ItemStack> invokeOnUse(String behaviorKey, Level level, Player player, InteractionHand hand) {
      return MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
   }

   static InteractionResult invokeOnUseOnBlock(String behaviorKey, UseOnContext context) {
      return MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
   }

   static void invokeOnInventoryTick(String behaviorKey, ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
      MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
   }

   static void invokeOnCrafted(String behaviorKey, ItemStack stack, Level level, Player player) {
      MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
   }

}
