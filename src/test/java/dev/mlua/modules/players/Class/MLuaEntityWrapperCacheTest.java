package dev.mlua.modules.players.Class;

import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaRegistration;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MLuaEntityWrapperCacheTest {
    @Test
    void equalEntityIdsInDifferentScopesKeepIndependentWrappers() {
        MLuaEntityWrapperCache cache = new MLuaEntityWrapperCache();
        UUID uuid = UUID.randomUUID();
        String overworld = "minecraft:overworld";
        String nether = "minecraft:the_nether";
        MLuaEntityWrapperCache.Key server = new MLuaEntityWrapperCache.Key(Side.SERVER, overworld, uuid, 7);
        MLuaEntityWrapperCache.Key client = new MLuaEntityWrapperCache.Key(Side.CLIENT, overworld, uuid, 7);
        MLuaEntityWrapperCache.Key otherDimension = new MLuaEntityWrapperCache.Key(Side.SERVER, nether, uuid, 7);
        LuaTable serverWrapper = new LuaTable();
        LuaTable clientWrapper = new LuaTable();
        LuaTable netherWrapper = new LuaTable();

        cache.put(server, serverWrapper);
        cache.put(client, clientWrapper);
        cache.put(otherDimension, netherWrapper);

        assertSame(serverWrapper, cache.get(server));
        assertSame(clientWrapper, cache.get(client));
        assertSame(netherWrapper, cache.get(otherDimension));
        assertNotSame(cache.get(server), cache.get(client));

        cache.remove(client);

        assertNull(cache.get(client));
        assertSame(serverWrapper, cache.get(server));
        assertSame(netherWrapper, cache.get(otherDimension));
        assertEquals(2, cache.size());
    }

    @Test
    void runtimeRegistrationsDoNotShareCachedLuaEntityTables() {
        MLuaEntityWrapperCache first = MLuaEntityWrapper.cacheFor(registration());
        MLuaEntityWrapperCache second = MLuaEntityWrapper.cacheFor(registration());
        MLuaEntityWrapperCache.Key key = new MLuaEntityWrapperCache.Key(
                Side.SERVER, "minecraft:overworld", UUID.randomUUID(), 12);
        LuaTable firstTable = new LuaTable();
        LuaTable secondTable = new LuaTable();
        first.put(key, firstTable);
        second.put(key, secondTable);

        assertNotSame(first, second);
        assertSame(firstTable, first.get(key));
        assertSame(secondTable, second.get(key));
    }

    @Test
    void clearingOneRegistrationKeepsOtherRuntimeCacheIntact() {
        MLuaRegistration firstRegistration = registration();
        MLuaRegistration secondRegistration = registration();
        MLuaEntityWrapperCache first = MLuaEntityWrapper.cacheFor(firstRegistration);
        MLuaEntityWrapperCache second = MLuaEntityWrapper.cacheFor(secondRegistration);
        MLuaEntityWrapperCache.Key key = new MLuaEntityWrapperCache.Key(
                Side.SERVER, "minecraft:overworld", UUID.randomUUID(), 13);
        LuaTable firstTable = new LuaTable();
        LuaTable secondTable = new LuaTable();
        first.put(key, firstTable);
        second.put(key, secondTable);

        MLuaEntityWrapper.clearCache(firstRegistration);

        assertNotSame(first, MLuaEntityWrapper.cacheFor(firstRegistration));
        assertSame(second, MLuaEntityWrapper.cacheFor(secondRegistration));
        assertSame(secondTable, second.get(key));
    }

    private static MLuaRegistration registration() {
        return new MLuaRegistration() {
            @Override public LuaTable registerClass(String name, String parentName) { return new LuaTable(); }
            @Override public LuaTable registerClass(String name, String parentName, String description) { return new LuaTable(); }
            @Override public LuaTable registerClassTable(String name, LuaTable classTable, String parentName) { return classTable; }
            @Override public LuaTable registerClassTable(String name, LuaTable classTable, String parentName, String description) { return classTable; }
            @Override public LuaTable classTable(String name) { return null; }
        };
    }
}
