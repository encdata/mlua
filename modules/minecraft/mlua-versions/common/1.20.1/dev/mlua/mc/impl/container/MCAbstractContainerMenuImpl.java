package dev.mlua.mc.impl.container;

import dev.mlua.mcapi.container.MCAbstractContainerMenu;
import dev.mlua.mcapi.container.MCSlot;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

public class MCAbstractContainerMenuImpl implements MCAbstractContainerMenu {
    private final AbstractContainerMenu inner;

    public MCAbstractContainerMenuImpl(AbstractContainerMenu inner) {
        this.inner = inner;
    }

    public static MCAbstractContainerMenuImpl fromReal(AbstractContainerMenu inner) {
        return inner == null ? null : new MCAbstractContainerMenuImpl(inner);
    }

    public AbstractContainerMenu inner() {
        return inner;
    }

    @Override
    public Object getType() {
        return inner.getType();
    }

    @Override
    public List<MCSlot> slots() {
        List<MCSlot> result = new ArrayList<>();
        for (Slot slot : inner.slots) {
            result.add(MCSlotImpl.fromReal(slot));
        }
        return result;
    }

    @Override
    public MCItemStack quickMoveStack(MCPlayer player, int slotIndex) {
        if (player instanceof dev.mlua.mc.impl.entity.MCPlayerImpl mp && mp.inner() instanceof Player pl) {
            net.minecraft.world.item.ItemStack stack = inner.quickMoveStack(pl, slotIndex);
            return MCItemStackImpl.fromReal(stack);
        }
        return null;
    }

    @Override
    public boolean stillValid(MCPlayer player) {
        if (player instanceof dev.mlua.mc.impl.entity.MCPlayerImpl mp && mp.inner() instanceof Player pl) {
            return inner.stillValid(pl);
        }
        return false;
    }
}
