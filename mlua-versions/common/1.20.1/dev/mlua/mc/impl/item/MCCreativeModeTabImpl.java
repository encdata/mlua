package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mcapi.item.MCCreativeModeTab;
import net.minecraft.world.item.CreativeModeTab;

public class MCCreativeModeTabImpl implements MCCreativeModeTab {
    private final CreativeModeTab inner;

    public MCCreativeModeTabImpl(CreativeModeTab inner) {
        this.inner = inner;
    }

    @Override
    public String getDisplayName() {
        return inner.getDisplayName().getString();
    }

    @Override
    public MCResourceLocation getRegistryName() {
        return null;
    }
}
