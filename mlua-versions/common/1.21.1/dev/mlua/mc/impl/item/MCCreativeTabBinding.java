package dev.mlua.mc.impl.item;

import net.minecraft.world.item.CreativeModeTab;

/** 1.21.x direct creative-tab binding. */
public final class MCCreativeTabBinding {
    private MCCreativeTabBinding() { }
    public static Object create() {
        try { return CreativeModeTab.class.getMethod("builder").invoke(null); }
        catch (ReflectiveOperationException ignored) { try { return CreativeModeTab.class.getMethod("builder", CreativeModeTab.Row.class, int.class).invoke(null, CreativeModeTab.Row.TOP, 0); } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); } }
    }
}
