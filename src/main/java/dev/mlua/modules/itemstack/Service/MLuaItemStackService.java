package dev.mlua.modules.itemstack.Service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaNbtWrapper;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
//--version 1.21.1,26.1
import net.minecraft.nbt.CompoundTag;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.nbt.MCCompoundTagImpl;
//--endversion

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
        table.set("Empty", fn(args -> MLuaItemWrapper.itemTable(ItemStack.EMPTY, context.mlua().registration())));
        table.set("Clone", fn(args -> MLuaItemWrapper.itemTable(stack(args.arg(2)).copy(), context.mlua().registration())));
        table.set("IsEmpty", fn(args -> LuaValue.valueOf(stack(args.arg(2)).isEmpty())));
        table.set("GetId", fn(args -> LuaValue.valueOf(id(stack(args.arg(2))))));
        table.set("GetCount", fn(args -> LuaValue.valueOf(stack(args.arg(2)).getCount())));
        table.set("SetCount", fn(args -> {
            stack(args.arg(2)).setCount(args.checkint(3));
            return LuaValue.NONE;
        }));
        table.set("GetNbt", fn(args -> {
            //--version 1.20.1
            ItemStack stack = stack(args.arg(2));
            if (args.narg() < 3 || args.arg(3).isnil()) {
                return MLuaNbtWrapper.table(stack::getOrCreateTag, stack::setTag, this.context.registration());
            }
            return stack.hasTag() ? MLuaNbtWrapper.get(stack.getTag(), args.checkjstring(3)) : LuaValue.NIL;
            //--endversion
            //--version 1.21.1,26.1
            MCItemStackImpl stack = new MCItemStackImpl(stack(args.arg(2)));
            if (args.narg() < 3 || args.arg(3).isnil()) {
                return MLuaNbtWrapper.table(
                    () -> {
                        var tag = stack.getOrCreateTag();
                        return ((MCCompoundTagImpl) tag).inner();
                    },
                    tag -> stack.setTag(MCCompoundTagImpl.fromReal(tag)), this.context.registration()
                );
            }
            return stack.hasTag() ? MLuaNbtWrapper.get(((MCCompoundTagImpl) stack.getTag()).inner(), args.checkjstring(3)) : LuaValue.NIL;
            //--endversion
        }));
        table.set("SetNbt", fn(args -> {
            //--version 1.20.1
            MLuaNbtWrapper.set(stack(args.arg(2)).getOrCreateTag(), args.checkjstring(3), args.arg(4));
            return LuaValue.NONE;
            //--endversion
            //--version 1.21.1,26.1
            MCItemStackImpl stack = new MCItemStackImpl(stack(args.arg(2)));
            CompoundTag tag = ((MCCompoundTagImpl) stack.getOrCreateTag()).inner();
            MLuaNbtWrapper.set(tag, args.checkjstring(3), args.arg(4));
            stack.setTag(MCCompoundTagImpl.fromReal(tag));
            return LuaValue.NONE;
            //--endversion
        }));
        return table;
    }

    private LuaValue create(String id, int count) {
        Item item = (Item) MCApiRuntime.lookupRegistry("item", id);
        if (item == null) {
            throw new LuaError("[MLua] Unknown item: " + id);
        }
        return MLuaItemWrapper.itemTable(new ItemStack(item, Math.max(0, count)), context.mlua().registration());
    }

    private static ItemStack stack(LuaValue value) {
        return MLuaItemWrapper.toItemStack(value);
    }

    private static String id(ItemStack stack) {
        var id = MCApiRuntime.registryKey("item", stack.getItem());
        return id == null ? "minecraft:air" : id;
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
