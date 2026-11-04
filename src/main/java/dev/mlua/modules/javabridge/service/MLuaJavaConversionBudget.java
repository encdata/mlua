package dev.mlua.modules.javabridge.service;

import org.luaj.vm2.LuaError;

/** Shared entry budget for one recursive Java/Lua value conversion. */
final class MLuaJavaConversionBudget {
    private long entries;

    void add(long count, String direction) {
        if (count < 0 || this.entries > Long.MAX_VALUE - count) {
            throw new LuaError("[MLua] " + direction + " table conversion entry count is invalid");
        }
        this.entries += count;
        MLuaJavaService.requireConversionEntries(this.entries, direction);
    }
}
