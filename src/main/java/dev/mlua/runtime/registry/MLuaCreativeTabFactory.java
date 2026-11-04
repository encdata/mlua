package dev.mlua.runtime.registry;

import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class MLuaCreativeTabFactory {
   public CreativeModeTab create(String modId, String name, Map<String, Object> properties) {
      String title = String.valueOf(properties.getOrDefault("title", "MLua"));
      String iconId = String.valueOf(properties.getOrDefault("icon", "minecraft:stone"));
      Object items = properties.get("items");
      CreativeModeTab.Builder builder = (CreativeModeTab.Builder) LoaderApi.registry().createCreativeTabBuilder();
      return builder.title(Component.literal(title)).icon(() -> {
         return this.stack(iconId);
      }).displayItems((params, output) -> {
         if (items instanceof List<?> list) {
            Iterator var5 = list.iterator();

            while(var5.hasNext()) {
               Object value = var5.next();
               ItemStack stack = this.stack(String.valueOf(value));
               if (!stack.isEmpty()) {
                  output.accept(stack);
               }
            }
         }

      }).build();
   }

   private ItemStack stack(String id) {
      Item item = (Item)BuiltInRegistries.ITEM.getOptional(MLuaResourceLocations.parse(id)).orElse(null);
      return item == null ? ItemStack.EMPTY : new ItemStack(item);
   }
}
