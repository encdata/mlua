package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.item.MCItem;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.item.enchantment.MCEnchantment;
import dev.mlua.mc.impl.item.enchantment.MCEnchantmentImpl;
import dev.mlua.mcapi.nbt.MCCompoundTag;
import dev.mlua.mc.impl.core.MCComponentImpl;
import dev.mlua.mc.impl.nbt.MCCompoundTagImpl;
import net.minecraft.world.item.ItemStack;

public class MCItemStackImpl implements MCItemStack {
    private final ItemStack inner;

    public MCItemStackImpl(ItemStack inner) {
        this.inner = inner;
    }

    public static MCItemStackImpl fromReal(ItemStack inner) {
        return inner == null || inner.isEmpty() ? null : new MCItemStackImpl(inner);
    }

    public ItemStack inner() {
        return inner;
    }

    @Override
    public MCItem getItem() {
        return MCItemImpl.fromReal(inner.getItem());
    }

    @Override
    public int getCount() {
        return inner.getCount();
    }

    @Override
    public void setCount(int count) {
        inner.setCount(count);
    }

    @Override
    public int getMaxStackSize() {
        return inner.getMaxStackSize();
    }

    @Override
    public int getMaxDamage() {
        return inner.getMaxDamage();
    }

    @Override
    public int getDamageValue() {
        return inner.getDamageValue();
    }

    @Override
    public void setDamageValue(int damage) {
        inner.setDamageValue(damage);
    }

    @Override
    public boolean isEmpty() {
        return inner.isEmpty();
    }

    @Override
    public boolean isDamageableItem() {
        return inner.isDamageableItem();
    }

    @Override
    public boolean hasTag() {
        return inner.hasTag();
    }

    @Override
    public MCCompoundTag getTag() {
        var tag = inner.getTag();
        return tag == null ? null : MCCompoundTagImpl.fromReal(tag);
    }

    @Override
    public MCCompoundTag getOrCreateTag() {
        return MCCompoundTagImpl.fromReal(inner.getOrCreateTag());
    }

    @Override
    public void setTag(MCCompoundTag tag) {
        inner.setTag(((MCCompoundTagImpl) tag).inner());
    }

    @Override
    public MCComponent getHoverName() {
        return MCComponentImpl.fromReal(inner.getHoverName());
    }

    @Override
    public void setHoverName(MCComponent name) {
        inner.setHoverName(((MCComponentImpl) name).inner());
    }

    @Override
    public MCComponent getDisplayName() {
        return MCComponentImpl.fromReal(inner.getDisplayName());
    }

    @Override
    public MCItemStack copy() {
        return MCItemStackImpl.fromReal(inner.copy());
    }

    @Override
    public MCCompoundTag getOrCreateTagElement(String key) {
        return MCCompoundTagImpl.fromReal(inner.getOrCreateTagElement(key));
    }

    @Override
    public int getEnchantmentLevel(MCEnchantment enchantment) {
        if (!(enchantment instanceof MCEnchantmentImpl impl)) {
            throw new IllegalArgumentException("Enchantment must use the active Minecraft version adapter");
        }
        return net.minecraft.world.item.enchantment.EnchantmentHelper
            .getItemEnchantmentLevel(impl.inner(), inner);
    }
}
