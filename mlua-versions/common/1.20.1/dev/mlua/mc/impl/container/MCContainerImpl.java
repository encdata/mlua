package dev.mlua.mc.impl.container;

import dev.mlua.mcapi.container.MCContainer;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MCContainerImpl implements MCContainer {
    private final Container inner;

    public MCContainerImpl(Container inner) {
        this.inner = inner;
    }

    public static MCContainerImpl fromReal(Container inner) {
        return inner == null ? null : new MCContainerImpl(inner);
    }

    public Container inner() {
        return inner;
    }

    @Override
    public int getContainerSize() {
        return inner.getContainerSize();
    }

    @Override
    public int getMaxStackSize() {
        return inner.getMaxStackSize();
    }

    @Override
    public MCItemStack getItem(int slot) {
        return MCItemStackImpl.fromReal(inner.getItem(slot));
    }

    @Override
    public boolean canPlaceItem(int slot, MCItemStack stack) {
        if (slot < 0 || slot >= inner.getContainerSize()) return false;
        return inner.canPlaceItem(slot, toMinecraftStack(stack));
    }

    @Override
    public void setItem(int slot, MCItemStack stack) {
        inner.setItem(slot, toMinecraftStack(stack));
    }

    private static ItemStack toMinecraftStack(MCItemStack stack) {
        if (stack == null || stack == MCItemStack.EMPTY || stack.isEmpty()) return ItemStack.EMPTY;
        if (stack instanceof MCItemStackImpl wrapped) return wrapped.inner();
        throw new IllegalArgumentException("Unsupported MCItemStack implementation: " + stack.getClass().getName());
    }

    @Override
    public void clearContent() {
        inner.clearContent();
    }

    @Override
    public boolean stillValid(MCPlayer player) {
        if (player instanceof dev.mlua.mc.impl.entity.MCPlayerImpl mp && mp.inner() instanceof Player pl) {
            return inner.stillValid(pl);
        }
        return false;
    }

    @Override
    public List<MCItemStack> getItems() {
        List<MCItemStack> result = new ArrayList<>();
        for (int i = 0; i < inner.getContainerSize(); i++) {
            result.add(MCItemStackImpl.fromReal(inner.getItem(i)));
        }
        return result;
    }
}
