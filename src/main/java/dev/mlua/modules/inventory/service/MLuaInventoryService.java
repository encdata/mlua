package dev.mlua.modules.inventory.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.modules.players.MLuaPlayerResolver;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Access player inventories and common container operations from Lua.")
public final class MLuaInventoryService extends MLuaServerThreadReflectiveService {
    public MLuaInventoryService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "InventoryService";
    }

    @MLuaDescription("Returns the same live Inventory instance exposed by Player.Inventory.")
    public LuaTable GetPlayerInventory(LuaValue playerValue) {
        return playerInventory(player(playerValue));
    }

    @MLuaDescription("Returns the same live ender chest Container exposed by Player.EnderChest.")
    public LuaTable GetEnderChest(LuaValue playerValue) {
        return enderChest(player(playerValue));
    }

    @MLuaDescription("Returns the player's current menu as its live Container instance. With no external menu active, Minecraft exposes the player's inventory menu.")
    public LuaValue GetOpenContainer(LuaValue playerValue) {
        LuaTable playerInstance = playerInstance(player(playerValue));
        LuaValue result = MLuaClassMethods.invokeInstanceMethod(playerInstance, "GetOpenContainer");
        if (result.isnil()) return LuaValue.NIL;
        if (!result.istable()) throw new LuaError("[MLua] Player:GetOpenContainer returned an invalid container value.");
        return result;
    }

    @MLuaDescription("Returns a live slot from the player's current menu, or nil when the index is outside it.")
    public LuaValue GetOpenContainerSlot(LuaValue playerValue, int slotIndex) {
        LuaValue containerValue = GetOpenContainer(playerValue);
        if (!containerValue.istable()) return LuaValue.NIL;
        return MLuaClassMethods.invokeInstanceMethod(containerValue.checktable(), "GetSlot", LuaValue.valueOf(slotIndex));
    }

    @MLuaDescription("Returns the live slots in the player's current menu, including its player-inventory slots when Minecraft provides them.")
    public LuaTable GetOpenContainerSlots(LuaValue playerValue) {
        LuaValue containerValue = GetOpenContainer(playerValue);
        if (!containerValue.istable()) return new LuaTable();
        return MLuaClassMethods.invokeInstanceMethod(containerValue.checktable(), "GetAll").checktable();
    }

    @MLuaDescription("Returns the same live Slot exposed by Player.Inventory:GetSlot(index).")
    public LuaValue GetSlot(LuaValue playerValue, int slotIndex) {
        LuaTable inventory = playerInventory(player(playerValue));
        return MLuaClassMethods.invokeInstanceMethod(inventory, "GetSlot", LuaValue.valueOf(slotIndex));
    }

    @MLuaDescription("Sets an item in the same live Slot exposed by Player.Inventory:GetSlot(index).")
    public boolean SetSlot(LuaValue playerValue, int slotIndex, LuaValue itemValue) {
        this.requireServerSide(this.name());
        LuaTable inventory = playerInventory(player(playerValue));
        LuaValue slotInstance = MLuaClassMethods.invokeInstanceMethod(inventory, "GetSlot", LuaValue.valueOf(slotIndex));
        if (!slotInstance.istable()) return false;
        MLuaClassMethods.invokeInstanceMethod(slotInstance.checktable(), "SetItem", itemValue);
        return true;
    }

    @MLuaDescription("Adds an item stack to a player's inventory.")
    public boolean AddItem(LuaValue playerValue, LuaValue itemValue) {
        this.requireServerSide(this.name());
        return player(playerValue).getInventory().add(MLuaItemWrapper.toItemStack(itemValue, context.mlua().registration()));
    }

    @MLuaDescription("Counts matching item stacks across a player's inventory. The item can be a registered id or an Item instance.")
    public int CountItem(LuaValue playerValue, LuaValue item) {
        LuaTable inventory = playerInventory(player(playerValue));
        return MLuaClassMethods.invokeInstanceMethod(inventory, "CountItem", item).checkint();
    }

    @MLuaDescription("Returns whether a player's inventory contains at least the requested item count. The item can be a registered id or an Item instance.")
    public boolean ContainsItem(LuaValue playerValue, LuaValue item, Optional<Integer> count) {
        LuaTable inventory = playerInventory(player(playerValue));
        return MLuaClassMethods.invokeInstanceMethod(inventory, "ContainsItem", item,
            LuaValue.valueOf(count.orElse(1))).checkboolean();
    }

    @MLuaDescription("Removes up to the requested item count from a player's inventory and returns the amount removed. The item can be a registered id or an Item instance.")
    public int RemoveItem(LuaValue playerValue, LuaValue item, int count) {
        this.requireServerSide(this.name());
        LuaTable inventory = playerInventory(player(playerValue));
        return MLuaClassMethods.invokeInstanceMethod(inventory, "RemoveItem", item,
            LuaValue.valueOf(count)).checkint();
    }

    @MLuaDescription("Removes all items from a player's inventory.")
    public void Clear(LuaValue playerValue) {
        this.requireServerSide(this.name());
        MLuaClassMethods.invokeInstanceMethod(playerInventory(player(playerValue)), "Clear");
    }

    private Player player(LuaValue value) {
        return MLuaPlayerResolver.resolve(value, this.context, this.name());
    }

    private LuaTable playerInventory(Player player) {
        LuaTable playerInstance = playerInstance(player);
        LuaValue inventory = MLuaClassMethods.invokeInstanceMethod(playerInstance, "GetInventory");
        if (!inventory.istable()) {
            throw new LuaError("[MLua] Player instance did not provide its live Inventory class.");
        }
        return inventory.checktable();
    }

    private LuaTable enderChest(Player player) {
        LuaTable playerInstance = playerInstance(player);
        LuaValue enderChest = MLuaClassMethods.invokeInstanceMethod(playerInstance, "GetEnderChest");
        if (!enderChest.istable()) {
            throw new LuaError("[MLua] Player instance did not provide its live EnderChest container.");
        }
        return enderChest.checktable();
    }

    private LuaTable playerInstance(Player player) {
        return MLuaPlayerWrapper.playerEntityTable(player, this.context.registration());
    }

}
