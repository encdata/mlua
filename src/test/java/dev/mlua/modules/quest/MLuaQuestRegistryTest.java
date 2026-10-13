package dev.mlua.modules.quest;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MLuaQuestRegistryTest {
    @Test
    void sameQuestIdIsIsolatedByRuntimeAndMod() {
        Object firstSession = new Object();
        Object secondSession = new Object();
        LuaTable first = new LuaTable();
        LuaTable second = new LuaTable();
        LuaTable otherMod = new LuaTable();

        MLuaQuestRegistry.put(firstSession, "example", "intro", first);
        MLuaQuestRegistry.put(secondSession, "example", "intro", second);
        MLuaQuestRegistry.put(firstSession, "other", "intro", otherMod);

        assertSame(first, MLuaQuestRegistry.get(firstSession, "example", "intro"));
        assertSame(second, MLuaQuestRegistry.get(secondSession, "example", "intro"));
        assertSame(otherMod, MLuaQuestRegistry.get(firstSession, "other", "intro"));
    }

    @Test
    void cleanupOnlyRemovesTheRequestedOwnerOrRuntime() {
        Object firstSession = new Object();
        Object secondSession = new Object();
        LuaTable first = new LuaTable();
        LuaTable sameSessionOtherOwner = new LuaTable();
        LuaTable second = new LuaTable();
        MLuaQuestRegistry.put(firstSession, "example", "intro", first);
        MLuaQuestRegistry.put(firstSession, "other", "intro", sameSessionOtherOwner);
        MLuaQuestRegistry.put(secondSession, "example", "intro", second);

        MLuaQuestRegistry.clearOwned(firstSession, "example");
        assertNull(MLuaQuestRegistry.get(firstSession, "example", "intro"));
        assertSame(sameSessionOtherOwner, MLuaQuestRegistry.get(firstSession, "other", "intro"));
        assertSame(second, MLuaQuestRegistry.get(secondSession, "example", "intro"));

        MLuaQuestRegistry.clearRuntime(firstSession);
        assertNull(MLuaQuestRegistry.get(firstSession, "other", "intro"));
        assertSame(second, MLuaQuestRegistry.get(secondSession, "example", "intro"));
    }

    @Test
    void persistentProgressIdsAreNamespacedPerOwningMod() {
        org.junit.jupiter.api.Assertions.assertEquals("one:intro", MLuaQuestRegistry.persistentId("one", "intro"));
        org.junit.jupiter.api.Assertions.assertEquals("two:intro", MLuaQuestRegistry.persistentId("two", "intro"));
        org.junit.jupiter.api.Assertions.assertEquals("intro", MLuaQuestRegistry.persistentId("", "intro"));
    }
}
