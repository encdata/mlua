package dev.mlua.mc.impl.item;

import dev.mlua.mc.impl.core.MCComponentImpl;
import dev.mlua.mc.impl.nbt.MCCompoundTagImpl;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.item.MCItem;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.item.enchantment.MCEnchantment;
import dev.mlua.mc.impl.item.enchantment.MCEnchantmentImpl;
import dev.mlua.mcapi.nbt.MCCompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

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
        CustomData data = inner.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    @Override
    public MCCompoundTag getTag() {
        CustomData data = inner.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : MCCompoundTagImpl.fromReal(data.copyTag());
    }

    @Override
    public MCCompoundTag getOrCreateTag() {
        CustomData data = inner.get(DataComponents.CUSTOM_DATA);
        return MCCompoundTagImpl.fromReal(data == null ? new net.minecraft.nbt.CompoundTag() : data.copyTag());
    }

    @Override
    public void setTag(MCCompoundTag tag) {
        if (tag == null) {
            inner.remove(DataComponents.CUSTOM_DATA);
        } else {
            inner.set(DataComponents.CUSTOM_DATA, CustomData.of(((MCCompoundTagImpl) tag).inner().copy()));
        }
    }

    @Override
    public MCComponent getHoverName() {
        return MCComponentImpl.fromReal(inner.getHoverName());
    }

    @Override
    public void setHoverName(MCComponent name) {
        inner.set(DataComponents.CUSTOM_NAME, ((MCComponentImpl) name).inner());
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
    public boolean canCombine(MCItemStack other) {
        return other instanceof MCItemStackImpl stack
            && ItemStack.isSameItemSameComponents(inner, stack.inner);
    }

    @Override
    public MCCompoundTag getOrCreateTagElement(String key) {
        net.minecraft.nbt.CompoundTag root = hasTag()
            ? ((MCCompoundTagImpl) getTag()).inner().copy()
            : new net.minecraft.nbt.CompoundTag();
        if (!root.contains(key, 10)) {
            root.put(key, new net.minecraft.nbt.CompoundTag());
        }
        return MCCompoundTagImpl.fromReal(root.getCompound(key));
    }

    @Override
    public int getEnchantmentLevel(MCEnchantment enchantment) {
        if (!(enchantment instanceof MCEnchantmentImpl impl)) {
            throw new IllegalArgumentException("Enchantment must use the active Minecraft version adapter");
        }
        net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder = impl.holder();
        if (holder == null) throw new IllegalArgumentException("Enchantment is not present in the active registry");
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, inner);
    }
}
