package dev.mlua.runtime.registry;

import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Builds MLua's vanilla-backed item implementations for registered and scripted definitions. */
final class MLuaBuiltinItemConstructors {
   final MLuaItemProperties properties;

   MLuaBuiltinItemConstructors(MLuaItemProperties properties) {
      this.properties = properties;
   }

   Item createNamed(Map<String, Object> properties, String registryName, MLuaItemProperties.ItemDefinition definition) {
      return switch (definition.kind()) {
         case ITEM ->
            new MLuaNamedItem(this.properties.makeRegisteredProperties(properties, registryName), registryName);
         case BOW ->
            new MLuaRegisteredBow(this.properties.makeRegisteredBowProperties(properties, registryName), registryName);
         //--version 1.21.1
         case SWORD ->
            new SwordItem(definition.tier(), this.properties.makeRegisteredProperties(properties, registryName)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(registryName);
               }

               public void appendHoverText(
                   ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
                  super.appendHoverText(stack, context, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }
            };
         case PICKAXE ->
            new PickaxeItem(definition.tier(), this.properties.makeRegisteredProperties(properties, registryName)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(registryName);
               }

               public void appendHoverText(
                   ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
                  super.appendHoverText(stack, context, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }
            };
         case AXE ->
            new AxeItem(definition.tier(), this.properties.makeRegisteredProperties(properties, registryName)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(registryName);
               }

               public void appendHoverText(
                   ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
                  super.appendHoverText(stack, context, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }
            };
         case SHOVEL ->
            new ShovelItem(definition.tier(), this.properties.makeRegisteredProperties(properties, registryName)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(registryName);
               }

               public void appendHoverText(
                   ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
                  super.appendHoverText(stack, context, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }
            };
         case HOE ->
            new HoeItem(definition.tier(), this.properties.makeRegisteredProperties(properties, registryName)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(registryName);
               }

               public void appendHoverText(
                   ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
                  super.appendHoverText(stack, context, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }
            };
         //--endversion
         //--version 1.20.1
         case SWORD ->
            new SwordItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(),
                this.properties.makeRegisteredProperties(properties, registryName)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(registryName);
               }

               public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                   java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
                  super.appendHoverText(stack, level, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }
            };
         case PICKAXE ->
            new PickaxeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(),
                this.properties.makeRegisteredProperties(properties, registryName)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(registryName);
               }

               public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                   java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
                  super.appendHoverText(stack, level, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }
            };
         case AXE ->
            new AxeItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(),
                this.properties.makeRegisteredProperties(properties, registryName)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(registryName);
               }

               public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                   java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
                  super.appendHoverText(stack, level, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }
            };
         case SHOVEL ->
            new ShovelItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(),
                this.properties.makeRegisteredProperties(properties, registryName)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(registryName);
               }

               public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                   java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
                  super.appendHoverText(stack, level, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }
            };
         case HOE ->
            new HoeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(),
                this.properties.makeRegisteredProperties(properties, registryName)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(registryName);
               }

               public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                   java.util.List<Component> tooltip, TooltipFlag flag) {
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

   Item createScripted(
       Map<String, Object> properties, String behaviorKey, MLuaItemProperties.ItemDefinition definition) {
      return switch (definition.kind()) {
         case ITEM ->
            new dev.mlua.minecraft.scripted.MLuaScriptedItem(
                this.properties.makeRegisteredProperties(properties, behaviorKey), behaviorKey);
         case BOW ->
            new MLuaRegisteredBow(this.properties.makeRegisteredBowProperties(properties, behaviorKey), behaviorKey);
         //--version 1.21.1
         case SWORD ->
            new SwordItem(definition.tier(), this.properties.makeRegisteredProperties(properties, behaviorKey)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(behaviorKey);
               }

               public void appendHoverText(
                   ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
                  super.appendHoverText(stack, context, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }

               public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                  MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
                  return super.finishUsingItem(stack, level, livingEntity);
               }

               public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                  InteractionResultHolder<ItemStack> handled =
                      MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
                  return handled != null ? handled : super.use(level, player, hand);
               }

               public InteractionResult useOn(UseOnContext context) {
                  InteractionResult handled = MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
                  return handled != null ? handled : super.useOn(context);
               }

               public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
                  MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
                  super.inventoryTick(stack, level, entity, slot, selected);
               }

               public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                  return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker)
                      && super.hurtEnemy(stack, target, attacker);
               }

               public void onCraftedBy(ItemStack stack, Level level, Player player) {
                  MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
                  super.onCraftedBy(stack, level, player);
               }
            };
         case PICKAXE ->
            new PickaxeItem(definition.tier(), this.properties.makeRegisteredProperties(properties, behaviorKey)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(behaviorKey);
               }

               public void appendHoverText(
                   ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
                  super.appendHoverText(stack, context, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }

               public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                  MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
                  return super.finishUsingItem(stack, level, livingEntity);
               }

               public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                  InteractionResultHolder<ItemStack> handled =
                      MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
                  return handled != null ? handled : super.use(level, player, hand);
               }

               public InteractionResult useOn(UseOnContext context) {
                  InteractionResult handled = MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
                  return handled != null ? handled : super.useOn(context);
               }

               public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
                  MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
                  super.inventoryTick(stack, level, entity, slot, selected);
               }

               public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                  return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker)
                      && super.hurtEnemy(stack, target, attacker);
               }

               public void onCraftedBy(ItemStack stack, Level level, Player player) {
                  MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
                  super.onCraftedBy(stack, level, player);
               }
            };
         case AXE ->
            new AxeItem(definition.tier(), this.properties.makeRegisteredProperties(properties, behaviorKey)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(behaviorKey);
               }

               public void appendHoverText(
                   ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
                  super.appendHoverText(stack, context, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }

               public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                  MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
                  return super.finishUsingItem(stack, level, livingEntity);
               }

               public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                  InteractionResultHolder<ItemStack> handled =
                      MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
                  return handled != null ? handled : super.use(level, player, hand);
               }

               public InteractionResult useOn(UseOnContext context) {
                  InteractionResult handled = MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
                  return handled != null ? handled : super.useOn(context);
               }

               public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
                  MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
                  super.inventoryTick(stack, level, entity, slot, selected);
               }

               public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                  return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker)
                      && super.hurtEnemy(stack, target, attacker);
               }

               public void onCraftedBy(ItemStack stack, Level level, Player player) {
                  MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
                  super.onCraftedBy(stack, level, player);
               }
            };
         case SHOVEL ->
            new ShovelItem(definition.tier(), this.properties.makeRegisteredProperties(properties, behaviorKey)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(behaviorKey);
               }

               public void appendHoverText(
                   ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
                  super.appendHoverText(stack, context, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }

               public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                  MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
                  return super.finishUsingItem(stack, level, livingEntity);
               }

               public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                  InteractionResultHolder<ItemStack> handled =
                      MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
                  return handled != null ? handled : super.use(level, player, hand);
               }

               public InteractionResult useOn(UseOnContext context) {
                  InteractionResult handled = MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
                  return handled != null ? handled : super.useOn(context);
               }

               public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
                  MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
                  super.inventoryTick(stack, level, entity, slot, selected);
               }

               public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                  return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker)
                      && super.hurtEnemy(stack, target, attacker);
               }

               public void onCraftedBy(ItemStack stack, Level level, Player player) {
                  MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
                  super.onCraftedBy(stack, level, player);
               }
            };
         case HOE ->
            new HoeItem(definition.tier(), this.properties.makeRegisteredProperties(properties, behaviorKey)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(behaviorKey);
               }

               public void appendHoverText(
                   ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
                  super.appendHoverText(stack, context, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }

               public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                  MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
                  return super.finishUsingItem(stack, level, livingEntity);
               }

               public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                  InteractionResultHolder<ItemStack> handled =
                      MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
                  return handled != null ? handled : super.use(level, player, hand);
               }

               public InteractionResult useOn(UseOnContext context) {
                  InteractionResult handled = MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
                  return handled != null ? handled : super.useOn(context);
               }

               public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
                  MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
                  super.inventoryTick(stack, level, entity, slot, selected);
               }

               public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                  return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker)
                      && super.hurtEnemy(stack, target, attacker);
               }

               public void onCraftedBy(ItemStack stack, Level level, Player player) {
                  MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
                  super.onCraftedBy(stack, level, player);
               }
            };
         //--endversion
         //--version 1.20.1
         case SWORD ->
            new SwordItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(),
                this.properties.makeRegisteredProperties(properties, behaviorKey)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(behaviorKey);
               }

               public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                   java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
                  super.appendHoverText(stack, level, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }

               public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                  MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
                  return super.finishUsingItem(stack, level, livingEntity);
               }

               public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                  InteractionResultHolder<ItemStack> handled =
                      MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
                  return handled != null ? handled : super.use(level, player, hand);
               }

               public InteractionResult useOn(UseOnContext context) {
                  InteractionResult handled = MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
                  return handled != null ? handled : super.useOn(context);
               }

               public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
                  MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
                  super.inventoryTick(stack, level, entity, slot, selected);
               }

               public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                  return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker)
                      && super.hurtEnemy(stack, target, attacker);
               }

               public void onCraftedBy(ItemStack stack, Level level, Player player) {
                  MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
                  super.onCraftedBy(stack, level, player);
               }
            };
         case PICKAXE ->
            new PickaxeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(),
                this.properties.makeRegisteredProperties(properties, behaviorKey)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(behaviorKey);
               }

               public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                   java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
                  super.appendHoverText(stack, level, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }

               public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                  MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
                  return super.finishUsingItem(stack, level, livingEntity);
               }

               public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                  InteractionResultHolder<ItemStack> handled =
                      MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
                  return handled != null ? handled : super.use(level, player, hand);
               }

               public InteractionResult useOn(UseOnContext context) {
                  InteractionResult handled = MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
                  return handled != null ? handled : super.useOn(context);
               }

               public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
                  MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
                  super.inventoryTick(stack, level, entity, slot, selected);
               }

               public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                  return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker)
                      && super.hurtEnemy(stack, target, attacker);
               }

               public void onCraftedBy(ItemStack stack, Level level, Player player) {
                  MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
                  super.onCraftedBy(stack, level, player);
               }
            };
         case AXE ->
            new AxeItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(),
                this.properties.makeRegisteredProperties(properties, behaviorKey)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(behaviorKey);
               }

               public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                   java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
                  super.appendHoverText(stack, level, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }

               public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                  MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
                  return super.finishUsingItem(stack, level, livingEntity);
               }

               public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                  InteractionResultHolder<ItemStack> handled =
                      MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
                  return handled != null ? handled : super.use(level, player, hand);
               }

               public InteractionResult useOn(UseOnContext context) {
                  InteractionResult handled = MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
                  return handled != null ? handled : super.useOn(context);
               }

               public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
                  MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
                  super.inventoryTick(stack, level, entity, slot, selected);
               }

               public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                  return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker)
                      && super.hurtEnemy(stack, target, attacker);
               }

               public void onCraftedBy(ItemStack stack, Level level, Player player) {
                  MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
                  super.onCraftedBy(stack, level, player);
               }
            };
         case SHOVEL ->
            new ShovelItem(definition.tier(), definition.attackDamage(), definition.attackSpeed(),
                this.properties.makeRegisteredProperties(properties, behaviorKey)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(behaviorKey);
               }

               public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                   java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
                  super.appendHoverText(stack, level, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }

               public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                  MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
                  return super.finishUsingItem(stack, level, livingEntity);
               }

               public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                  InteractionResultHolder<ItemStack> handled =
                      MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
                  return handled != null ? handled : super.use(level, player, hand);
               }

               public InteractionResult useOn(UseOnContext context) {
                  InteractionResult handled = MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
                  return handled != null ? handled : super.useOn(context);
               }

               public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
                  MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
                  super.inventoryTick(stack, level, entity, slot, selected);
               }

               public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                  return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker)
                      && super.hurtEnemy(stack, target, attacker);
               }

               public void onCraftedBy(ItemStack stack, Level level, Player player) {
                  MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
                  super.onCraftedBy(stack, level, player);
               }
            };
         case HOE ->
            new HoeItem(definition.tier(), Math.round(definition.attackDamage()), definition.attackSpeed(),
                this.properties.makeRegisteredProperties(properties, behaviorKey)) {
               public Component getName(ItemStack stack) {
                  return MLuaItemHooks.itemName(behaviorKey);
               }

               public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                   java.util.List<Component> tooltip, TooltipFlag flag) {
                  MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
                  super.appendHoverText(stack, level, tooltip, flag);
               }

               public void initializeClient(java.util.function.Consumer consumer) {
                  MLuaItemHooks.initializeClient(consumer);
               }

               public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                  MLuaItemBehaviorDispatcher.onConsume(behaviorKey, stack, level, livingEntity);
                  return super.finishUsingItem(stack, level, livingEntity);
               }

               public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                  InteractionResultHolder<ItemStack> handled =
                      MLuaItemBehaviorDispatcher.onUse(behaviorKey, level, player, hand);
                  return handled != null ? handled : super.use(level, player, hand);
               }

               public InteractionResult useOn(UseOnContext context) {
                  InteractionResult handled = MLuaItemBehaviorDispatcher.onUseOnBlock(behaviorKey, context);
                  return handled != null ? handled : super.useOn(context);
               }

               public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
                  MLuaItemBehaviorDispatcher.onInventoryTick(behaviorKey, stack, level, entity, slot, selected);
                  super.inventoryTick(stack, level, entity, slot, selected);
               }

               public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                  return MLuaItemBehaviorDispatcher.onAttackEntity(behaviorKey, stack, target, attacker)
                      && super.hurtEnemy(stack, target, attacker);
               }

               public void onCraftedBy(ItemStack stack, Level level, Player player) {
                  MLuaItemBehaviorDispatcher.onCrafted(behaviorKey, stack, level, player);
                  super.onCraftedBy(stack, level, player);
               }
            };
            //--endversion
      };
   }
}
