package dev.mlua.modules.inventory.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.mc.impl.container.MCContainerImpl;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Access player inventories and common container operations from Lua.")
public final class MLuaInventoryService extends MLuaReflectiveService {
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

    @MLuaDescription("Returns the same live Slot exposed by Player.Inventory:GetSlot(index).")
    public LuaValue GetSlot(LuaValue playerValue, int slotIndex) {
        LuaTable inventory = playerInventory(player(playerValue));
        return invokeInstanceMethod(inventory, "GetSlot", LuaValue.valueOf(slotIndex));
    }

    @MLuaDescription("Sets an item in the same live Slot exposed by Player.Inventory:GetSlot(index).")
    public boolean SetSlot(LuaValue playerValue, int slotIndex, LuaValue itemValue) {
        ensureServer();
        LuaTable inventory = playerInventory(player(playerValue));
        LuaValue slotInstance = invokeInstanceMethod(inventory, "GetSlot", LuaValue.valueOf(slotIndex));
        if (!slotInstance.istable()) return false;
        invokeInstanceMethod(slotInstance.checktable(), "SetItem", itemValue);
        return true;
    }

    @MLuaDescription("Adds an item stack to a player's inventory.")
    public boolean AddItem(LuaValue playerValue, LuaValue itemValue) {
        ensureServer();
        return player(playerValue).getInventory().add(MLuaItemWrapper.toItemStack(itemValue, context.mlua().registration()));
    }

    @MLuaDescription("Counts matching item stacks across a player's inventory.")
    public int CountItem(LuaValue playerValue, String itemId) {
        LuaTable inventory = playerInventory(player(playerValue));
        return invokeInstanceMethod(inventory, "CountItem", LuaValue.valueOf(itemId)).checkint();
    }

    @MLuaDescription("Returns whether a player's inventory contains at least the requested item count.")
    public boolean ContainsItem(LuaValue playerValue, String itemId, Optional<Integer> count) {
        LuaTable inventory = playerInventory(player(playerValue));
        return invokeInstanceMethod(inventory, "ContainsItem", LuaValue.valueOf(itemId),
            LuaValue.valueOf(count.orElse(1))).checkboolean();
    }

    @MLuaDescription("Removes up to the requested item count from a player's inventory and returns the amount removed.")
    public int RemoveItem(LuaValue playerValue, String itemId, int count) {
        ensureServer();
        LuaTable inventory = playerInventory(player(playerValue));
        return invokeInstanceMethod(inventory, "RemoveItem", LuaValue.valueOf(itemId),
            LuaValue.valueOf(count)).checkint();
    }

    @MLuaDescription("Removes all items from a player's inventory.")
    public void Clear(LuaValue playerValue) {
        ensureServer();
        invokeInstanceMethod(playerInventory(player(playerValue)), "Clear");
    }

    private Player player(LuaValue value) {
        if (value.istable()) {
            return (Player) MLuaEntityWrapper.requireLuaInstance(value, this.context.registration(), "Player", "Player");
        }
        if (value.isstring() && this.context.server() != null) {
            String text = value.tojstring();
            ServerPlayer byName = this.context.server().getPlayerList().getPlayerByName(text);
            if (byName != null) return byName;
            try {
                ServerPlayer byUuid = this.context.server().getPlayerList().getPlayer(UUID.fromString(text));
                if (byUuid != null) return byUuid;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (value.isnil() && this.context.side() == Side.CLIENT) {
            Player local = localPlayer();
            if (local != null) return local;
        }
        throw new LuaError("[MLua] Expected a Player instance, player name, or player UUID.");
    }

    private static Player localPlayer() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            Object player = minecraft.getField("player").get(instance);
            return player instanceof Player local ? local : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private LuaTable playerInventory(Player player) {
        var inventory = player.getInventory();
        return MLuaContainer.inventoryTable(MCContainerImpl.fromReal(inventory),
            this.context.side() == Side.SERVER, this.context.registration(),
            new MLuaContainer.InventoryLayout(Math.min(9, Math.max(0, inventory.getContainerSize() - 5)),
                inventory.getContainerSize() - 4, 4, inventory.getContainerSize()));
    }

    private LuaTable enderChest(Player player) {
        return MLuaContainer.containerTable(MCContainerImpl.fromReal(player.getEnderChestInventory()),
            this.context.side() == Side.SERVER, this.context.registration(), "Ender Chest");
    }

    private static LuaValue invokeInstanceMethod(LuaTable instance, String name, LuaValue... methodArguments) {
        LuaValue method = instance.get(name);
        if (!method.isfunction()) {
            throw new LuaError("[MLua] Registered class instance is missing method '" + name + "'.");
        }
        LuaValue[] arguments = new LuaValue[methodArguments.length + 1];
        arguments[0] = instance;
        System.arraycopy(methodArguments, 0, arguments, 1, methodArguments.length);
        return method.invoke(LuaValue.varargsOf(arguments)).arg1();
    }

    private void ensureServer() {
        if (this.context.side() != Side.SERVER) {
            throw new LuaError("[MLua] Inventory mutation is server-only.");
        }
    }
}
