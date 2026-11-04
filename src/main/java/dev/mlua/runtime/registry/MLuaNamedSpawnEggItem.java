package dev.mlua.runtime.registry;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
//--version 1.20.1
import net.minecraft.world.level.Level;
//--endversion
//--version 26.1
import net.minecraft.world.item.component.TooltipDisplay;
//--endversion

public class MLuaNamedSpawnEggItem extends SpawnEggItem {
   private final String registryName;

   public MLuaNamedSpawnEggItem(EntityType<? extends Mob> entityType, int primaryColor, int secondaryColor, Item.Properties properties, String modId, String name) {
      super(entityType, primaryColor, secondaryColor, properties);
      this.registryName = modId + ":" + name;
   }

   public Component getName(ItemStack stack) {
      return MLuaItemHooks.itemName(this.registryName);
   }

   //--version 1.21.1
   public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      MLuaItemHooks.appendDescriptionTooltip(this, stack, context, tooltip, flag);
      super.appendHoverText(stack, context, tooltip, flag);
   }
   //--endversion
   //--version 1.20.1
   public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      MLuaItemHooks.appendDescriptionTooltip(this, stack, level, tooltip, flag);
      super.appendHoverText(stack, level, tooltip, flag);
   }
   //--endversion
   //--version 26.1
   public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
      MLuaItemHooks.appendDescriptionTooltip(this, stack, context, display, tooltip, flag);
      super.appendHoverText(stack, context, display, tooltip, flag);
   }
   //--endversion

   @SuppressWarnings("rawtypes")
   public void initializeClient(Consumer consumer) {
      MLuaItemHooks.initializeClient(consumer);
   }
}
