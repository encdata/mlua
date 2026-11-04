package dev.mlua.modules.inventory;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.MLuaContextView;
import dev.mlua.modules.inventory.service.MLuaInventoryService;
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
            .reflective(MLuaInventoryService.class);
        return List.of(inventory.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("InventoryService", "MLuaInventoryService",
            "Access player inventories and common container operations from Lua.",
            "services/inventory.html"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:GetPlayerInventory(player: Player)", "Inventory"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:GetEnderChest(player: Player)", "Container"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:GetOpenContainer(player: Player)", "Container|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:GetOpenContainerSlot(player: Player, index: integer)", "Slot|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:GetOpenContainerSlots(player: Player)", "Slot[]"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:GetSlot(player: Player, index: integer)", "Slot?"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:SetSlot(player: Player, index: integer, item: Item?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:AddItem(player: Player, item: Item)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:CountItem(player: Player, item: string|Item)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:ContainsItem(player: Player, item: string|Item, count: integer?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:RemoveItem(player: Player, item: string|Item, count: integer)", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("InventoryService", "InventoryService:Clear(player: Player)", "void"));
        docs.register(MLuaDocBuilder.Registration.clazz("Container", "MLuaContainer", "A live container instance that exposes its current slots.", "classes/container.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:GetSize(): number", "Returns the number of slots in this container.", "", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:IsEmpty(): boolean", "Returns whether this container has no items.", "", "boolean", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:Clear()", "Removes all items from this writable container.", "", "void", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:GetSlot(index: integer): Slot|nil", "Returns the live slot at a one-based whole-number index.", "index: one-based slot index", "Slot|nil", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:SetSlot(index: integer, item: Item|nil)", "Sets the item in a writable slot by one-based whole-number index. Raises a Lua error when Minecraft rejects the item for that slot.", "index: one-based slot index; item: item stack or nil to clear", "void", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:GetTitle(): string|nil", "Returns this container's display title when available.", "", "string|nil", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:GetAll(): Slot[]", "Returns all live slots in this container.", "", "Slot[]", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:GetFirstEmptySlot(): Slot|nil", "Returns the first empty live slot, or nil when the container is full.", "", "Slot|nil", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:CountItem(item: string|Item): number", "Returns the total stack count for an item across this container.", "item: registered item identifier or Item instance", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:FindSlots(item: string|Item): Slot[]", "Returns the live slots that currently contain this item.", "item: registered item identifier or Item instance", "Slot[]", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:ContainsItem(item: string|Item, count: integer?): boolean", "Returns whether this container has at least the requested total count.", "item: registered item identifier or Item instance; count: positive whole number, default 1", "boolean", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Container", "Container:RemoveItem(item: string|Item, count: integer): number", "Removes up to count items from a writable container and returns the amount removed.", "item: registered item identifier or Item instance; count: positive whole number", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.property("Container", "container.FirstEmptySlot", "Slot|nil", "The first empty live slot, or nil when full.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Container", "Read slots through a live container",
            "Container and Inventory expose live slots. Lua slot indexes start at one, and each Slot reads the current backing inventory.",
            "local inventory = player.Inventory\nlocal first = inventory:GetSlot(1)\nif first and not first.Empty then\n    print(first.Item:GetDisplayName())\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Container", "Check and consume an item stack",
            "CountItem and ContainsItem include matching stacks across the whole container. RemoveItem consumes up to the requested amount and reports how many it removed.",
            "local inventory = player.Inventory\nlocal arrowId = \"minecraft:arrow\"\nif inventory:ContainsItem(arrowId, 3) then\n    local removed = inventory:RemoveItem(arrowId, 3)\n    print(\"Arrows used:\", removed)\nend\n\nlocal freeSlot = inventory.FirstEmptySlot\nif freeSlot then\n    print(\"Free slot:\", freeSlot.Index)\nend"));
        docs.register(MLuaDocBuilder.Registration.clazz("Inventory", "MLuaContainer", "A live player inventory instance with indexed slots and item operations.", "classes/inventory.html", "Container"));
        docs.register(MLuaDocBuilder.Registration.method("Inventory", "Inventory:GetHotbar(): Slot[]", "Returns the same live slot instances as the first nine inventory slots.", "", "Slot[]", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Inventory", "Inventory:GetArmorContents(): Slot[]", "Returns the same live slot instances as the inventory's armor positions.", "", "Slot[]", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Inventory", "Inventory:GetOffhandSlot(): Slot|nil", "Returns the same live slot instance as the inventory's offhand position.", "", "Slot|nil", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Inventory", "Inventory:SetHotbarSlot(index: integer, item: Item|nil)", "Sets or clears a hotbar position by one-based whole-number index.", "index: 1-9; item: item stack or nil to clear", "void", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Inventory", "Inventory:SetArmorSlot(index: integer, item: Item|nil)", "Sets or clears an armor position by one-based whole-number index.", "index: 1-4; item: item stack or nil to clear", "void", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Inventory", "Inventory:SetOffhandSlot(item: Item|nil)", "Sets or clears the offhand position.", "item: item stack or nil to clear", "void", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.guideStep("Inventory", "Use the inventory's named slot groups",
            "The hotbar, armor, and offhand accessors return the same cached Slot instances as GetSlot, so changes and addon extensions are visible through either path.",
            "local inventory = player.Inventory\nlocal armorSlot = inventory.Armor[1]\nlocal offhand = inventory.Offhand\nprint(inventory.Hotbar[1], armorSlot, offhand)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("InventoryService", "Work with the open menu",
            "GetOpenContainer returns the same live Container exposed by Player:GetOpenContainer. Its slots are the actual menu slots, including the container and player inventory sections. Use the slot accessors when a menu interaction depends on the screen the player currently has open.",
            "local Inventory = Minecraft:GetService(\"InventoryService\")\nlocal menu = Inventory:GetOpenContainer(player)\nif menu then\n    print(menu.Title, menu.Size)\n    for _, slot in ipairs(Inventory:GetOpenContainerSlots(player)) do\n        if not slot.Empty then\n            print(slot.Index, slot.ItemId, slot.Count)\n        end\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.clazz("Slot", "MLuaSlot", "A live slot instance that reads or updates the item currently in that slot.", "classes/slot.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetItem(): Item|nil", "Returns the live item stack currently in this slot, or nil when empty.", "", "Item|nil", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetIndex(): number", "Returns this slot's one-based container index.", "", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetRawIndex(): number", "Returns this slot's zero-based Minecraft index.", "", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:IsEmpty(): boolean", "Returns whether this slot contains no item.", "", "boolean", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetCount(): number", "Returns the number of items in this slot.", "", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetMaxStackSize(): number", "Returns the maximum stack size for this slot's item.", "", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetMaxCount(): number", "Compatibility alias for GetMaxStackSize.", "", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetDisplayName(): string|nil", "Returns this slot item's display name, or nil when empty.", "", "string|nil", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetItemId(): string|nil", "Returns this slot item's registered identifier, or nil when empty.", "", "string|nil", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetLore(): string[]", "Returns this slot item's lore lines.", "", "string[]", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetEnchantments(): table", "Returns a map from enchantment id to level for this slot's item.", "", "table", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetEnchantmentLevel(id: string): number", "Returns this slot item's level for an enchantment id, or zero when empty or not enchanted with it.", "id: enchantment identifier", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:HasEnchantment(id: string): boolean", "Returns whether this slot's item has the given enchantment.", "id: enchantment identifier", "boolean", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:AddEnchantment(id: string, level: integer?): boolean", "Adds an enchantment to this slot's item when writable; returns false when empty.", "id: enchantment identifier; level: positive whole-number level, default 1", "boolean", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:RemoveEnchantment(id: string): boolean", "Removes an enchantment from this slot's item when writable; returns false when empty.", "id: enchantment identifier", "boolean", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetDamage(): number", "Returns this slot item's current durability damage.", "", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetMaxDamage(): number", "Returns this slot item's maximum durability damage.", "", "number", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:HasTag(): boolean", "Returns whether this slot's item has a data tag.", "", "boolean", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:HasTagKey(key: string): boolean", "Returns whether this slot's item data has the given key.", "key: tag key", "boolean", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:GetTag(key: string): any", "Returns the value for a key in this slot item's data tag.", "key: tag key", "any", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:SetItem(item: Item|nil)", "Sets the item in this writable slot. Raises a Lua error when Minecraft rejects the item for this slot.", "item: item stack or nil to clear", "void", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Slot", "Slot:Clear()", "Removes the item from this writable slot.", "", "void", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.guideStep("Slot", "Inspect and update one slot",
            "A Slot provides the current Item and stack details. Assign a new Item to replace its contents, or nil to clear a writable slot.",
            "local slot = player.Inventory:GetSlot(1)\nif slot then\n    print(slot.Index, slot.Count, slot.Item)\n    slot.Item = Item.Find(\"minecraft:apple\", 3)\nend"));
    }
}
