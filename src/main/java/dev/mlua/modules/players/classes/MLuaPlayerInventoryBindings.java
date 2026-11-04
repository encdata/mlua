package dev.mlua.modules.players.classes;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.mc.impl.container.MCAbstractContainerMenuImpl;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mcapi.container.MCAbstractContainerMenu;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.module.MLuaRegistration;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** Binds player inventory, ender chest, and the current open container as Lua class instances. */
final class MLuaPlayerInventoryBindings {
    private MLuaPlayerInventoryBindings() { }

    static LuaTable attach(LuaTable playerTable, MCPlayer player, boolean writable,
                           boolean includeMutationMethods, MLuaRegistration registration) {
        Player rawPlayer = (Player) ((MCEntityImpl) player).inner();
        Inventory rawInventory = rawPlayer.getInventory();
        dev.mlua.mcapi.container.MCContainer inventoryContainer = MCContainerImpl.fromReal(rawInventory);
        LuaTable inventory = MLuaContainer.inventoryTable(inventoryContainer, writable, registration,
            new MLuaContainer.InventoryLayout(Math.min(9, Math.max(0, rawInventory.getContainerSize() - 5)),
                rawInventory.getContainerSize() - 4, 4, rawInventory.getContainerSize()));
        LuaTable enderChest = MLuaContainer.containerTable(
            MCContainerImpl.fromReal(rawPlayer.getEnderChestInventory()), writable, registration, "Ender Chest");
        Map<Object, WeakReference<LuaTable>> openContainerInstances = new WeakHashMap<>();

        MLuaClassMethods.setInstanceMethod(playerTable, "GetInventory", MLuaPlayerWrapper.luaFn(() -> inventory));
        MLuaClassMethods.setInstanceMethod(playerTable, "GetSlot", MLuaPlayerWrapper.varArg(args -> {
            int slot = MLuaLuaArguments.wholeInt(args, 2, "Player:GetSlot", "slot");
            int rawIndex = slot - 1;
            if (rawIndex < 0 || rawIndex >= rawInventory.getContainerSize()) return LuaValue.NIL;
            return containerSlot(inventory, slot);
        }));
        MLuaClassMethods.setInstanceMethod(playerTable, "GetInventorySize",
            MLuaPlayerWrapper.luaFn(() -> LuaValue.valueOf(rawInventory.getContainerSize())));
        MLuaClassMethods.setInstanceMethod(playerTable, "GetHotbar",
            MLuaPlayerWrapper.luaFn(() -> MLuaClassMethods.invokeInstanceMethod(inventory, "GetHotbar")));
        MLuaClassMethods.setInstanceMethod(playerTable, "GetArmorContents",
            MLuaPlayerWrapper.luaFn(() -> MLuaClassMethods.invokeInstanceMethod(inventory, "GetArmorContents")));
        MLuaClassMethods.setInstanceMethod(playerTable, "GetOffhandSlot",
            MLuaPlayerWrapper.luaFn(() -> MLuaClassMethods.invokeInstanceMethod(inventory, "GetOffhandSlot")));
        MLuaClassMethods.setInstanceMethod(playerTable, "GetEnderChest",
            MLuaPlayerWrapper.luaFn(() -> enderChest));
        MLuaClassMethods.setInstanceMethod(playerTable, "GetOpenContainer",
            MLuaPlayerWrapper.luaFn(() -> openContainerInstance(openContainerInstances,
                rawPlayer.containerMenu, MCAbstractContainerMenuImpl.fromReal(rawPlayer.containerMenu), rawPlayer,
                writable, registration)));
        MLuaClassMethods.setInstanceMethod(playerTable, "GetOpenContainerTitle",
            MLuaPlayerWrapper.luaFn(() -> LuaValue.valueOf(containerTitle(
                MCAbstractContainerMenuImpl.fromReal(rawPlayer.containerMenu)))));
        MLuaClassMethods.setInstanceMethod(playerTable, "GetOpenContainerSize",
            MLuaPlayerWrapper.luaFn(() -> LuaValue.valueOf(rawPlayer.containerMenu.slots.size())));
        MLuaClassMethods.setInstanceMethod(playerTable, "GetIsLocalPlayer",
            MLuaPlayerWrapper.luaFn(() -> LuaValue.valueOf(playerTable.rawget("__is_local_player").toboolean())));
        if (includeMutationMethods) {
            MLuaClassMethods.setInstanceMethod(playerTable, "ClearInventory", MLuaPlayerWrapper.luaFn(() -> {
                rawInventory.clearContent();
                return LuaValue.NONE;
            }));
            MLuaClassMethods.setInstanceMethod(playerTable, "SetInventory", MLuaPlayerWrapper.varArg(args -> {
                MLuaClassMethods.invokeInstanceMethod(inventory, "SetContents", args.arg(2));
                return LuaValue.NONE;
            }));
        }
        return inventory;
    }

    private static LuaValue containerSlot(LuaTable inventory, int index) {
        return MLuaClassMethods.invokeInstanceMethod(inventory, "GetSlot", LuaValue.valueOf(index));
    }

    private static LuaTable openContainerInstance(Map<Object, WeakReference<LuaTable>> cache, Object menuKey,
                                                   MCAbstractContainerMenu menu, Player owner, boolean writable,
                                                   MLuaRegistration registration) {
        synchronized (cache) {
            WeakReference<LuaTable> reference = cache.get(menuKey);
            LuaTable instance = reference == null ? null : reference.get();
            if (instance != null) return instance;
            instance = MLuaContainer.containerTable(menu, MCComponent.literal(containerTitle(menu)), writable,
                registration, () -> owner.containerMenu == menuKey && !owner.isRemoved());
            cache.put(menuKey, new WeakReference<>(instance));
            return instance;
        }
    }

    private static String containerTitle(MCAbstractContainerMenu menu) {
        try {
            Object type = menu.getType();
            if (type != null) return type.toString();
        } catch (RuntimeException ignored) {
            // Some Minecraft versions do not expose a usable registered type for the inventory menu.
        }
        return menu.getClass().getSimpleName();
    }
}
