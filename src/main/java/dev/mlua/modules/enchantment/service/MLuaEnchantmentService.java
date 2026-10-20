package dev.mlua.modules.enchantment.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.Iterator;
import java.util.Optional;
//--version 26.1
import dev.mlua.minecraft.binding.MLuaEnchantmentWrapper;
//--endversion
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

@MLuaDescription("Service for managing enchantments on items.")
public final class MLuaEnchantmentService extends MLuaServerThreadReflectiveService {
    public MLuaEnchantmentService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "Enchantment";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Both");
        return table;
    }

    @MLuaDescription("Returns details for an enchantment by identifier.")
    public LuaTable GetEnchantment(String id) {
        //--version 26.1
        return enchantmentTable26(id);
        //--endversion
        //--version 1.20.1,1.21.1
        return enchantmentTable(id);
        //--endversion
    }

    @MLuaDescription("Returns the registered enchantments.")
    public LuaTable GetAllEnchantments() {
        LuaTable list = new LuaTable();
        int index = 1;
        //--version 1.20.1
        Iterator<Enchantment> iter = BuiltInRegistries.ENCHANTMENT.iterator();
        while (iter.hasNext()) {
            Enchantment enchantment = iter.next();
            ResourceLocation key = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
            if (key != null) {
                LuaTable entry = new LuaTable();
                entry.set("Id", key.toString());
                entry.set("Name", net.minecraft.network.chat.Component.translatable(enchantment.getDescriptionId()).getString());
                list.set(index++, entry);
            }
        }
        //--endversion
        //--version 26.1
        for (Enchantment enchantment : enchantmentRegistry26()) {
            var id = enchantmentRegistry26().getKey(enchantment);
            if (id != null) {
                LuaTable entry = new LuaTable();
                entry.set("Id", id.toString());
                entry.set("Name", enchantment.description().getString());
                list.set(index++, entry);
            }
        }
        //--endversion
        //--version 1.21.1
        for (Enchantment enchantment : enchantmentRegistry()) {
            ResourceLocation key = enchantmentRegistry().getKey(enchantment);
            if (key != null) {
                LuaTable entry = new LuaTable();
                entry.set("Id", key.toString());
                entry.set("Name", ComponentName(enchantment));
                list.set(index++, entry);
            }
        }
        //--endversion
        return list;
    }

    @MLuaDescription("Adds an enchantment and level to an item stack.")
    public boolean AddEnchantment(LuaValue itemValue, String id, Optional<Integer> requestedLevel) {
        ItemStack stack = extractItemStack(itemValue);
        int level = requestedLevel.orElse(1);
        //--version 1.20.1
        stack.enchant(resolveEnchantment(id), level);
        //--endversion
        //--version 1.21.1
        stack.enchant(resolveEnchantment(id), level);
        //--endversion
        //--version 26.1
        if (!MLuaEnchantmentWrapper.addEnchantment(stack, id, level)) {
            throw new LuaError("[MLua] Unknown enchantment: " + id);
        }
        //--endversion
        return true;
    }

    @MLuaDescription("Removes an enchantment from an item stack.")
    public boolean RemoveEnchantment(LuaValue itemValue, String id) {
        ItemStack stack = extractItemStack(itemValue);
        //--version 1.20.1
        Enchantment enchantment = resolveEnchantment(id);
        ResourceLocation enchantmentId = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
        if (!stack.hasTag() || !stack.getTag().contains("Enchantments", 9)) return true;
        ListTag enchantments = stack.getTag().getList("Enchantments", 10);
        ListTag remaining = new ListTag();
        for (int index = 0; index < enchantments.size(); index++) {
            CompoundTag entry = enchantments.getCompound(index);
            if (!entry.getString("id").equals(enchantmentId.toString())) remaining.add(entry);
        }
        stack.getTag().put("Enchantments", remaining);
        return true;
        //--endversion
        //--version 1.21.1
        Holder.Reference<Enchantment> enchantment = resolveEnchantment(id);
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.removeIf(holder -> holder.equals(enchantment)));
        return true;
        //--endversion
        //--version 26.1
        if (!MLuaEnchantmentWrapper.removeEnchantment(stack, id)) {
            throw new LuaError("[MLua] Unknown enchantment: " + id);
        }
        return true;
        //--endversion
    }

    @MLuaDescription("Returns a map from enchantment id to level for an item stack.")
    public LuaTable GetEnchantments(LuaValue itemValue) {
        ItemStack stack = extractItemStack(itemValue);
        LuaTable result = new LuaTable();
        //--version 1.20.1
        if (stack.hasTag() && stack.getTag().contains("Enchantments", 9)) {
            ListTag enchantments = stack.getTag().getList("Enchantments", 10);
            for (int index = 0; index < enchantments.size(); index++) {
                CompoundTag entry = enchantments.getCompound(index);
                result.set(entry.getString("id"), entry.getInt("lvl"));
            }
        }
        //--endversion
        //--version 1.21.1
        for (var entry : stack.getEnchantments().entrySet()) {
            String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
            result.set(enchantmentId, entry.getIntValue());
        }
        //--endversion
        //--version 26.1
        return MLuaEnchantmentWrapper.getEnchantments(stack);
        //--endversion
        //--version 1.20.1,1.21.1
        return result;
        //--endversion
    }

    @MLuaDescription("Returns whether an item stack has an enchantment.")
    public boolean HasEnchantment(LuaValue itemValue, String id) {
        ItemStack stack = extractItemStack(itemValue);
        //--version 1.20.1
        ResourceLocation enchantmentId = MLuaResourceLocations.tryParseOrNull(id);
        if (enchantmentId == null || BuiltInRegistries.ENCHANTMENT.get(enchantmentId) == null) return false;
        if (stack.hasTag() && stack.getTag().contains("Enchantments", 9)) {
            ListTag enchantments = stack.getTag().getList("Enchantments", 10);
            for (int index = 0; index < enchantments.size(); index++) {
                CompoundTag enchantment = enchantments.getCompound(index);
                if (enchantment.getString("id").equals(enchantmentId.toString()) && enchantment.getInt("lvl") > 0) return true;
            }
        }
        return false;
        //--endversion
        //--version 1.21.1
        ResourceLocation enchantmentId = MLuaResourceLocations.tryParseOrNull(id);
        if (enchantmentId == null) return false;
        Holder.Reference<Enchantment> enchantment = enchantmentRegistry().getHolder(enchantmentId).orElse(null);
        return enchantment != null && stack.getEnchantments().getLevel(enchantment) > 0;
        //--endversion
        //--version 26.1
        return MLuaEnchantmentWrapper.hasEnchantment(stack, id);
        //--endversion
    }

    //--version 1.20.1
    private static LuaTable enchantmentTable(String id) {
        Enchantment ench = resolveEnchantment(id);
        ResourceLocation key = BuiltInRegistries.ENCHANTMENT.getKey(ench);
        LuaTable table = new LuaTable();
        table.set("Id", LuaValue.valueOf(key != null ? key.toString() : id));
        table.set("Name", LuaValue.valueOf(net.minecraft.network.chat.Component.translatable(ench.getDescriptionId()).getString()));
        table.set("MinLevel", LuaValue.valueOf(ench.getMinLevel()));
        table.set("MaxLevel", LuaValue.valueOf(ench.getMaxLevel()));
        table.set("Rarity", LuaValue.valueOf(ench.getRarity().name()));
        table.set("IsTreasure", LuaValue.valueOf(ench.isTreasureOnly()));
        table.set("IsCurse", LuaValue.valueOf(ench.isCurse()));
        table.set("IsTradeable", LuaValue.valueOf(ench.isTradeable()));
        table.set("IsDiscoverable", LuaValue.valueOf(ench.isDiscoverable()));
        return table;
    }
    //--endversion
    //--version 26.1
    private static LuaTable enchantmentTable26(String id) {
        Holder.Reference<Enchantment> enchantment = enchantmentRegistry26()
            .get(MLuaResourceLocations.parse(id))
            .orElseThrow(() -> new LuaError("[MLua] Unknown enchantment: " + id));
        Enchantment value = enchantment.value();
        LuaTable table = new LuaTable();
        table.set("Id", enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse(id));
        table.set("Name", value.description().getString());
        table.set("MinLevel", value.getMinLevel());
        table.set("MaxLevel", value.getMaxLevel());
        return table;
    }
    //--endversion
    //--version 1.21.1
    private static LuaTable enchantmentTable(String id) {
        Holder.Reference<Enchantment> ench = resolveEnchantment(id);
        ResourceLocation key = enchantmentRegistry().getKey(ench.value());
        LuaTable table = new LuaTable();
        table.set("Id", LuaValue.valueOf(key != null ? key.toString() : id));
        table.set("Name", LuaValue.valueOf(ComponentName(ench.value())));
        table.set("MinLevel", LuaValue.valueOf(ench.value().getMinLevel()));
        table.set("MaxLevel", LuaValue.valueOf(ench.value().getMaxLevel()));
        return table;
    }
    //--endversion

    private static ItemStack extractItemStack(LuaValue val) {
        LuaTable t = val.checktable();
        LuaValue stackObj = t.get("__item_stack");
        if (stackObj.isuserdata(ItemStack.class)) {
            return (ItemStack) stackObj.checkuserdata(ItemStack.class);
        }
        throw new LuaError("[MLua] Expected an Item table");
    }

    //--version 1.20.1
    private static Enchantment resolveEnchantment(String id) {
        ResourceLocation loc = MLuaResourceLocations.parse(id);
        Enchantment ench = BuiltInRegistries.ENCHANTMENT.get(loc);
        if (ench == null) throw new LuaError("[MLua] Unknown enchantment: " + id);
        return ench;
    }
    //--endversion
    //--version 1.21.1
    private static Holder.Reference<Enchantment> resolveEnchantment(String id) {
        ResourceLocation loc = MLuaResourceLocations.parse(id);
        return enchantmentRegistry().getHolder(loc).orElseThrow(() -> new LuaError("[MLua] Unknown enchantment: " + id));
    }

    @SuppressWarnings("unchecked")
    private static Registry<Enchantment> enchantmentRegistry() {
        return (Registry<Enchantment>) BuiltInRegistries.REGISTRY.get(Registries.ENCHANTMENT.location());
    }

    private static String ComponentName(Enchantment ench) {
        return ench.description().getString();
    }
    //--endversion

    //--version 26.1
    @SuppressWarnings("unchecked")
    private static Registry<Enchantment> enchantmentRegistry26() {
        return (Registry<Enchantment>) BuiltInRegistries.REGISTRY.get(Registries.ENCHANTMENT.registry()).orElseThrow().value();
    }
    //--endversion

}
