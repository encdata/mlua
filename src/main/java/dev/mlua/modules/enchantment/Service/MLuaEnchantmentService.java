package dev.mlua.modules.enchantment.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.Iterator;
import java.util.function.Function;
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

public final class MLuaEnchantmentService extends MLuaServiceBase {
    public MLuaEnchantmentService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "Enchantment";
    }

    @Override
    public LuaTable toLua() {
        //--version 1.20.1
        LuaTable table = new LuaTable();
        table.set("__side", "Both");

        table.set("GetEnchantment", fn((args) -> {
            String id = args.checkjstring(2);
            return enchantmentTable(id);
        }));

        table.set("GetAllEnchantments", fn((args) -> {
            LuaTable list = new LuaTable();
            int index = 1;
            Iterator<Enchantment> iter = BuiltInRegistries.ENCHANTMENT.iterator();
            while (iter.hasNext()) {
                Enchantment ench = iter.next();
                ResourceLocation key = BuiltInRegistries.ENCHANTMENT.getKey(ench);
                if (key != null) {
                    LuaTable entry = new LuaTable();
                    entry.set("Id", LuaValue.valueOf(key.toString()));
                    entry.set("Name", LuaValue.valueOf(net.minecraft.network.chat.Component.translatable(ench.getDescriptionId()).getString()));
                    list.set(index++, entry);
                }
            }
            return list;
        }));

        table.set("AddEnchantment", fn((args) -> {
            ItemStack stack = extractItemStack(args.arg(2));
            Enchantment ench = resolveEnchantment(args.checkjstring(3));
            int level = args.optint(4, 1);
            stack.enchant(ench, level);
            return LuaValue.TRUE;
        }));

        table.set("RemoveEnchantment", fn((args) -> {
            ItemStack stack = extractItemStack(args.arg(2));
            String enchId = args.checkjstring(3);
            if (!stack.hasTag() || !stack.getTag().contains("Enchantments", 9)) {
                return LuaValue.FALSE;
            }
            ListTag enchList = stack.getTag().getList("Enchantments", 10);
            ListTag newList = new ListTag();
            for (int i = 0; i < enchList.size(); i++) {
                CompoundTag entry = enchList.getCompound(i);
                if (!entry.getString("id").equals(enchId)) {
                    newList.add(entry);
                }
            }
            stack.getTag().put("Enchantments", newList);
            return LuaValue.TRUE;
        }));

        table.set("GetEnchantments", fn((args) -> {
            ItemStack stack = extractItemStack(args.arg(2));
            LuaTable result = new LuaTable();
            if (stack.hasTag() && stack.getTag().contains("Enchantments", 9)) {
                ListTag enchList = stack.getTag().getList("Enchantments", 10);
                for (int i = 0; i < enchList.size(); i++) {
                    CompoundTag entry = enchList.getCompound(i);
                    String id = entry.getString("id");
                    int level = entry.getInt("lvl");
                    result.set(id, LuaValue.valueOf(level));
                }
            }
            return result;
        }));

        table.set("HasEnchantment", fn((args) -> {
            ItemStack stack = extractItemStack(args.arg(2));
            String enchId = args.checkjstring(3);
            if (stack.hasTag() && stack.getTag().contains("Enchantments", 9)) {
                ListTag enchList = stack.getTag().getList("Enchantments", 10);
                for (int i = 0; i < enchList.size(); i++) {
                    CompoundTag entry = enchList.getCompound(i);
                    if (entry.getString("id").equals(enchId)) {
                        return LuaValue.TRUE;
                    }
                }
            }
            return LuaValue.FALSE;
        }));

        return table;
        //--endversion
        //--version 26.1
        LuaTable table = new LuaTable();
        table.set("__side", "Both");
        table.set("GetEnchantment", fn(args -> enchantmentTable26(args.checkjstring(2))));
        table.set("GetAllEnchantments", fn(args -> {
            LuaTable list = new LuaTable();
            int index = 1;
            for (Enchantment enchantment : enchantmentRegistry26()) {
                var id = enchantmentRegistry26().getKey(enchantment);
                if (id != null) {
                    LuaTable entry = new LuaTable();
                    entry.set("Id", id.toString());
                    entry.set("Name", enchantment.description().getString());
                    list.set(index++, entry);
                }
            }
            return list;
        }));
        table.set("AddEnchantment", fn(args -> {
            ItemStack stack = extractItemStack(args.arg(2));
            String id = args.checkjstring(3);
            int level = args.optint(4, 1);
            if (!MLuaEnchantmentWrapper.addEnchantment(stack, id, level)) {
                throw new LuaError("[MLua] Unknown enchantment: " + id);
            }
            return LuaValue.TRUE;
        }));
        table.set("RemoveEnchantment", fn(args -> {
            ItemStack stack = extractItemStack(args.arg(2));
            String id = args.checkjstring(3);
            if (!MLuaEnchantmentWrapper.removeEnchantment(stack, id)) {
                throw new LuaError("[MLua] Unknown enchantment: " + id);
            }
            return LuaValue.TRUE;
        }));
        table.set("GetEnchantments", fn(args -> MLuaEnchantmentWrapper.getEnchantments(extractItemStack(args.arg(2)))));
        table.set("HasEnchantment", fn(args -> LuaValue.valueOf(
            MLuaEnchantmentWrapper.hasEnchantment(extractItemStack(args.arg(2)), args.checkjstring(3)))));
        return table;
        //--endversion
        //--version 1.21.1
        LuaTable table = new LuaTable();
        table.set("__side", "Both");

        table.set("GetEnchantment", fn(args -> enchantmentTable(args.checkjstring(2))));

        table.set("GetAllEnchantments", fn(args -> {
            LuaTable list = new LuaTable();
            int index = 1;
            for (Enchantment ench : enchantmentRegistry()) {
                ResourceLocation key = enchantmentRegistry().getKey(ench);
                if (key != null) {
                    LuaTable entry = new LuaTable();
                    entry.set("Id", LuaValue.valueOf(key.toString()));
                    entry.set("Name", LuaValue.valueOf(ComponentName(ench)));
                    list.set(index++, entry);
                }
            }
            return list;
        }));

        table.set("AddEnchantment", fn(args -> {
            ItemStack stack = extractItemStack(args.arg(2));
            Holder.Reference<Enchantment> ench = resolveEnchantment(args.checkjstring(3));
            int level = args.optint(4, 1);
            stack.enchant(ench, level);
            return LuaValue.TRUE;
        }));

        table.set("RemoveEnchantment", fn(args -> {
            ItemStack stack = extractItemStack(args.arg(2));
            Holder.Reference<Enchantment> ench = resolveEnchantment(args.checkjstring(3));
            EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.removeIf(holder -> holder.equals(ench)));
            return LuaValue.TRUE;
        }));

        table.set("GetEnchantments", fn(args -> {
            ItemStack stack = extractItemStack(args.arg(2));
            LuaTable result = new LuaTable();
            for (var entry : stack.getEnchantments().entrySet()) {
                String id = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
                result.set(id, LuaValue.valueOf(entry.getIntValue()));
            }
            return result;
        }));

        table.set("HasEnchantment", fn(args -> {
            ItemStack stack = extractItemStack(args.arg(2));
            Holder.Reference<Enchantment> ench = resolveEnchantment(args.checkjstring(3));
            return LuaValue.valueOf(stack.getEnchantments().getLevel(ench) > 0);
        }));

        return table;
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

    private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    LuaValue result = body.apply(args);
                    return result != null ? result : LuaValue.NONE;
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaError("[MLua] " + e.toString());
                }
            }
        };
    }
}
