package dev.mlua.runtime.registry;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaRegistryDefinitionSnapshotTest {
    @Test
    void snapshotDetachesTablesAndPreservesSharedSubtables() {
        LuaTable shared = new LuaTable();
        shared.set("damage", 7);
        LuaTable source = new LuaTable();
        source.set("primary", shared);
        source.set("secondary", shared);

        MLuaRegistryDefinitionSnapshot.Snapshot snapshot =
            MLuaRegistryDefinitionSnapshot.create(source, 4, 8);

        assertNotSame(source, snapshot.value());
        assertNotSame(shared, snapshot.value().get("primary"));
        assertSame(snapshot.value().get("primary"), snapshot.value().get("secondary"));
        assertEquals(3, snapshot.entryCount());
    }

    @Test
    void snapshotRejectsCyclesRatherThanRecursingIndefinitely() {
        LuaTable source = new LuaTable();
        source.set("self", source);

        LuaError error = assertThrows(LuaError.class,
            () -> MLuaRegistryDefinitionSnapshot.copy(source, 8, 8));

        assertEquals("[MLua] Registry definitions cannot contain cyclic tables", error.getMessage());
    }

    @Test
    void snapshotEnforcesDepthAndEntryBudgets() {
        LuaTable root = new LuaTable();
        LuaTable nested = new LuaTable();
        LuaTable tooDeep = new LuaTable();
        root.set("nested", nested);
        nested.set("tooDeep", tooDeep);
        assertThrows(LuaError.class, () -> MLuaRegistryDefinitionSnapshot.copy(root, 2, 8));

        LuaTable tooLarge = new LuaTable();
        tooLarge.set("first", 1);
        tooLarge.set("second", 2);
        assertThrows(LuaError.class, () -> MLuaRegistryDefinitionSnapshot.copy(tooLarge, 8, 1));
    }
}
