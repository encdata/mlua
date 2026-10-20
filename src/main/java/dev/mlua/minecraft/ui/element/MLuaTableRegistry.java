package dev.mlua.minecraft.ui.element;

import org.luaj.vm2.LuaTable;
import java.util.Map;
import java.util.WeakHashMap;

public final class MLuaTableRegistry {
    private static final Map<LuaTable, GuiBase> TABLE_TO_ELEMENT = new WeakHashMap<>();

    public static void register(LuaTable table, GuiBase element) {
        TABLE_TO_ELEMENT.put(table, element);
    }

    public static GuiBase getElement(LuaTable table) {
        return TABLE_TO_ELEMENT.get(table);
    }

    public static void unregister(LuaTable table) {
        if (table != null) TABLE_TO_ELEMENT.remove(table);
    }
}
