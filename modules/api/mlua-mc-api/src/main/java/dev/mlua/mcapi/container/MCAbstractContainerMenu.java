package dev.mlua.mcapi.container;

import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.item.MCItemStack;

import java.util.List;

public interface MCAbstractContainerMenu {
    Object getType();
    List<MCSlot> slots();
    MCItemStack quickMoveStack(MCPlayer player, int slotIndex);
    boolean stillValid(MCPlayer player);
}
