package dev.mlua.mc.impl.container;

import dev.mlua.mcapi.container.MCSlot;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

public class MCSlotImpl implements MCSlot {
    private final Slot inner;

    public MCSlotImpl(Slot inner) {
        this.inner = inner;
    }

    public static MCSlotImpl fromReal(Slot inner) {
        return inner == null ? null : new MCSlotImpl(inner);
    }

    public Slot inner() {
        return inner;
    }

    @Override
    public int getSlotIndex() {
        return inner.getSlotIndex();
    }

    @Override
    public MCItemStack getItem() {
        return MCItemStackImpl.fromReal(inner.getItem());
    }

    @Override
    public boolean mayPlace(MCItemStack stack) {
        if (stack == null || stack == MCItemStack.EMPTY || stack.isEmpty()) return true;
        if (!(stack instanceof MCItemStackImpl wrapped)) {
            throw new IllegalArgumentException("Unsupported MCItemStack implementation: " + stack.getClass().getName());
        }
        return inner.mayPlace(wrapped.inner());
    }

    @Override
    public void set(MCItemStack stack) {
        if (stack == null || stack == MCItemStack.EMPTY || stack.isEmpty()) {
            inner.set(ItemStack.EMPTY);
        } else if (stack instanceof MCItemStackImpl wrapped) {
            inner.set(wrapped.inner());
        } else {
            throw new IllegalArgumentException("Unsupported MCItemStack implementation: " + stack.getClass().getName());
        }
    }

    @Override
    public void setChanged() {
        inner.setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return inner.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(MCItemStack stack) {
        if (stack == null || stack == MCItemStack.EMPTY || stack.isEmpty()) return inner.getMaxStackSize();
        if (!(stack instanceof MCItemStackImpl wrapped)) {
            throw new IllegalArgumentException("Unsupported MCItemStack implementation: " + stack.getClass().getName());
        }
        return inner.getMaxStackSize(wrapped.inner());
    }

    @Override
    public boolean hasItem() {
        return inner.hasItem();
    }
}
