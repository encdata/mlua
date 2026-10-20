package dev.mlua.modules.quest;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MLuaQuestRegistryTest {
    @Test
    void sameQuestIdIsIsolatedByRuntimeAndMod() {
        Object firstSession = new Object();
        Object secondSession = new Object();
        LuaTable first = new LuaTable();
        LuaTable second = new LuaTable();
        LuaTable otherMod = new LuaTable();
        first.set("Title", "first session");
        second.set("Title", "second session");
        otherMod.set("Title", "other owner");

        MLuaQuestRegistry.put(firstSession, "example", "intro", first);
        MLuaQuestRegistry.put(secondSession, "example", "intro", second);
        MLuaQuestRegistry.put(firstSession, "other", "intro", otherMod);

        LuaTable firstSnapshot = MLuaQuestRegistry.get(firstSession, "example", "intro");
        assertNotSame(first, firstSnapshot);
        assertEquals("first session", firstSnapshot.get("Title").tojstring());
        first.set("Title", "mutated source");
        firstSnapshot.set("Title", "mutated snapshot");
        assertEquals("first session", MLuaQuestRegistry.get(firstSession, "example", "intro").get("Title").tojstring());
        assertEquals("second session", MLuaQuestRegistry.get(secondSession, "example", "intro").get("Title").tojstring());
        assertEquals("other owner", MLuaQuestRegistry.get(firstSession, "other", "intro").get("Title").tojstring());
    }

    @Test
    void cleanupOnlyRemovesTheRequestedOwnerOrRuntime() {
        Object firstSession = new Object();
        Object secondSession = new Object();
        LuaTable first = new LuaTable();
        LuaTable sameSessionOtherOwner = new LuaTable();
        LuaTable second = new LuaTable();
        sameSessionOtherOwner.set("Title", "other owner");
        second.set("Title", "second session");
        MLuaQuestRegistry.put(firstSession, "example", "intro", first);
        MLuaQuestRegistry.put(firstSession, "other", "intro", sameSessionOtherOwner);
        MLuaQuestRegistry.put(secondSession, "example", "intro", second);

        MLuaQuestRegistry.clearOwned(firstSession, "example");
        assertNull(MLuaQuestRegistry.get(firstSession, "example", "intro"));
        assertEquals("other owner", MLuaQuestRegistry.get(firstSession, "other", "intro").get("Title").tojstring());
        assertEquals("second session", MLuaQuestRegistry.get(secondSession, "example", "intro").get("Title").tojstring());

        MLuaQuestRegistry.clearRuntime(firstSession);
        assertNull(MLuaQuestRegistry.get(firstSession, "other", "intro"));
        assertEquals("second session", MLuaQuestRegistry.get(secondSession, "example", "intro").get("Title").tojstring());
    }

    @Test
    void persistentProgressIdsAreNamespacedPerOwningMod() {
        org.junit.jupiter.api.Assertions.assertEquals("one:intro", MLuaQuestRegistry.persistentId("one", "intro"));
        org.junit.jupiter.api.Assertions.assertEquals("two:intro", MLuaQuestRegistry.persistentId("two", "intro"));
        org.junit.jupiter.api.Assertions.assertEquals("intro", MLuaQuestRegistry.persistentId("", "intro"));
    }
}
