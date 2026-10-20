package dev.mlua.runtime.registry;

//--version 1.20.1,1.21.1
import dev.mlua.runtime.client.MLuaVisualItemRenderer;
//--endversion
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//--version 26.1
import net.minecraft.world.item.component.TooltipDisplay;
//--endversion
//--version 1.20.1
import net.minecraft.world.level.Level;
//--endversion

public final class MLuaItemHooks {
   private MLuaItemHooks() {
   }

   public static Component itemName(String registryName) {
      return Component.literal(MLuaRegistryNameHelper.displayName(registryName));
   }

   //--version 1.21.1
   public static void appendDescriptionTooltip(Item item, ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      var id = BuiltInRegistries.ITEM.getKey(item);
      if (id != null) {
         List<String> desc = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
         if (desc != null) {
            for (String line : desc) {
               tooltip.add(Component.literal(line).withStyle((style) -> style.withItalic(true).withColor(8421504)));
            }
         }
      }
   }
   //--endversion
   //--version 1.20.1
   public static void appendDescriptionTooltip(Item item, ItemStack stack, @javax.annotation.Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      var id = BuiltInRegistries.ITEM.getKey(item);
      if (id != null) {
         List<String> desc = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
         if (desc != null) {
            for (String line : desc) {
               tooltip.add(Component.literal(line).withStyle((style) -> style.withItalic(true).withColor(8421504)));
            }
         }
      }
   }
   //--endversion
   //--version 26.1
   public static void appendDescriptionTooltip(Item item, ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
      var id = BuiltInRegistries.ITEM.getKey(item);
      if (id != null) {
         List<String> desc = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
         if (desc != null) for (String line : desc) tooltip.accept(Component.literal(line).withStyle(style -> style.withItalic(true).withColor(8421504)));
      }
   }
   //--endversion

   @SuppressWarnings({"rawtypes", "unchecked"})
   public static void initializeClient(Consumer consumer) {
      try {
         Class<?> extensionsClass = Class.forName("net.minecraftforge.client.extensions.common.IClientItemExtensions");
         Object proxy = Proxy.newProxyInstance(
            MLuaItemHooks.class.getClassLoader(),
            new Class<?>[]{extensionsClass},
            (instance, method, args) -> {
               if (method.getDeclaringClass() == Object.class) {
                  return switch (method.getName()) {
                     case "toString" -> "MLuaItemExtensions";
                     case "hashCode" -> System.identityHashCode(instance);
                     case "equals" -> instance == (args != null && args.length > 0 ? args[0] : null);
                     default -> method.invoke(instance, args);
                  };
               }

               //--version 1.20.1,1.21.1
               if ("getCustomRenderer".equals(method.getName())) {
                  return MLuaVisualItemRenderer.instance();
               }
               //--endversion

               Class<?> returnType = method.getReturnType();
               if (returnType == Boolean.TYPE) {
                  return Boolean.FALSE;
               }

               if (returnType == Byte.TYPE) {
                  return (byte)0;
               }

               if (returnType == Short.TYPE) {
                  return (short)0;
               }

               if (returnType == Integer.TYPE) {
                  return 0;
               }

               if (returnType == Long.TYPE) {
                  return 0L;
               }

               if (returnType == Float.TYPE) {
                  return 0.0F;
               }

               if (returnType == Double.TYPE) {
                  return 0.0D;
               }

               if (returnType == Character.TYPE) {
                  return '\u0000';
               }

               return null;
            }
         );
         consumer.accept(proxy);
      } catch (ClassNotFoundException ignored) {
      }
   }
}
