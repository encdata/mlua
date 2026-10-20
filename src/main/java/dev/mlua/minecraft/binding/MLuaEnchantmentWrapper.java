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
import net.minecraft.resources.ResourceLocation;
//--version 1.20.1
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
//--endversion
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
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
      for (int i = 0; i < enchList.size(); i++) {
         MCCompoundTag entry = (MCCompoundTag) enchList.get(i);
         t.set(entry.getString("id"), LuaValue.valueOf(entry.getInt("lvl")));
      }
      return t;
   }

   public static boolean addEnchantment(MCItemStack stack, String idStr, int level) {
      ResourceLocation id = MLuaResourceLocations.tryParseOrNull(idStr);
      if (id == null) throw new LuaError("[MLua] Invalid enchantment id: " + idStr);
      Enchantment ench = BuiltInRegistries.ENCHANTMENT.get(id);
      if (ench == null) throw new LuaError("[MLua] Unknown enchantment: " + idStr);
      ((MCItemStackImpl)stack).inner().enchant(ench, level);
      return true;
   }

   public static boolean removeEnchantment(MCItemStack stack, String idStr) {
      ResourceLocation id = MLuaResourceLocations.tryParseOrNull(idStr);
      if (id == null) throw new LuaError("[MLua] Invalid enchantment id: " + idStr);
      if (BuiltInRegistries.ENCHANTMENT.get(id) == null) throw new LuaError("[MLua] Unknown enchantment: " + idStr);
      ItemStack rawStack = ((MCItemStackImpl)stack).inner();
      if (!rawStack.hasTag() || !rawStack.getTag().contains("Enchantments", 9)) return true;
      ListTag enchList = rawStack.getTag().getList("Enchantments", 10);
      ListTag newList = new ListTag();
      for (int i = 0; i < enchList.size(); i++) {
         CompoundTag entry = enchList.getCompound(i);
         if (!entry.getString("id").equals(id.toString())) {
            newList.add(entry);
         }
      }
      rawStack.getTag().put("Enchantments", newList);
      return true;
   }

   public static int getEnchantmentLevel(MCItemStack stack, String idStr) {
      ResourceLocation id = MLuaResourceLocations.tryParseOrNull(idStr);
      if (id == null) return 0;
      Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
      if (enchantment == null) return 0;
      return stack.getEnchantmentLevel(new MCEnchantmentImpl(enchantment));
   }

   public static boolean hasEnchantment(MCItemStack stack, String idStr) {
      return getEnchantmentLevel(stack, idStr) > 0;
   }
   //--endversion
   //--version 1.21.1
   public static LuaTable getEnchantments(MCItemStack stack) {
      LuaTable table = new LuaTable();
      for (var entry : ((MCItemStackImpl) stack).inner().getEnchantments().entrySet()) {
         String id = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
         table.set(id, LuaValue.valueOf(entry.getIntValue()));
      }
      return table;
   }

   public static boolean addEnchantment(MCItemStack stack, String idStr, int level) {
      ResourceLocation id = MLuaResourceLocations.tryParseOrNull(idStr);
      if (id == null) throw new LuaError("[MLua] Invalid enchantment id: " + idStr);
      Holder.Reference<Enchantment> enchantment = enchantmentRegistry().getHolder(id).orElse(null);
      if (enchantment == null) {
         throw new LuaError("[MLua] Unknown enchantment: " + idStr);
      }
      ((MCItemStackImpl) stack).inner().enchant(enchantment, level);
      return true;
   }

   public static boolean removeEnchantment(MCItemStack stack, String idStr) {
      ResourceLocation id = MLuaResourceLocations.tryParseOrNull(idStr);
      if (id == null) throw new LuaError("[MLua] Invalid enchantment id: " + idStr);
      Holder.Reference<Enchantment> enchantment = enchantmentRegistry().getHolder(id).orElse(null);
      if (enchantment == null) {
         throw new LuaError("[MLua] Unknown enchantment: " + idStr);
      }
      EnchantmentHelper.updateEnchantments(((MCItemStackImpl) stack).inner(), mutable -> mutable.removeIf(holder -> holder.equals(enchantment)));
      return true;
   }

   public static int getEnchantmentLevel(MCItemStack stack, String idStr) {
      ResourceLocation id = MLuaResourceLocations.tryParseOrNull(idStr);
      if (id == null) return 0;
      Holder.Reference<Enchantment> enchantment = enchantmentRegistry().getHolder(id).orElse(null);
      if (enchantment == null) return 0;
      return stack.getEnchantmentLevel(new MCEnchantmentImpl(enchantment.value()));
   }

   public static boolean hasEnchantment(MCItemStack stack, String idStr) {
      return getEnchantmentLevel(stack, idStr) > 0;
   }

   @SuppressWarnings("unchecked")
   private static Registry<Enchantment> enchantmentRegistry() {
      return (Registry<Enchantment>) BuiltInRegistries.REGISTRY.get(Registries.ENCHANTMENT.location());
   }
   //--endversion
}
