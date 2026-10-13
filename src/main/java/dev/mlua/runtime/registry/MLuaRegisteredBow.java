package dev.mlua.runtime.registry;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//--version 1.20.1,1.21.1
import net.minecraft.world.InteractionResultHolder;
//--endversion
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import java.util.List;
import java.util.function.Consumer;

/** Native bow behavior with MLua registry naming, descriptions, and item rendering hooks. */
final class MLuaRegisteredBow extends BowItem {
   private final String registryKey;

   MLuaRegisteredBow(Item.Properties properties, String registryKey) {
      super(properties);
      this.registryKey = registryKey;
   }

   @Override
   public Component getName(ItemStack stack) {
      return MLuaItemHooks.itemName(this.registryKey);
   }

   @SuppressWarnings("rawtypes")
   public void initializeClient(Consumer consumer) {
      MLuaItemHooks.initializeClient(consumer);
   }

   //--version 1.20.1,1.21.1
   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      InteractionResultHolder<ItemStack> handled = MLuaItemFactory.invokeOnUse(this.registryKey, level, player, hand);
      return handled != null ? handled : super.use(level, player, hand);
   }
   //--endversion

   //--version 26.1
   @Override
   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      InteractionResult handled = MLuaItemFactory.invokeOnUse(this.registryKey, level, player, hand);
      return handled != null ? handled : super.use(level, player, hand);
   }
   //--endversion

   @Override
   public InteractionResult useOn(UseOnContext context) {
      InteractionResult handled = MLuaItemFactory.invokeOnUseOnBlock(this.registryKey, context);
      return handled != null ? handled : super.useOn(context);
   }

   @Override
   public boolean hurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target,
                            net.minecraft.world.entity.LivingEntity attacker) {
      return MLuaItemFactory.invokeOnAttackEntity(this.registryKey, stack, target, attacker)
         && super.hurtEnemy(stack, target, attacker);
   }

   //--version 1.20.1,1.21.1
   @Override
   public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
      MLuaItemFactory.invokeOnInventoryTick(this.registryKey, stack, level, entity, slot, selected);
      super.inventoryTick(stack, level, entity, slot, selected);
   }
   //--endversion

   //--version 26.1
   @Override
   public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                             Entity entity, net.minecraft.world.entity.EquipmentSlot slot) {
      MLuaItemFactory.invokeOnInventoryTick(this.registryKey, stack, level, entity, slot);
      super.inventoryTick(stack, level, entity, slot);
   }
   //--endversion

   //--version 1.20.1,1.21.1
   @Override
   public void onCraftedBy(ItemStack stack, Level level, Player player) {
      MLuaItemFactory.invokeOnCrafted(this.registryKey, stack, level, player);
      super.onCraftedBy(stack, level, player);
   }
   //--endversion

   //--version 26.1
   @Override
   public void onCraftedBy(ItemStack stack, Player player) {
      MLuaItemFactory.invokeOnCrafted(this.registryKey, stack, player);
      super.onCraftedBy(stack, player);
   }
   //--endversion

   //--version 1.20.1
   @Override
   public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level,
                               List<Component> tooltip, TooltipFlag flag) {
      MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
      super.appendHoverText(stack, level, tooltip, flag);
   }
   //--endversion

   //--version 1.21.1
   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
      super.appendHoverText(stack, context, tooltip, flag);
   }
   //--endversion

   //--version 26.1
   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                               net.minecraft.world.item.component.TooltipDisplay display,
                               Consumer<Component> tooltip, TooltipFlag flag) {
      var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this);
      if (id != null) {
         List<String> lines = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
         if (lines != null) for (String line : lines) {
            tooltip.accept(Component.literal(line).withStyle(style -> style.withItalic(true).withColor(0x808080)));
         }
      }
      super.appendHoverText(stack, context, display, tooltip, flag);
   }
   //--endversion
}
