package dev.mlua.minecraft.binding;

import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.item.enchantment.MCEnchantmentImpl;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.nbt.MCCompoundTag;
import dev.mlua.mcapi.nbt.MCListTag;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

public final class MLuaEnchantmentWrapper {
   private MLuaEnchantmentWrapper() {}

   //--version 1.20.1
   public static LuaTable getEnchantments(MCItemStack stack) {
      LuaTable t = new LuaTable();
      if (!stack.hasTag() || !stack.getTag().contains("Enchantments", 9)) {
         return t;
      }
      MCListTag enchList = stack.getTag().getList("Enchantments", 10);
      int idx = 1;
      for (int i = 0; i < enchList.size(); i++) {
         MCCompoundTag entry = (MCCompoundTag) enchList.get(i);
         LuaTable ench = new LuaTable();
         ench.set("Id", LuaValue.valueOf(entry.getString("id")));
         ench.set("Level", LuaValue.valueOf(entry.getInt("lvl")));
         t.set(idx++, ench);
      }
      return t;
   }

   public static void addEnchantment(MCItemStack stack, String idStr, int level) {
      net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(idStr);
      if (id == null) return;
      net.minecraft.world.item.enchantment.Enchantment ench = BuiltInRegistries.ENCHANTMENT.get(id);
      if (ench == null) return;
      ((MCItemStackImpl)stack).inner().enchant(ench, level);
   }

   public static void removeEnchantment(MCItemStack stack, String idStr) {
      net.minecraft.world.item.ItemStack rawStack = ((MCItemStackImpl)stack).inner();
      if (!rawStack.hasTag() || !rawStack.getTag().contains("Enchantments", 9)) return;
      net.minecraft.nbt.ListTag enchList = rawStack.getTag().getList("Enchantments", 10);
      net.minecraft.nbt.ListTag newList = new net.minecraft.nbt.ListTag();
      for (int i = 0; i < enchList.size(); i++) {
         net.minecraft.nbt.CompoundTag entry = enchList.getCompound(i);
         if (!entry.getString("id").equals(idStr)) {
            newList.add(entry);
         }
      }
      rawStack.getTag().put("Enchantments", newList);
   }

   public static int getEnchantmentLevel(MCItemStack stack, String idStr) {
      net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(idStr);
      if (id == null) throw new LuaError("[MLua] Invalid enchantment id: " + idStr);
      Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
      if (enchantment == null) throw new LuaError("[MLua] Unknown enchantment: " + idStr);
      return stack.getEnchantmentLevel(new MCEnchantmentImpl(enchantment));
   }
   //--endversion
   //--version 1.21.1
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

   public static int getEnchantmentLevel(MCItemStack stack, String idStr) {
      Holder.Reference<Enchantment> enchantment = enchantmentRegistry()
         .getHolder(MLuaResourceLocations.parse(idStr)).orElse(null);
      if (enchantment == null) throw new LuaError("[MLua] Unknown enchantment: " + idStr);
      return stack.getEnchantmentLevel(new MCEnchantmentImpl(enchantment.value()));
   }

   @SuppressWarnings("unchecked")
   private static Registry<Enchantment> enchantmentRegistry() {
      return (Registry<Enchantment>) BuiltInRegistries.REGISTRY.get(Registries.ENCHANTMENT.location());
   }
   //--endversion
}
