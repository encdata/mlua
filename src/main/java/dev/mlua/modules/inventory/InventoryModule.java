package dev.mlua.modules.inventory;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.MLuaContextView;
import dev.mlua.modules.inventory.Service.MLuaInventoryService;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.minecraft.binding.MLuaSlot;
import java.util.List;
import org.luaj.vm2.Globals;

public final class InventoryModule implements MLuaModule {
    @Override
    public List<String> dependencies() {
        return List.of("core-classes", "registry");
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("Container", registration.requireInstanceBaseClassName(), "A live container instance that exposes its current slots.");
        registration.registerClass("Inventory", "Container", "A live player inventory instance with indexed slots and item operations.");
        registration.registerClass("Slot", registration.requireInstanceBaseClassName(), "A live slot instance that reads or updates the item currently in that slot.");
        MLuaContainer.registerClassMethods(registration);
        MLuaSlot.registerClassMethods(registration);
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        var registration = context.registration();
        globals.set("Container", registration.classTable("Container"));
        globals.set("Inventory", registration.classTable("Inventory"));
        globals.set("Slot", registration.classTable("Slot"));
    }

    @Override
    public String id() {
        return "inventory";
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder inventory = ServiceEntry.builder("InventoryService", MLuaInventoryService::new)
            .description("Access player inventories and common container operations from Lua.");
        inventory.function("GetPlayerInventory", "Returns the same live Inventory instance exposed by Player.Inventory.")
            .function("GetEnderChest", "Returns the same live ender chest Container exposed by Player.EnderChest.")
            .function("GetSlot", "Returns the same live Slot exposed by Player.Inventory:GetSlot(index).")
            .function("SetSlot", "Sets an item in the same live Slot exposed by Player.Inventory:GetSlot(index).")
            .function("AddItem", "Adds an item stack to a player's inventory.")
            .function("Clear", "Removes all items from a player's inventory.");
        return List.of(inventory.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("InventoryService", "MLuaInventoryService",
            "Access player inventories and common container operations from Lua.",
            "services/inventory.html");
        docs.clazz("Container", "MLuaContainer", "A live container instance that exposes its current slots.", "classes/container.html", "Instance");
        docs.method("Container", "Container:GetSize(): number", "Returns the number of slots in this container.", "", "number", "Shared", "");
        docs.method("Container", "Container:IsEmpty(): boolean", "Returns whether this container has no items.", "", "boolean", "Shared", "");
        docs.method("Container", "Container:Clear()", "Removes all items from this writable container.", "", "void", "Shared", "");
        docs.method("Container", "Container:GetSlot(index: number): Slot|nil", "Returns the live slot at a one-based index.", "index: one-based slot index", "Slot|nil", "Shared", "");
        docs.method("Container", "Container:SetSlot(index: number, item: Item|nil)", "Sets the item in a writable slot by one-based index.", "index: one-based slot index; item: item stack or nil to clear", "void", "Shared", "");
        docs.method("Container", "Container:GetTitle(): string|nil", "Returns this container's display title when available.", "", "string|nil", "Shared", "");
        docs.method("Container", "Container:GetAll(): Slot[]", "Returns all live slots in this container.", "", "Slot[]", "Shared", "");
        docs.guideStep("Container", "Read slots through a live container",
            "Container and Inventory expose live slots. Lua slot indexes start at one, and each Slot reads the current backing inventory.",
            "local inventory = player.Inventory\nlocal first = inventory:GetSlot(1)\nif first and not first.Empty then\n    print(first.Item:GetDisplayName())\nend");
        docs.clazz("Inventory", "MLuaContainer", "A live player inventory instance with indexed slots and item operations.", "classes/inventory.html", "Container");
        docs.method("Inventory", "Inventory:GetHotbar(): Slot[]", "Returns the same live slot instances as the first nine inventory slots.", "", "Slot[]", "Shared", "");
        docs.method("Inventory", "Inventory:GetArmorContents(): Slot[]", "Returns the same live slot instances as the inventory's armor positions.", "", "Slot[]", "Shared", "");
        docs.method("Inventory", "Inventory:GetOffhandSlot(): Slot|nil", "Returns the same live slot instance as the inventory's offhand position.", "", "Slot|nil", "Shared", "");
        docs.method("Inventory", "Inventory:SetHotbarSlot(index: number, item: Item|nil)", "Sets or clears a hotbar position by one-based index.", "index: 1-9; item: item stack or nil to clear", "void", "Shared", "");
        docs.method("Inventory", "Inventory:SetArmorSlot(index: number, item: Item|nil)", "Sets or clears an armor position by one-based index.", "index: 1-4; item: item stack or nil to clear", "void", "Shared", "");
        docs.method("Inventory", "Inventory:SetOffhandSlot(item: Item|nil)", "Sets or clears the offhand position.", "item: item stack or nil to clear", "void", "Shared", "");
        docs.guideStep("Inventory", "Use the inventory's named slot groups",
            "The hotbar, armor, and offhand accessors return the same cached Slot instances as GetSlot, so changes and addon extensions are visible through either path.",
            "local inventory = player.Inventory\nlocal armorSlot = inventory.Armor[1]\nlocal offhand = inventory.Offhand\nprint(inventory.Hotbar[1], armorSlot, offhand)");
        docs.clazz("Slot", "MLuaSlot", "A live slot instance that reads or updates the item currently in that slot.", "classes/slot.html", "Instance");
        docs.method("Slot", "Slot:GetItem(): Item|nil", "Returns the live item stack currently in this slot, or nil when empty.", "", "Item|nil", "Shared", "");
        docs.method("Slot", "Slot:GetIndex(): number", "Returns this slot's one-based container index.", "", "number", "Shared", "");
        docs.method("Slot", "Slot:GetRawIndex(): number", "Returns this slot's zero-based Minecraft index.", "", "number", "Shared", "");
        docs.method("Slot", "Slot:IsEmpty(): boolean", "Returns whether this slot contains no item.", "", "boolean", "Shared", "");
        docs.method("Slot", "Slot:GetCount(): number", "Returns the number of items in this slot.", "", "number", "Shared", "");
        docs.method("Slot", "Slot:GetMaxStackSize(): number", "Returns the maximum stack size for this slot's item.", "", "number", "Shared", "");
        docs.method("Slot", "Slot:GetMaxCount(): number", "Compatibility alias for GetMaxStackSize.", "", "number", "Shared", "");
        docs.method("Slot", "Slot:GetDisplayName(): string|nil", "Returns this slot item's display name, or nil when empty.", "", "string|nil", "Shared", "");
        docs.method("Slot", "Slot:GetItemId(): string|nil", "Returns this slot item's registered identifier, or nil when empty.", "", "string|nil", "Shared", "");
        docs.method("Slot", "Slot:GetLore(): string[]", "Returns this slot item's lore lines.", "", "string[]", "Shared", "");
        docs.method("Slot", "Slot:GetEnchantments(): any", "Returns this slot item's enchantments.", "", "any", "Shared", "");
        docs.method("Slot", "Slot:GetEnchantmentLevel(id: string): number", "Returns this slot item's level for an enchantment id, or zero when empty or not enchanted with it.", "id: enchantment identifier", "number", "Shared", "");
        docs.method("Slot", "Slot:AddEnchantment(id: string, level: number)", "Adds an enchantment to this slot's item when writable.", "id: enchantment identifier; level: enchantment level", "void", "Shared", "");
        docs.method("Slot", "Slot:RemoveEnchantment(id: string)", "Removes an enchantment from this slot's item when writable.", "id: enchantment identifier", "void", "Shared", "");
        docs.method("Slot", "Slot:GetDamage(): number", "Returns this slot item's current durability damage.", "", "number", "Shared", "");
        docs.method("Slot", "Slot:GetMaxDamage(): number", "Returns this slot item's maximum durability damage.", "", "number", "Shared", "");
        docs.method("Slot", "Slot:HasTag(): boolean", "Returns whether this slot's item has a data tag.", "", "boolean", "Shared", "");
        docs.method("Slot", "Slot:HasTagKey(key: string): boolean", "Returns whether this slot's item data has the given key.", "key: tag key", "boolean", "Shared", "");
        docs.method("Slot", "Slot:GetTag(key: string): any", "Returns the value for a key in this slot item's data tag.", "key: tag key", "any", "Shared", "");
        docs.method("Slot", "Slot:SetItem(item: Item|nil)", "Sets the item in this writable slot.", "item: item stack or nil to clear", "void", "Shared", "");
        docs.method("Slot", "Slot:Clear()", "Removes the item from this writable slot.", "", "void", "Shared", "");
        docs.guideStep("Slot", "Inspect and update one slot",
            "A Slot provides the current Item and stack details. Assign a new Item to replace its contents, or nil to clear a writable slot.",
            "local slot = player.Inventory:GetSlot(1)\nif slot then\n    print(slot.Index, slot.Count, slot.Item)\n    slot.Item = Item.Find(\"minecraft:apple\", 3)\nend");
    }
}
