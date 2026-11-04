package dev.mlua.minecraft;

import net.minecraft.SharedConstants;

/** Minecraft version details for the 26.1 API. */
public final class MLuaMinecraftVersion {
    private MLuaMinecraftVersion() {
    }

    public static String name() {
        return SharedConstants.getCurrentVersion().name();
    }
}
