package dev.mlua.core.classes;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaClassHierarchyTest {
    @Test
    void childIndexTracksDescendantsAndRemovals() {
        MLuaClassHierarchy hierarchy = new MLuaClassHierarchy();
        hierarchy.registerParent("Child", "Root");
        hierarchy.registerParent("Grandchild", "Child");
        hierarchy.registerParent("Unrelated", "OtherRoot");

        assertTrue(hierarchy.hasChildren("Root"));
        assertEquals(Set.of("Child", "Grandchild"), new HashSet<>(hierarchy.descendantsOf("Root")));
        assertEquals(Set.of("Grandchild"), new HashSet<>(hierarchy.descendantsOf("Child")));
        assertEquals("Child", hierarchy.removeClass("Grandchild"));
        assertFalse(hierarchy.hasChildren("Child"));
        assertEquals("Root", hierarchy.removeClass("Child"));
        assertFalse(hierarchy.hasChildren("Root"));
        assertEquals(Set.of("Unrelated"), new HashSet<>(hierarchy.descendantsOf("OtherRoot")));
    }
}
