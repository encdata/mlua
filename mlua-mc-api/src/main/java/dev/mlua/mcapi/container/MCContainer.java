package dev.mlua.mcapi.container;

import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.item.MCItemStack;

import java.util.List;

public interface MCContainer {
    int getContainerSize();
    MCItemStack getItem(int slot);
    void setItem(int slot, MCItemStack stack);
    void clearContent();
    boolean stillValid(MCPlayer player);
    List<MCItemStack> getItems();
}
