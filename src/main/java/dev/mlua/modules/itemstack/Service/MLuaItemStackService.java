package dev.mlua.modules.itemstack.Service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.api.MLuaDescription;
@MLuaDescription("Create, clone, inspect, and mutate Minecraft ItemStack values.")
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

    @MLuaDescription("Creates an item stack by registry identifier and optional whole-number count, defaulting to one.")
    public LuaValue Create(String id, Optional<LuaValue> count) {
        return create(id, MLuaLuaArguments.optionalWholeInt(
            count.orElse(LuaValue.NIL), 1, "ItemStackService:Create", "count"));
    }

    @MLuaDescription("Creates an item stack by registry identifier and optional whole-number count, defaulting to one.")
    public LuaValue Of(String id, Optional<LuaValue> count) {
        return create(id, MLuaLuaArguments.optionalWholeInt(
            count.orElse(LuaValue.NIL), 1, "ItemStackService:Of", "count"));
    }

    @MLuaDescription("Returns an empty item stack instance. Since a stack with no item type cannot hold Minecraft item components, NBT assigned to this instance is isolated to the Lua Item wrapper and is not serialized.")
    public LuaValue Empty() {
        return MLuaItemWrapper.itemTable(ItemStack.EMPTY, this.context.registration());
    }

    @MLuaDescription("Returns a copy of an item stack.")
    public LuaValue Clone(LuaValue item) {
        return MLuaItemWrapper.cloneItem(item, this.context.registration());
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

    @MLuaDescription("Sets the stack count to a whole number of zero or greater.")
    public void SetCount(LuaValue item, LuaValue count) {
        int wholeCount = MLuaLuaArguments.wholeInt(count, "ItemStackService:SetCount", "count");
        MLuaItemWrapper.setCount(item, wholeCount, context.mlua().registration());
    }

    @MLuaDescription("Returns an item stack's NBT data or one value by key.")
    public LuaValue GetNbt(LuaValue item, Optional<String> key) {
        return MLuaItemWrapper.getNbt(item, key, this.context.registration());
    }

    @MLuaDescription("Sets one NBT value in an item stack's NBT data.")
    public void SetNbt(LuaValue item, String key, LuaValue value) {
        MLuaItemWrapper.setNbt(item, key, value, this.context.registration());
    }

    private LuaValue create(String id, int count) {
        if (count < 0) throw new LuaError("[MLua] ItemStackService count must be zero or greater.");
        Item item = (Item) MCApiRuntime.lookupRegistry("item", id);
        if (item == null) {
            throw new LuaError("[MLua] Unknown item: " + id);
        }
        return MLuaItemWrapper.itemTable(new ItemStack(item, count), context.registration());
    }

    private ItemStack stack(LuaValue value) {
        return MLuaItemWrapper.toItemStack(value, context.mlua().registration());
    }

    private static String id(ItemStack stack) {
        var id = MCApiRuntime.registryKey("item", stack.getItem());
        return id == null ? "minecraft:air" : id;
    }
}
