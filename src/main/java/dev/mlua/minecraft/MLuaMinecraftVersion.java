package dev.mlua.minecraft;

import net.minecraft.SharedConstants;

/** Minecraft version details whose names changed between supported game releases. */
public final class MLuaMinecraftVersion {
    private MLuaMinecraftVersion() {
    }

    public static String name() {
        return SharedConstants.getCurrentVersion().getName();
    }
}
