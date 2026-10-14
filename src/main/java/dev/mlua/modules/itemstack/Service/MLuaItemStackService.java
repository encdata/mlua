package dev.mlua.modules.itemstack.Service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaNbtWrapper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.api.MLuaDescription;
//--version 1.21.1,26.1
import net.minecraft.nbt.CompoundTag;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.nbt.MCCompoundTagImpl;
//--endversion

public final class MLuaItemStackService extends MLuaReflectiveService {
    public MLuaItemStackService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "ItemStackService";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Both");
        return table;
    }

    @MLuaDescription("Creates an item stack by registry identifier and optional count, defaulting to one.")
    public LuaValue Create(String id, Optional<Integer> count) {
        return create(id, count.orElse(1));
    }

    @MLuaDescription("Creates an item stack by registry identifier and optional count, defaulting to one.")
    public LuaValue Of(String id, Optional<Integer> count) {
        return create(id, count.orElse(1));
    }

    @MLuaDescription("Returns an empty item stack instance.")
    public LuaValue Empty() {
        return MLuaItemWrapper.itemTable(ItemStack.EMPTY, this.context.registration());
    }

    @MLuaDescription("Returns a copy of an item stack.")
    public LuaValue Clone(LuaValue item) {
        return MLuaItemWrapper.itemTable(stack(item).copy(), this.context.registration());
    }

    @MLuaDescription("Returns whether an item stack is empty.")
    public boolean IsEmpty(LuaValue item) {
        return stack(item).isEmpty();
    }

    @MLuaDescription("Returns an item stack's registered item identifier.")
    public String GetId(LuaValue item) {
        return id(stack(item));
    }

    @MLuaDescription("Returns the number of items in a stack.")
    public int GetCount(LuaValue item) {
        return stack(item).getCount();
    }

    @MLuaDescription("Sets the number of items in a stack.")
    public void SetCount(LuaValue item, int count) {
        stack(item).setCount(count);
    }

    @MLuaDescription("Returns an item stack's NBT data or one value by key.")
    public LuaValue GetNbt(LuaValue item, Optional<String> key) {
        //--version 1.20.1
        ItemStack stack = stack(item);
        if (key.isEmpty()) {
            return MLuaNbtWrapper.table(stack::getOrCreateTag, stack::setTag, this.context.registration());
        }
        return stack.hasTag() ? MLuaNbtWrapper.get(stack.getTag(), key.get()) : LuaValue.NIL;
        //--endversion
        //--version 1.21.1,26.1
        MCItemStackImpl stack = new MCItemStackImpl(stack(item));
        if (key.isEmpty()) {
            return MLuaNbtWrapper.table(
                () -> {
                    var tag = stack.getOrCreateTag();
                    return ((MCCompoundTagImpl) tag).inner();
                },
                tag -> stack.setTag(MCCompoundTagImpl.fromReal(tag)), this.context.registration()
            );
        }
        return stack.hasTag() ? MLuaNbtWrapper.get(((MCCompoundTagImpl) stack.getTag()).inner(), key.get()) : LuaValue.NIL;
        //--endversion
    }

    @MLuaDescription("Sets one NBT value in an item stack's NBT data.")
    public void SetNbt(LuaValue item, String key, LuaValue value) {
        //--version 1.20.1
        MLuaNbtWrapper.set(stack(item).getOrCreateTag(), key, value);
        //--endversion
        //--version 1.21.1,26.1
        MCItemStackImpl stack = new MCItemStackImpl(stack(item));
        CompoundTag tag = ((MCCompoundTagImpl) stack.getOrCreateTag()).inner();
        MLuaNbtWrapper.set(tag, key, value);
        stack.setTag(MCCompoundTagImpl.fromReal(tag));
        //--endversion
    }

    private LuaValue create(String id, int count) {
        Item item = (Item) MCApiRuntime.lookupRegistry("item", id);
        if (item == null) {
            throw new LuaError("[MLua] Unknown item: " + id);
        }
        return MLuaItemWrapper.itemTable(new ItemStack(item, Math.max(0, count)), context.registration());
    }

    private ItemStack stack(LuaValue value) {
        return MLuaItemWrapper.toItemStack(value, context.mlua().registration());
    }

    private static String id(ItemStack stack) {
        var id = MCApiRuntime.registryKey("item", stack.getItem());
        return id == null ? "minecraft:air" : id;
    }
}
