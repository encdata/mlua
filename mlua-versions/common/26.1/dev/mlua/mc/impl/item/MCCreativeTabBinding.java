package dev.mlua.mc.impl.item;

import net.minecraft.world.item.CreativeModeTab;

/** 26.x direct creative-tab binding. */
public final class MCCreativeTabBinding {
    private MCCreativeTabBinding() { }
    public static Object create() { return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0); }
}
