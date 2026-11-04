package dev.mlua.modules.render.service;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MLuaRenderObjectHandleCacheTest {
    @Test
    void returnsTheSameLiveHandleUntilItIsRemoved() {
        MLuaRenderObjectHandleCache cache = new MLuaRenderObjectHandleCache();
        LuaTable handle = new LuaTable();

        cache.put(17, handle);

        assertSame(handle, cache.get(17));
        cache.remove(17);
        assertNull(cache.get(17));
    }

    @Test
    void clearingTheCacheReleasesAllHandleMappings() {
        MLuaRenderObjectHandleCache cache = new MLuaRenderObjectHandleCache();
        LuaTable first = new LuaTable();
        LuaTable second = new LuaTable();
        cache.put(17, first);
        cache.put(18, second);

        cache.clear();

        assertNull(cache.get(17));
        assertNull(cache.get(18));
    }
}
