package dev.mlua.minecraft.binding;

import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaEnchantmentWrapper {
    private MLuaEnchantmentWrapper() {}

    public static LuaTable getEnchantments(ItemStack stack) {
        LuaTable table = new LuaTable();
        for (var entry : stack.getEnchantments().entrySet()) {
            String id = entry.getKey().unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
            table.set(id, LuaValue.valueOf(entry.getIntValue()));
        }
        return table;
    }

    public static boolean addEnchantment(ItemStack stack, String id, int level) {
        Holder.Reference<Enchantment> enchantment = resolve(id);
        if (enchantment == null) return false;
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(enchantment, level));
        return true;
    }

    public static boolean removeEnchantment(ItemStack stack, String id) {
        Holder.Reference<Enchantment> enchantment = resolve(id);
        if (enchantment == null) return false;
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.removeIf(enchantment::equals));
        return true;
    }

    public static boolean hasEnchantment(ItemStack stack, String id) {
        Holder.Reference<Enchantment> enchantment = resolve(id);
        return enchantment != null && stack.getEnchantments().getLevel(enchantment) > 0;
    }

    private static Holder.Reference<Enchantment> resolve(String id) {
        try {
            return BuiltInRegistries.ENCHANTMENT.get(MLuaResourceLocations.parse(id)).orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
