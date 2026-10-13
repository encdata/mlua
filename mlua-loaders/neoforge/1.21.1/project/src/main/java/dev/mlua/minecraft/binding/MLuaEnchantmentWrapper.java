package dev.mlua.minecraft.binding;

import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaEnchantmentWrapper {
   private MLuaEnchantmentWrapper() {
   }

   public static LuaTable getEnchantments(MCItemStack stack) {
      LuaTable table = new LuaTable();
      int index = 1;
      for (var entry : ((MCItemStackImpl) stack).inner().getEnchantments().entrySet()) {
         LuaTable ench = new LuaTable();
         ench.set("Id", LuaValue.valueOf(entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("unknown")));
         ench.set("Level", LuaValue.valueOf(entry.getIntValue()));
         table.set(index++, ench);
      }
      return table;
   }

   public static void addEnchantment(MCItemStack stack, String idStr, int level) {
      Holder.Reference<Enchantment> enchantment = enchantmentRegistry().getHolder(MLuaResourceLocations.parse(idStr)).orElse(null);
      if (enchantment == null) {
         return;
      }
      ((MCItemStackImpl) stack).inner().enchant(enchantment, level);
   }

   public static void removeEnchantment(MCItemStack stack, String idStr) {
      Holder.Reference<Enchantment> enchantment = enchantmentRegistry().getHolder(MLuaResourceLocations.parse(idStr)).orElse(null);
      if (enchantment == null) {
         return;
      }
      EnchantmentHelper.updateEnchantments(((MCItemStackImpl) stack).inner(), mutable -> mutable.removeIf(holder -> holder.equals(enchantment)));
   }

   @SuppressWarnings("unchecked")
   private static Registry<Enchantment> enchantmentRegistry() {
      return (Registry<Enchantment>) BuiltInRegistries.REGISTRY.get(Registries.ENCHANTMENT.location());
   }
}
