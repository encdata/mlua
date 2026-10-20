package dev.mlua.runtime.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MLuaRegisteredTypeCatalogTest {
    @Test
    void typeHandleRemovesOnlyItsOwnProvider() {
        var catalog = new MLuaRegisteredTypeCatalog<Object>();
        Object first = new Object();
        Object replacement = new Object();

        catalog.register("first.addon", "first.addon:weapon", "First", first, "Item");
        assertSame(first, catalog.get("first.addon:weapon", "Item"));
        assertTrue(catalog.remove("first.addon", "first.addon:weapon", "Item", first));
        assertFalse(catalog.remove("first.addon", "first.addon:weapon", "Item", first));

        catalog.register("first.addon", "first.addon:weapon", "Replacement", replacement, "Item");
        assertFalse(catalog.remove("first.addon", "first.addon:weapon", "Item", first));
        assertSame(replacement, catalog.get("first.addon:weapon", "Item"));
        assertEquals("Replacement", catalog.descriptions(java.util.Map.of()).get("first.addon:weapon"));
    }
}
