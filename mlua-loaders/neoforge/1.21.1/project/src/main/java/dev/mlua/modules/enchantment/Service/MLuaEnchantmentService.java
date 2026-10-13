package dev.mlua.modules.enchantment.Service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

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
    }

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

    private static ItemStack extractItemStack(LuaValue val) {
        LuaTable t = val.checktable();
        LuaValue stackObj = t.get("__item_stack");
        if (stackObj.isuserdata(ItemStack.class)) {
            return (ItemStack) stackObj.checkuserdata(ItemStack.class);
        }
        throw new LuaError("[MLua] Expected an Item table");
    }

    @SuppressWarnings("unchecked")
    private static Registry<Enchantment> enchantmentRegistry() {
        return (Registry<Enchantment>) BuiltInRegistries.REGISTRY.get(Registries.ENCHANTMENT.location());
    }

    private static Holder.Reference<Enchantment> resolveEnchantment(String id) {
        ResourceLocation loc = MLuaResourceLocations.parse(id);
        return enchantmentRegistry().getHolder(loc).orElseThrow(() -> new LuaError("[MLua] Unknown enchantment: " + id));
    }

    private static String ComponentName(Enchantment ench) {
        return ench.description().getString();
    }

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
                    throw new LuaError("[MLua] " + e);
                }
            }
        };
    }
}
