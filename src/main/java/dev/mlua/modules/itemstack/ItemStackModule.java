package dev.mlua.modules.itemstack;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.itemstack.Service.MLuaItemStackService;
import java.util.List;

public final class ItemStackModule implements MLuaModule {
    @Override
    public List<String> dependencies() {
        return List.of("registry");
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaItemWrapper.classTable(registration);
    }

    @Override
    public String id() {
        return "itemstack";
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder itemStack = ServiceEntry.builder("ItemStackService", MLuaItemStackService::new)
            .description("Create, clone, inspect, and mutate Minecraft ItemStack values.");
        itemStack.function("Create", "Creates an item stack by registry identifier and count.")
            .function("Of", "Creates an item stack by registry identifier and count.")
            .function("Empty", "Returns an empty item stack instance.")
            .function("Clone", "Returns a copy of an item stack.")
            .function("IsEmpty", "Returns whether an item stack is empty.")
            .function("GetId", "Returns an item stack's registered item identifier.")
            .function("GetCount", "Returns the number of items in a stack.")
            .function("SetCount", "Sets the number of items in a stack.")
            .function("GetNbt", "Returns an item stack's NBT data or one value by key.")
            .function("SetNbt", "Sets one NBT value or replaces an item stack's NBT data.");
        return List.of(itemStack.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("ItemStackService", "MLuaItemStackService",
            "Create, clone, inspect, and mutate Minecraft ItemStack values.",
            "services/item-stack.html");
        docs.clazz("Item", "MLuaItemWrapper", "A live item stack instance with count, durability, display name, tags, and enchantments.", "classes/item.html", "Instance");
        docs.guideStep("Item", "Read and change a live item stack",
            "Item values are live stack wrappers returned by player inventory and held-item methods. Use Item.Find to create a stack when you need one directly.",
            "local item = Item.Find(\"minecraft:torch\", 4)\nprint(item:GetId(), item:GetCount())\nitem:SetCount(8)");
    }
}
