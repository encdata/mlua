package dev.mlua.mcapi.container;

import dev.mlua.mcapi.item.MCItemStack;

public interface MCSlot {
    int getSlotIndex();
    MCItemStack getItem();
    void set(MCItemStack stack);
    int getMaxStackSize();
    boolean hasItem();
}
