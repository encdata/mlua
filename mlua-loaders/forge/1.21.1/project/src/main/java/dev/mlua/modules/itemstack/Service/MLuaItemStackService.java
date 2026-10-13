package dev.mlua.modules.itemstack.Service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.nbt.MCCompoundTagImpl;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaNbtWrapper;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaItemStackService extends MLuaServiceBase {
    public MLuaItemStackService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "ItemStackService";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("__side", "Both");
        table.set("Create", fn(args -> create(args.checkjstring(2), args.optint(3, 1))));
        table.set("Of", table.get("Create"));
        table.set("Empty", fn(args -> MLuaItemWrapper.itemTable(ItemStack.EMPTY, context.registration())));
        table.set("Clone", fn(args -> MLuaItemWrapper.itemTable(stack(args.arg(2)).copy(), context.registration())));
        table.set("IsEmpty", fn(args -> LuaValue.valueOf(stack(args.arg(2)).isEmpty())));
        table.set("GetId", fn(args -> LuaValue.valueOf(id(stack(args.arg(2))))));
        table.set("GetCount", fn(args -> LuaValue.valueOf(stack(args.arg(2)).getCount())));
        table.set("SetCount", fn(args -> {
            stack(args.arg(2)).setCount(args.checkint(3));
            return LuaValue.NONE;
        }));
        table.set("GetNbt", fn(args -> {
            MCItemStackImpl stack = new MCItemStackImpl(stack(args.arg(2)));
            if (args.narg() < 3 || args.arg(3).isnil()) {
                return MLuaNbtWrapper.table(
                    () -> {
                        var tag = stack.getOrCreateTag();
                        return ((MCCompoundTagImpl) tag).inner();
                    },
                    tag -> stack.setTag(MCCompoundTagImpl.fromReal(tag)), context.registration()
                );
            }
            return stack.hasTag() ? MLuaNbtWrapper.get(((MCCompoundTagImpl) stack.getTag()).inner(), args.checkjstring(3)) : LuaValue.NIL;
        }));
        table.set("SetNbt", fn(args -> {
            MCItemStackImpl stack = new MCItemStackImpl(stack(args.arg(2)));
            CompoundTag tag = ((MCCompoundTagImpl) stack.getOrCreateTag()).inner();
            MLuaNbtWrapper.set(tag, args.checkjstring(3), args.arg(4));
            stack.setTag(MCCompoundTagImpl.fromReal(tag));
            return LuaValue.NONE;
        }));
        return table;
    }

    private LuaValue create(String id, int count) {
        Item item = BuiltInRegistries.ITEM.get(MLuaResourceLocations.parse(id));
        if (item == null) {
            throw new LuaError("[MLua] Unknown item: " + id);
        }
        return MLuaItemWrapper.itemTable(new ItemStack(item, Math.max(0, count)), context.registration());
    }

    private static ItemStack stack(LuaValue value) {
        return MLuaItemWrapper.toItemStack(value);
    }

    private static String id(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    private static VarArgFunction fn(Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaError("[MLua] " + e.getMessage());
                }
            }
        };
    }
}
