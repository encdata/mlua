package dev.mlua.mc.impl.nbt;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Keeps Minecraft's version-specific NBT accessors out of callers. */
public final class MCNbtAccess {
    private MCNbtAccess() {}

    public static Set<String> keys(CompoundTag tag) {
        //--version 26.1
        return tag.keySet();
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getAllKeys();
        //--endversion
    }

    public static String string(CompoundTag tag, String key) {
        //--version 26.1
        return tag.getStringOr(key, "");
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getString(key);
        //--endversion
    }

    public static String string(ListTag tag, int index) {
        //--version 26.1
        return tag.getStringOr(index, "");
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getString(index);
        //--endversion
    }

    public static CompoundTag compound(CompoundTag tag, String key) {
        //--version 26.1
        return tag.getCompoundOrEmpty(key);
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getCompound(key);
        //--endversion
    }

    public static ListTag list(CompoundTag tag, String key, int type) {
        //--version 26.1
        return tag.getListOrEmpty(key);
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getList(key, type);
        //--endversion
    }

    public static int integer(CompoundTag tag, String key) {
        //--version 26.1
        return tag.getIntOr(key, 0);
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getInt(key);
        //--endversion
    }

    public static long longValue(CompoundTag tag, String key) {
        //--version 26.1
        return tag.getLongOr(key, 0L);
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getLong(key);
        //--endversion
    }

    public static double decimal(CompoundTag tag, String key) {
        //--version 26.1
        return tag.getDoubleOr(key, 0.0D);
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getDouble(key);
        //--endversion
    }

    public static boolean contains(CompoundTag tag, String key, int type) {
        //--version 26.1
        return tag.contains(key);
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.contains(key, type);
        //--endversion
    }
}
