package dev.mlua.mcapi.container;

import dev.mlua.mcapi.item.MCItemStack;

public interface MCSlot {
    int getSlotIndex();
    MCItemStack getItem();
    /** Whether the backing Minecraft slot accepts this stack. */
    default boolean mayPlace(MCItemStack stack) { return true; }
    /** Maximum count for this slot and item, honoring both slot and item stack limits. */
    default int getMaxStackSize(MCItemStack stack) {
        int slotLimit = Math.max(0, getMaxStackSize());
        return stack == null || stack.isEmpty() ? slotLimit : Math.min(slotLimit, stack.getMaxStackSize());
    }
    void set(MCItemStack stack);
    void setChanged();
    int getMaxStackSize();
    boolean hasItem();
}
