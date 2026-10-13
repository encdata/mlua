package dev.mlua.runtime.registry;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class MLuaNamedItem extends Item {
   private final String displayName;

   public MLuaNamedItem(Item.Properties properties, String registryName) {
      super(properties);
      this.displayName = MLuaRegistryNameHelper.displayName(registryName);
   }

   public Component getName(ItemStack stack) {
      return Component.literal(this.displayName);
   }

   //--version 1.21.1
   public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      var id = BuiltInRegistries.ITEM.getKey(this);
      if (id != null) {
         List<String> desc = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
         if (desc != null) {
            for (String line : desc) {
               tooltip.add(Component.literal(line).withStyle(style -> style.withItalic(true).withColor(0x808080)));
            }
         }
      }
      super.appendHoverText(stack, context, tooltip, flag);
   }
   //--endversion
   //--version 1.20.1
   public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
      var id = BuiltInRegistries.ITEM.getKey(this);
      if (id != null) {
         List<String> desc = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
         if (desc != null) {
            for (String line : desc) {
               tooltip.add(Component.literal(line).withStyle(style -> style.withItalic(true).withColor(0x808080)));
            }
         }
      }
      super.appendHoverText(stack, level, tooltip, flag);
   }
   //--endversion
   //--version 26.1
   public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
      MLuaItemHooks.appendDescriptionTooltip(this, stack, context, display, tooltip, flag);
      super.appendHoverText(stack, context, display, tooltip, flag);
   }
   //--endversion

   @SuppressWarnings("rawtypes")
   public void initializeClient(Consumer consumer) {
      MLuaItemHooks.initializeClient(consumer);
   }
}
