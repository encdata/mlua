package dev.mlua.mcapi.container;

import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.item.MCItemStack;

import java.util.List;

public interface MCContainer {
    int getContainerSize();
    /** Maximum item count accepted by this container before the item-specific limit is applied. */
    default int getMaxStackSize() { return 64; }
    MCItemStack getItem(int slot);
    /** Whether the backing Minecraft container accepts this stack at the given zero-based slot. */
    default boolean canPlaceItem(int slot, MCItemStack stack) { return true; }
    void setItem(int slot, MCItemStack stack);
    void clearContent();
    boolean stillValid(MCPlayer player);
    List<MCItemStack> getItems();
}
