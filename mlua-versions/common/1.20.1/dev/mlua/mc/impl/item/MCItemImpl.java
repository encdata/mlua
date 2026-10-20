package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.item.MCCreativeModeTab;
import dev.mlua.mcapi.item.MCItem;
import dev.mlua.mcapi.item.MCItemStack;
import net.minecraft.world.item.Item;

public class MCItemImpl implements MCItem {
    private final Item inner;

    public MCItemImpl(Item inner) {
        this.inner = inner;
    }

    public static MCItemImpl fromReal(Item inner) {
        return inner == null ? null : new MCItemImpl(inner);
    }

    public Item inner() {
        return inner;
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
    public boolean isDamageable(MCItemStack stack) {
        return inner.canBeDepleted();
    }

    @Override
    public MCCreativeModeTab getCreativeTab() {
        return null;
    }

    @Override
    public String getDescriptionId() {
        return inner.getDescriptionId();
    }

    @Override
    public MCItemStack getDefaultInstance() {
        return MCItemStackImpl.fromReal(inner.getDefaultInstance());
    }

    @Override
    public boolean isEnabledForEnchantment() {
        return inner.getEnchantmentValue() > 0;
    }
}
