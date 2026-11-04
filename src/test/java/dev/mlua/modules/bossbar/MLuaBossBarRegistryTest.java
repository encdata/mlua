package dev.mlua.modules.bossbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class MLuaBossBarRegistryTest {
    @Test
    void duplicateNamesAreIsolatedBySessionAndMod() {
        var registry = new MLuaBossBarRegistry<String>();
        Object firstSession = new Object();
        Object secondSession = new Object();
        var first = new MLuaBossBarRegistry.Key(firstSession, "one", "progress");
        var otherMod = new MLuaBossBarRegistry.Key(firstSession, "two", "progress");
        var otherSession = new MLuaBossBarRegistry.Key(secondSession, "one", "progress");
        registry.put(first, "first");
        registry.put(otherMod, "other mod");
        registry.put(otherSession, "other session");

        assertEquals("first", registry.get(first));
        assertEquals("other mod", registry.get(otherMod));
        assertEquals("other session", registry.get(otherSession));
    }

    @Test
    void ownerCleanupPreservesOtherBarsAndStaleHandlesCannotRemoveReplacement() {
        var registry = new MLuaBossBarRegistry<String>();
        Object session = new Object();
        var owned = new MLuaBossBarRegistry.Key(session, "one", "progress");
        var other = new MLuaBossBarRegistry.Key(session, "two", "progress");
        registry.put(owned, "old event");
        registry.put(other, "other event");
        assertEquals("old event", registry.put(owned, "replacement event"));
        assertFalse(registry.remove(owned, "old event"));

        assertEquals(java.util.List.of("replacement event"), registry.clearOwned(session, "one"));
        assertNull(registry.get(owned));
        assertEquals("other event", registry.get(other));
    }
}
