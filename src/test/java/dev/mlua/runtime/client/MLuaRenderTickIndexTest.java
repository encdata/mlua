package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MLuaRenderTickIndexTest {
    @Test
    void publishesOnlyMembershipChangesAndDropsInactiveObjects() {
        MLuaRenderTickIndex<String> index = new MLuaRenderTickIndex<>();

        index.update(1, "animated", true);
        var firstSnapshot = index.snapshot();
        assertEquals(java.util.List.of("animated"), firstSnapshot);

        index.update(1, "animated", true);
        assertSame(firstSnapshot, index.snapshot());

        index.update(2, "follow-offset", true);
        var activeSnapshot = index.snapshot();
        assertNotSame(firstSnapshot, activeSnapshot);
        assertEquals(2, activeSnapshot.size());

        index.update(1, "animated", false);
        assertEquals(java.util.List.of("follow-offset"), index.snapshot());
        index.remove(2);
        assertEquals(java.util.List.of(), index.snapshot());
    }
}
