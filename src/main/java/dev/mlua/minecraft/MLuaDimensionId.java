package dev.mlua.minecraft;

import net.minecraft.world.level.Level;
//--version 26.1
import net.minecraft.resources.Identifier;
//--endversion
//--version 1.20.1,1.21.1
import net.minecraft.resources.ResourceLocation;
//--endversion

/** Resolves a world's identifier using the name exposed by the active Minecraft version. */
public final class MLuaDimensionId {
    private MLuaDimensionId() {}

    public static String from(Level level) {
        return nativeId(level).toString();
    }

    //--version 26.1
    public static Identifier nativeId(Level level) {
        return level.dimension().identifier();
    }
    //--endversion
    //--version 1.20.1,1.21.1
    public static ResourceLocation nativeId(Level level) {
        return level.dimension().location();
    }
    //--endversion
}
